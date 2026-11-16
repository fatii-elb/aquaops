package ma.aquaops.detection.anomaly;

/**
 * Gravite d'une anomalie, deduite du type a la lecture (le contrat Kafka ne la porte pas).
 * Meme table que PriorityCalculator d'incidents-service : LEAK_SUSPECTED -> CRITICAL, PUMP_FAULT -> HIGH,
 * SENSOR_SILENT -> MEDIUM, LOW_LEVEL -> LOW. Type inconnu : MEDIUM.
 */
public enum Severity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    public static Severity forType(String type) {
        if (type == null) {
            return MEDIUM;
        }
        return switch (type) {
            case "LEAK_SUSPECTED" -> CRITICAL;
            case "PUMP_FAULT" -> HIGH;
            case "LOW_LEVEL" -> LOW;
            default -> MEDIUM;
        };
    }
}
