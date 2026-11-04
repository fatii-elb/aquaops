package ma.aquaops.incidents.incident;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PriorityCalculatorTest {

    @ParameterizedTest
    @CsvSource({
            "LEAK_SUSPECTED,CRITICAL",
            "PUMP_FAULT,HIGH",
            "LOW_LEVEL,MEDIUM",
            "SENSOR_SILENT,LOW",
            "TYPE_INCONNU,MEDIUM"
    })
    void priorityByType(String type, Priority expected) {
        assertEquals(expected, PriorityCalculator.forAnomalyType(type));
    }

    @org.junit.jupiter.api.Test
    void nullTypeIsMedium() {
        assertEquals(Priority.MEDIUM, PriorityCalculator.forAnomalyType(null));
    }
}
