package ma.aquaops.telemetry.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import ma.aquaops.telemetry.readings.Reading;
import org.junit.jupiter.api.Test;

class SensorReadingsConsumerTests {

    private final List<Reading> recus = new ArrayList<>();
    private final SensorReadingsConsumer consommateur = new SensorReadingsConsumer(recus::add);

    @Test
    void accepteUnMessageJsonValide() {
        String json = "{\"eventVersion\":\"1\",\"sensorId\":12,\"zoneId\":3,\"type\":\"PRESSURE\","
                + "\"value\":2.4,\"unit\":\"bar\",\"measuredAt\":\"2026-10-12T21:30:00Z\"}";
        assertTrue(consommateur.handle(json));
        assertEquals(1, recus.size());
        assertEquals(12L, recus.get(0).sensorId);
    }

    @Test
    void accepteUneValeurEntiere() {
        String json = "{\"eventVersion\":\"1\",\"sensorId\":12,\"zoneId\":3,\"type\":\"FLOW\","
                + "\"value\":40,\"measuredAt\":\"2026-10-12T21:30:00Z\"}";
        assertTrue(consommateur.handle(json));
    }

    @Test
    void rejetteUnJsonIllisibleSansPlanter() {
        assertFalse(consommateur.handle("{ceci n'est pas du json"));
        assertTrue(recus.isEmpty());
        assertEquals(1, consommateur.rejectedCount());
    }

    @Test
    void rejetteUneMesureInvalide() {
        String json = "{\"eventVersion\":\"1\",\"sensorId\":12,\"zoneId\":3,\"type\":\"PRESSURE\","
                + "\"value\":\"abc\",\"measuredAt\":\"2026-10-12T21:30:00Z\"}";
        assertFalse(consommateur.handle(json));
        assertTrue(recus.isEmpty());
        assertEquals(1, consommateur.rejectedCount());
    }

    @Test
    void rejetteUnMessageNul() {
        assertFalse(consommateur.handle("null"));
        assertEquals(1, consommateur.rejectedCount());
    }
}