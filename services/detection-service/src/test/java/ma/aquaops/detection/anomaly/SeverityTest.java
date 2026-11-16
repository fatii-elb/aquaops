package ma.aquaops.detection.anomaly;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SeverityTest {

    @Test
    void laGraviteDependDuType() {
        assertEquals(Severity.CRITICAL, Severity.forType("LEAK_SUSPECTED"));
        assertEquals(Severity.HIGH, Severity.forType("PUMP_FAULT"));
        assertEquals(Severity.MEDIUM, Severity.forType("SENSOR_SILENT"));
        assertEquals(Severity.LOW, Severity.forType("LOW_LEVEL"));
    }

    @Test
    void unTypeInconnuOuAbsentEstMoyen() {
        assertEquals(Severity.MEDIUM, Severity.forType("AUTRE"));
        assertEquals(Severity.MEDIUM, Severity.forType(null));
    }

    @Test
    void laCleDeDeduplicationEstParCapteurPourLeSilenceEtParZoneSinon() {
        assertEquals("SENSOR_SILENT:3:12", AnomalyStore.dedupKey("SENSOR_SILENT", 3, 12));
        assertEquals("LEAK_SUSPECTED:3", AnomalyStore.dedupKey("LEAK_SUSPECTED", 3, 12));
        assertEquals("PUMP_FAULT:3", AnomalyStore.dedupKey("PUMP_FAULT", 3, null));
    }
}
