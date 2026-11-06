package ma.aquaops.detection.silence;

import ma.aquaops.detection.kafka.AnomalyPublisher;
import ma.aquaops.detection.model.Anomaly;
import ma.aquaops.detection.model.SensorReading;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class SilenceDetector {

    private record SensorState(
            Integer zoneId,
            Instant measuredAt,
            Instant receivedAt,
            boolean silent) {}

    private final Clock clock;
    private final Duration threshold;
    private final AnomalyPublisher publisher;
    private final Map<Integer, SensorState> sensors = new HashMap<>();

    public SilenceDetector(
            Clock clock,
            @Value("${silence.threshold-seconds:10}") long thresholdSeconds,
            AnomalyPublisher publisher) {

        this.clock = clock;
        this.threshold = Duration.ofSeconds(thresholdSeconds);
        this.publisher = publisher;
    }

    // Appelé pour chaque mesure reçue : le capteur n'est plus silencieux.
    public synchronized void onReading(SensorReading reading) {
        if (reading.sensorId() == null || reading.zoneId() == null) {
            return;
        }
        Instant received = clock.instant();
        Instant measured = reading.measuredAt() != null
                ? reading.measuredAt()
                : received;

        sensors.put(reading.sensorId(),
                new SensorState(reading.zoneId(), measured, received, false));
    }

    @Scheduled(fixedRateString = "${silence.check-interval-ms:1000}")
    public void check() {
        collectSilentSensors().forEach(publisher::publish);
    }

    // Sous verrou : une seule anomalie par période de silence.
    private synchronized List<Anomaly> collectSilentSensors() {
        Instant now = clock.instant();
        List<Anomaly> anomalies = new ArrayList<>();

        for (Map.Entry<Integer, SensorState> entry : sensors.entrySet()) {
            SensorState state = entry.getValue();
            boolean expired = Duration.between(state.receivedAt(), now)
                    .compareTo(threshold) >= 0;

            if (!state.silent() && expired) {
                entry.setValue(new SensorState(
                        state.zoneId(), state.measuredAt(),
                        state.receivedAt(), true));

                anomalies.add(new Anomaly(
                        "1",
                        UUID.randomUUID().toString(),
                        "SENSOR_SILENT",
                        state.zoneId(),
                        entry.getKey(),
                        state.measuredAt(),
                        now));
            }
        }
        return anomalies;
    }
}