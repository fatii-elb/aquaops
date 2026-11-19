package ma.aquaops.detection.threshold;

/** Fournit les seuils en vigueur pour une zone ; les regles l'interrogent a chaque mesure. */
@FunctionalInterface
public interface ThresholdProvider {

    ZoneThresholds forZone(int zoneId);

    /** Memes seuils pour toutes les zones (tests et usage sans base). */
    static ThresholdProvider fixed(ZoneThresholds thresholds) {
        return zoneId -> thresholds;
    }
}
