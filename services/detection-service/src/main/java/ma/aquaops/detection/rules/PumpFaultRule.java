package ma.aquaops.detection.rules;

import ma.aquaops.detection.model.Anomaly;
import ma.aquaops.detection.model.SensorReading;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

public class PumpFaultRule implements Rule {

    private final Clock clock;

    public PumpFaultRule(Clock clock) {
        this.clock = clock;
    }

    @Override
    public List<Anomaly> evaluate(SensorReading reading) {

        if ("PUMP_STATE".equals(reading.type())
                && "FAULT".equals(reading.pumpState())) {

            Anomaly anomaly = new Anomaly(
                    "1",
                    UUID.randomUUID().toString(),
                    "PUMP_FAULT",
                    reading.zoneId(),
                    reading.sensorId(),
                    reading.measuredAt(),
                    clock.instant()
            );

            return List.of(anomaly);
        }

        return List.of();
    }
}
