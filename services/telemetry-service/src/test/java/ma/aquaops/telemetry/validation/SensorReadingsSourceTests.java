package ma.aquaops.telemetry.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import ma.aquaops.telemetry.readings.Reading;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

/** NFR-05 : l'origine Kafka du message est conservee sur la mesure enregistree. */
class SensorReadingsSourceTests {

    private static final String MESURE = "{\"eventVersion\":\"1\",\"sensorId\":12,\"zoneId\":3,\"type\":\"PRESSURE\","
            + "\"value\":2.4,\"unit\":\"bar\",\"measuredAt\":\"2026-10-12T21:30:00Z\"}";

    private final List<Reading> recus = new ArrayList<>();
    private final SensorReadingsConsumer consommateur = new SensorReadingsConsumer(recus::add);

    @Test
    void laMesureGardeLaPartitionLOffsetEtLHorodatageDuMessage() {
        assertTrue(consommateur.handle(MESURE, new KafkaSource(0, 42L, 1_700_000_000_000L)));
        Reading lue = recus.get(0);
        assertEquals(0, lue.sourcePartition);
        assertEquals(42L, lue.sourceOffset);
        assertEquals(1_700_000_000_000L, lue.sourceTimestamp);
    }

    @Test
    void sansOrigineKafkaLesChampsRestentVides() {
        assertTrue(consommateur.handle(MESURE));
        assertNull(recus.get(0).sourceOffset);
        assertNull(recus.get(0).sourcePartition);
    }

    @Test
    void unMessageIllisibleAvecOrigineEstRejeteSansPlanter() {
        assertEquals(false, consommateur.handle("{pas du json", new KafkaSource(0, 1L, 1L)));
        assertTrue(recus.isEmpty());
        assertEquals(1, consommateur.rejectedCount());
    }

    // RC-04 / RC-05 : l'identifiant de la mesure arrive dans l'en-tete Kafka reading-id, pas dans le JSON.
    private static final String ID = "3f2b8c1a-1111-4222-8333-444455556666";

    private static ConsumerRecord<String, String> message(String readingIdHeader) {
        ConsumerRecord<String, String> r = new ConsumerRecord<>("sensor-readings", 0, 7L, "12", MESURE);
        if (readingIdHeader != null) {
            r.headers().add(KafkaSource.READING_ID_HEADER, readingIdHeader.getBytes(StandardCharsets.UTF_8));
        }
        return r;
    }

    @Test
    void lIdentifiantDeLaMesureEstLuDansLEnTeteKafka() {
        consommateur.onMessage(message(ID));
        assertEquals(ID, recus.get(0).readingId);
        assertEquals(7L, recus.get(0).sourceOffset);
    }

    @Test
    void sansEnTeteLaMesureEstGardeeSansIdentifiant() {
        consommateur.onMessage(message(null));
        assertEquals(1, recus.size());
        assertNull(recus.get(0).readingId);
    }

    @Test
    void unEnTeteMalFormeNEmpechePasDeGarderLaMesure() {
        consommateur.onMessage(message("x'; drop table readings; --"));
        assertEquals(1, recus.size());
        assertNull(recus.get(0).readingId);
    }

    @Test
    void unLotKafkaGardeChaqueOrigineEtRejetteLeMessageIllisible() {
        ConsumerRecord<String, String> illisible = new ConsumerRecord<>("sensor-readings", 1, 8L, "12", "{pas du json");
        ConsumerRecord<String, String> autre = new ConsumerRecord<>("sensor-readings", 1, 9L, "12", MESURE);
        consommateur.onMessages(List.of(message(ID), illisible, autre));
        assertEquals(2, recus.size());
        assertEquals(ID, recus.get(0).readingId);
        assertEquals(7L, recus.get(0).sourceOffset);
        assertEquals(9L, recus.get(1).sourceOffset);
        assertEquals(1, consommateur.rejectedCount());
    }
}
