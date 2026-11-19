package ma.aquaops.detection.threshold;

/**
 * Corps de PUT /api/detection/thresholds/{zoneId} et valeurs renvoyees par l'API.
 * Champs en objets (Double, Long) pour distinguer un champ absent d'une valeur nulle.
 */
public record ThresholdBody(
        Double leakMinPressure,
        Double leakMaxFlow,
        Long leakDurationSeconds,
        Long silenceThresholdSeconds,
        Double tankMinLevel) {

    public static ThresholdBody of(ZoneThresholds t) {
        return new ThresholdBody(t.leakMinPressure(), t.leakMaxFlow(), t.leakDurationSeconds(),
                t.silenceThresholdSeconds(), t.tankMinLevel());
    }
}
