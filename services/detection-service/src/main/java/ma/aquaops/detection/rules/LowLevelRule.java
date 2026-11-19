package ma.aquaops.detection.rules;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import ma.aquaops.detection.model.Anomaly;
import ma.aquaops.detection.model.SensorReading;
import ma.aquaops.detection.threshold.ThresholdProvider;

/**
 * Niveau de reservoir bas (FR-12) : une mesure LEVEL strictement inferieure au seuil minimal de sa zone
 * cree une anomalie LOW_LEVEL. Un niveau egal ou superieur au seuil n'en cree pas.
 * Meme structure que PumpFaultRule : la deduplication, la persistance et le retour a la normale sont ceux
 * du moteur de detection (DetectionEngine), il n'y a pas de second moteur.
 */
public class LowLevelRule implements Rule {

    public static final String TYPE = "LOW_LEVEL";

    private final ThresholdProvider thresholds;
    private final Clock clock;

    public LowLevelRule(ThresholdProvider thresholds, Clock clock) {
        this.thresholds = thresholds;
        this.clock = clock;
    }

    @Override
    public List<Anomaly> evaluate(SensorReading reading) {
        if (!isUsableLevel(reading)) {
            return List.of();
        }
        double minLevel = thresholds.forZone(reading.zoneId()).tankMinLevel();
        if (reading.value() < minLevel) {
            return List.of(new Anomaly(
                    "1",
                    UUID.randomUUID().toString(),
                    TYPE,
                    reading.zoneId(),
                    reading.sensorId(),
                    reading.measuredAt(),
                    clock.instant()));
        }
        return List.of();
    }

    /** Vrai quand la mesure montre un niveau redevenu normal (au moins egal au seuil) : l'anomalie ouverte se termine. */
    public boolean confirmsRecovery(SensorReading reading) {
        return isUsableLevel(reading)
                && reading.value() >= thresholds.forZone(reading.zoneId()).tankMinLevel();
    }

    private static boolean isUsableLevel(SensorReading reading) {
        return reading != null
                && "LEVEL".equals(reading.type())
                && reading.zoneId() != null
                && reading.measuredAt() != null
                && reading.value() != null
                && Double.isFinite(reading.value());
    }
}
