package ma.aquaops.telemetry.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import ma.aquaops.telemetry.readings.Reading;
import org.junit.jupiter.api.Test;

class MeasureProcessorTests {

    private final List<Reading> recus = new ArrayList<>();
    private final MeasureProcessor processeur =
            new MeasureProcessor(new MeasureValidator(), recus::add);

    private Map<String, Object> pression() {
        Map<String, Object> m = new HashMap<>();
        m.put("eventVersion", "1");
        m.put("sensorId", 12);
        m.put("zoneId", 3);
        m.put("type", "PRESSURE");
        m.put("value", 2.4);
        m.put("unit", "bar");
        m.put("measuredAt", "2026-10-12T21:30:00Z");
        return m;
    }

    @Test
    void envoieUneMesureValideAuSinkAvecLesBonsChamps() {
        assertTrue(processeur.process(pression()));
        assertEquals(1, recus.size());
        Reading r = recus.get(0);
        assertEquals(12L, r.sensorId);
        assertEquals(3L, r.zoneId);
        assertEquals("PRESSURE", r.type);
        assertEquals(2.4, r.value);
        assertEquals("bar", r.unit);
        assertEquals(Instant.parse("2026-10-12T21:30:00Z"), r.measuredAt);
        assertEquals(1, processeur.acceptedCount());
    }

    @Test
    void envoieUnEtatDePompeSansValeur() {
        Map<String, Object> m = new HashMap<>();
        m.put("eventVersion", "1");
        m.put("sensorId", 15);
        m.put("zoneId", 3);
        m.put("type", "PUMP_STATE");
        m.put("pumpState", "FAULT");
        m.put("measuredAt", "2026-10-12T21:30:01Z");
        assertTrue(processeur.process(m));
        assertNull(recus.get(0).value);
        assertEquals("FAULT", recus.get(0).pumpState);
    }

    @Test
    void rejetteUneMesureMalformeeSansLEnvoyerAuSink() {
        Map<String, Object> m = pression();
        m.put("value", "abc");
        assertFalse(processeur.process(m));
        assertTrue(recus.isEmpty());
        assertEquals(1, processeur.rejectedCount());
    }

    @Test
    void rejetteUnMessageVideSansPlanter() {
        assertFalse(processeur.process(null));
        assertTrue(recus.isEmpty());
        assertEquals(1, processeur.rejectedCount());
    }

    // RC-04 : un lot Kafka ; une mesure invalide ou inconvertible est rejetee seule, le reste passe en une fois.

    @Test
    void unLotEnvoieSesMesuresValidesEnUneFoisEtRejetteLesAutres() {
        List<List<Reading>> appels = new ArrayList<>();
        MeasureProcessor parLot = new MeasureProcessor(new MeasureValidator(), new ma.aquaops.telemetry.readings.MeasureSink() {
            @Override
            public void record(Reading reading) {
                throw new AssertionError("le lot doit passer par recordAll");
            }

            @Override
            public void recordAll(List<Reading> readings) {
                appels.add(List.copyOf(readings));
            }
        });
        Map<String, Object> invalide = pression();
        invalide.put("value", "abc");
        Map<String, Object> dateFausse = pression();
        dateFausse.put("measuredAt", "pas une date");

        int acceptees = parLot.processAll(List.of(
                new MeasureProcessor.Incoming(pression(), new KafkaSource(0, 1L, 1L)),
                new MeasureProcessor.Incoming(invalide, new KafkaSource(0, 2L, 2L)),
                new MeasureProcessor.Incoming(dateFausse, new KafkaSource(0, 3L, 3L)),
                new MeasureProcessor.Incoming(pression(), new KafkaSource(0, 4L, 4L))));

        assertEquals(2, acceptees);
        assertEquals(1, appels.size());
        assertEquals(List.of(1L, 4L), appels.get(0).stream().map(r -> r.sourceOffset).toList());
        assertEquals(2, parLot.acceptedCount());
        assertEquals(2, parLot.rejectedCount());
    }
}
