package ma.aquaops.detection.engine;

import ma.aquaops.detection.model.SensorReading;
import ma.aquaops.detection.rules.PumpFaultRule;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DetectionEngineTest {

    private final Instant time =
            Instant.parse("2026-10-12T21:30:01Z");

    private final DetectionEngine engine =
            new DetectionEngine(List.of(
                    new PumpFaultRule(
                            Clock.fixed(time, ZoneOffset.UTC)
                    )
            ));

    @Test
    void shouldDetectPumpFault() {
        SensorReading reading = new SensorReading(
                "1", 15, 3, "PUMP_STATE",
                null, null, "FAULT", time
        );

        var anomalies = engine.process(reading);

        assertEquals(1, anomalies.size());
        assertEquals("PUMP_FAULT", anomalies.get(0).type());
    }

    @Test
    void shouldIgnoreNormalPump() {
        SensorReading reading = new SensorReading(
                "1", 15, 3, "PUMP_STATE",
                null, null, "NORMAL", time
        );

        assertTrue(engine.process(reading).isEmpty());
    }
}
