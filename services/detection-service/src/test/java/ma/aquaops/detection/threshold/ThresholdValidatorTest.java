package ma.aquaops.detection.threshold;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ThresholdValidatorTest {

    private static ThresholdBody valid() {
        return new ThresholdBody(2.0, 40.0, 10L, 10L, 2.0);
    }

    private static List<String> fields(ThresholdBody b) {
        return ThresholdValidator.validate(b).stream().map(FieldError::field).toList();
    }

    @Test
    void desSeuilsCorrectsSontAcceptes() {
        assertTrue(ThresholdValidator.validate(valid()).isEmpty());
        assertTrue(ThresholdValidator.validate(new ThresholdBody(0.0, 0.1, 1L, 3600L, 0.0)).isEmpty());
    }

    @Test
    void uneAbsenceDeCorpsEstRefusee() {
        assertEquals(List.of("body"), fields(null));
    }

    @Test
    void chaqueChampEstObligatoire() {
        assertEquals(
            List.of("leakMinPressure", "leakMaxFlow", "leakDurationSeconds", "silenceThresholdSeconds", "tankMinLevel"),
            fields(new ThresholdBody(null, null, null, null, null)));
    }

    @Test
    void lesValeursNegativesOuNullesInterditesSontRefusees() {
        assertEquals(List.of("leakMinPressure"), fields(new ThresholdBody(-0.1, 40.0, 10L, 10L, 2.0)));
        assertEquals(List.of("leakMaxFlow"), fields(new ThresholdBody(2.0, 0.0, 10L, 10L, 2.0)));
        assertEquals(List.of("tankMinLevel"), fields(new ThresholdBody(2.0, 40.0, 10L, 10L, -1.0)));
    }

    @Test
    void lesDureesSontBornees() {
        assertEquals(List.of("leakDurationSeconds"), fields(new ThresholdBody(2.0, 40.0, 0L, 10L, 2.0)));
        assertEquals(List.of("silenceThresholdSeconds"), fields(new ThresholdBody(2.0, 40.0, 10L, 3601L, 2.0)));
    }

    @Test
    void lesValeursAbsurdesOuNonNumeriquesSontRefusees() {
        assertEquals(List.of("leakMinPressure"), fields(new ThresholdBody(100.1, 40.0, 10L, 10L, 2.0)));
        assertEquals(List.of("leakMaxFlow"), fields(new ThresholdBody(2.0, 100_001.0, 10L, 10L, 2.0)));
        assertEquals(List.of("tankMinLevel"), fields(new ThresholdBody(2.0, 40.0, 10L, 10L, Double.NaN)));
        assertEquals(List.of("leakMaxFlow"), fields(new ThresholdBody(2.0, Double.POSITIVE_INFINITY, 10L, 10L, 2.0)));
    }

    @Test
    void toutesLesErreursSontRenvoyeesEnUneFois() {
        assertEquals(3, ThresholdValidator.validate(new ThresholdBody(-1.0, -1.0, 0L, 10L, 2.0)).size());
    }

    @Test
    void laConversionRepreneLesValeurs() {
        ZoneThresholds t = ThresholdValidator.toThresholds(new ThresholdBody(1.5, 30.0, 8L, 12L, 3.5));
        assertEquals(new ZoneThresholds(1.5, 30.0, 8, 12, 3.5), t);
    }
}
