package ma.aquaops.telemetry.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import ma.aquaops.telemetry.readings.Reading;
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
}
