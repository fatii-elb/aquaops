
package ma.aquaops.detection.rules;

import ma.aquaops.detection.model.Anomaly;
import ma.aquaops.detection.model.SensorReading;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class LeakRule implements Rule {

    // Duree maximale entre les mesures de pression et de debit
    private static final Duration MAX_READING_GAP =
            Duration.ofSeconds(30);

    private final PressureWindowTracker pressureTracker;
    private final double maxFlow;
    private final Clock clock;

    private final Map<Integer, Boolean> lowPressure =
            new HashMap<>();

    private final Map<Integer, Boolean> highFlow =
            new HashMap<>();

    private final Map<Integer, Instant> lastPressure =
            new HashMap<>();

    private final Map<Integer, Instant> lastFlow =
            new HashMap<>();

    public LeakRule(
            double minPressure,
            double maxFlow,
            Duration pressureDuration,
            Clock clock) {

        if (!Double.isFinite(maxFlow) || maxFlow <= 0) {
            throw new IllegalArgumentException(
                    "Invalid flow threshold");
        }

        this.pressureTracker = new PressureWindowTracker(
                minPressure, pressureDuration);

        this.maxFlow = maxFlow;
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public synchronized List<Anomaly> evaluate(
            SensorReading reading) {

        String type = reading.type();

        // Seules les mesures PRESSURE et FLOW nous interessent
        if (!"PRESSURE".equals(type)
                && !"FLOW".equals(type)) {
            return List.of();
        }

        Integer zone = reading.zoneId();
        Instant time = reading.measuredAt();
        Double value = reading.value();

        // Verification des donnees
        if (zone == null || time == null
                || value == null
                || !Double.isFinite(value)) {
            return List.of();
        }

        // ETAPE 1 : Traitement de la pression
        if ("PRESSURE".equals(type)) {

            Instant previous = lastPressure.get(zone);

            // Ignorer les anciennes mesures
            if (previous != null
                    && !time.isAfter(previous)) {
                return List.of();
            }

            lastPressure.put(zone, time);

            // Verification de la pression basse pendant N secondes
            boolean confirmed =
                    pressureTracker.update(reading);

            lowPressure.put(zone, confirmed);

        } else {

            // ETAPE 2 : Traitement du debit
            Instant previous = lastFlow.get(zone);

            // Ignorer les anciennes mesures
            if (previous != null
                    && !time.isAfter(previous)) {
                return List.of();
            }

            lastFlow.put(zone, time);

            // Le debit depasse-t-il le seuil maximum ?
            highFlow.put(zone, value > maxFlow);
        }

        // ETAPE 3 : Verification des deux conditions
        boolean pressureCondition =
                lowPressure.getOrDefault(zone, false);

        boolean flowCondition =
                highFlow.getOrDefault(zone, false);

        if (!pressureCondition || !flowCondition) {
            return List.of();
        }

        // ETAPE 4 : Verification de la fraicheur des mesures
        Instant pressureTime = lastPressure.get(zone);
        Instant flowTime = lastFlow.get(zone);

        if (pressureTime == null || flowTime == null) {
            return List.of();
        }

        Duration gap = Duration.between(
                pressureTime, flowTime
        ).abs();

        // Si les mesures sont trop eloignees, pas d'anomalie
        if (gap.compareTo(MAX_READING_GAP) > 0) {
            return List.of();
        }

        // ETAPE 5 : Creation de l'anomalie
        Anomaly anomaly = new Anomaly(
                "1",
                UUID.randomUUID().toString(),
                "LEAK_SUSPECTED",
                zone,
                reading.sensorId(),
                time,
                clock.instant()
        );

        return List.of(anomaly);
    }
}
