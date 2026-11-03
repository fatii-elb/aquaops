
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

    // Duree maximale entre une mesure de pression et de debit
    private static final Duration MAX_READING_GAP =
            Duration.ofSeconds(30);

    private final PressureWindowTracker pressureTracker;
    private final double minPressure;
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

    // Conserver la derniere valeur acceptee pour chaque zone
    private final Map<Integer, Double> lastPressureValue =
            new HashMap<>();

    private final Map<Integer, Double> lastFlowValue =
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

        this.minPressure = minPressure;
        this.maxFlow = maxFlow;
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public synchronized List<Anomaly> evaluate(
            SensorReading reading) {

        String type = reading.type();

        // Seules les mesures PRESSURE et FLOW sont concernees
        if (!"PRESSURE".equals(type)
                && !"FLOW".equals(type)) {
            return List.of();
        }

        Integer zone = reading.zoneId();
        Instant time = reading.measuredAt();
        Double value = reading.value();

        // Validation des informations recues
        if (zone == null || time == null
                || value == null
                || !Double.isFinite(value)) {
            return List.of();
        }

        // ETAPE 1 : Verification de la pression
        if ("PRESSURE".equals(type)) {

            Instant previous = lastPressure.get(zone);

            // Ignorer les mesures anciennes ou dupliquees
            if (previous != null
                    && !time.isAfter(previous)) {
                return List.of();
            }

            lastPressure.put(zone, time);
            lastPressureValue.put(zone, value);

            boolean confirmed =
                    pressureTracker.update(reading);

            lowPressure.put(zone, confirmed);

        } else {

            // ETAPE 2 : Verification du debit
            Instant previous = lastFlow.get(zone);

            if (previous != null
                    && !time.isAfter(previous)) {
                return List.of();
            }

            lastFlow.put(zone, time);
            lastFlowValue.put(zone, value);

            highFlow.put(zone, value > maxFlow);
        }

        // ETAPE 3 : Les deux conditions doivent etre vraies
        boolean pressureCondition =
                lowPressure.getOrDefault(zone, false);

        boolean flowCondition =
                highFlow.getOrDefault(zone, false);

        if (!pressureCondition || !flowCondition) {
            return List.of();
        }

        // ETAPE 4 : Verifier la fraicheur des mesures
        Instant pressureTime = lastPressure.get(zone);
        Instant flowTime = lastFlow.get(zone);

        if (pressureTime == null || flowTime == null) {
            return List.of();
        }

        Duration gap = Duration.between(
                pressureTime, flowTime
        ).abs();

        if (gap.compareTo(MAX_READING_GAP) > 0) {
            return List.of();
        }

        // ETAPE 5 : Creer l'anomalie de fuite probable
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

    // ETAPE 6 : Confirmer le retour a la normale
    public synchronized boolean confirmsRecovery(
            SensorReading reading) {

        if (reading == null
                || reading.zoneId() == null
                || reading.measuredAt() == null
                || reading.value() == null
                || !Double.isFinite(reading.value())) {
            return false;
        }

        Integer zone = reading.zoneId();
        Instant time = reading.measuredAt();
        double value = reading.value();

        // La pression est redevenue normale
        if ("PRESSURE".equals(reading.type())) {

            return value >= minPressure
                    && time.equals(lastPressure.get(zone))
                    && Objects.equals(
                            value,
                            lastPressureValue.get(zone)
                    );
        }

        // Le debit est redevenu normal
        if ("FLOW".equals(reading.type())) {

            return value <= maxFlow
                    && time.equals(lastFlow.get(zone))
                    && Objects.equals(
                            value,
                            lastFlowValue.get(zone)
                    );
        }

        return false;
    }
}
