package ma.aquaops.incidents.incident;

/**
 * Regle de priorite (FR-15). Aucune dependance Spring : un changement de regle = quelques lignes ici.
 * Table proposee : LEAK_SUSPECTED -> CRITICAL, PUMP_FAULT -> HIGH, LOW_LEVEL -> MEDIUM, SENSOR_SILENT -> LOW.
 */
public final class PriorityCalculator {

    private PriorityCalculator() {
    }

    public static Priority forAnomalyType(String type) {
        if (type == null) {
            return Priority.MEDIUM;
        }
        return switch (type) {
            case "LEAK_SUSPECTED" -> Priority.CRITICAL;
            case "PUMP_FAULT" -> Priority.HIGH;
            case "LOW_LEVEL" -> Priority.MEDIUM;
            case "SENSOR_SILENT" -> Priority.LOW;
            default -> Priority.MEDIUM; // type inconnu : prudence, ni urgent ni ignore
        };
    }
}
