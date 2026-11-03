package ma.aquaops.detection.rules;

import ma.aquaops.detection.model.SensorReading;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class PumpFaultScenarioTest {

    @ParameterizedTest
    @CsvFileSource(
        resources = "/pump-scenarios.csv",
        numLinesToSkip = 1
    )
    void shouldDetectExpectedAnomalies(
            String type,
            String pumpState,
            int expected) {

        Instant time =
                Instant.parse("2026-11-02T15:00:00Z");

        PumpFaultRule rule = new PumpFaultRule(
                Clock.fixed(time, ZoneOffset.UTC)
        );

        SensorReading reading = new SensorReading(
                "1",
                15,
                3,
                type,
                "PUMP_STATE".equals(type) ? null : 2.4,
                null,
                pumpState,
                time
        );

        var anomalies = rule.evaluate(reading);

        assertEquals(expected, anomalies.size());
    }
}
