package ma.aquaops.incidents.incident;

/**
 * Regle de priorite (FR-15). Aucune dependance Spring : un changement de regle = quelques lignes ici.
 * Table (plan Sprint 3) : LEAK_SUSPECTED -> CRITICAL, PUMP_FAULT -> HIGH, SENSOR_SILENT -> MEDIUM, LOW_LEVEL -> LOW.
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
            case "LOW_LEVEL" -> Priority.LOW;
            case "SENSOR_SILENT" -> Priority.MEDIUM;
            default -> Priority.MEDIUM; // type inconnu : prudence, ni urgent ni ignore
        };
    }
}
