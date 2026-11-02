package ma.aquaops.detection.rules;

import ma.aquaops.detection.model.Anomaly;
import ma.aquaops.detection.model.SensorReading;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PumpFaultRuleTest {

    private static final Instant TIME =
            Instant.parse("2026-10-12T21:30:01Z");

    private final PumpFaultRule rule =
            new PumpFaultRule(
                    Clock.fixed(TIME, ZoneOffset.UTC)
            );

    private SensorReading pumpReading(String state) {
        return new SensorReading(
                "1", 15, 3, "PUMP_STATE",
                null, null, state, TIME
        );
    }

    @Test
    void faultShouldCreateAnomaly() {
        List<Anomaly> anomalies =
                rule.evaluate(pumpReading("FAULT"));

        assertEquals(1, anomalies.size());
        assertEquals("PUMP_FAULT", anomalies.get(0).type());
        assertEquals(3, anomalies.get(0).zoneId());
        assertEquals(TIME, anomalies.get(0).detectedAt());
    }

    @Test
    void normalPumpShouldNotCreateAnomaly() {
        assertTrue(
                rule.evaluate(pumpReading("NORMAL")).isEmpty()
        );
    }

    @Test
    void pressureReadingShouldNotCreatePumpAnomaly() {
        SensorReading reading = new SensorReading(
                "1", 15, 3, "PRESSURE",
                2.4, "bar", null, TIME
        );

        assertTrue(rule.evaluate(reading).isEmpty());
    }
}
