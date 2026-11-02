
package ma.aquaops.detection.rules;

import ma.aquaops.detection.model.SensorReading;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class LeakRuleTest {

    private final Instant start =
            Instant.parse("2026-11-02T10:00:00Z");

    private LeakRule newRule() {
        return new LeakRule(
                2.0,
                40.0,
                Duration.ofSeconds(10),
                Clock.fixed(start, ZoneOffset.UTC)
        );
    }

    private SensorReading reading(
            String type, int zone,
            double value, long seconds) {

        return new SensorReading(
                "1",
                15,
                zone,
                type,
                value,
                "PRESSURE".equals(type) ? "bar" : "m3/h",
                null,
                start.plusSeconds(seconds)
        );
    }

    // TEST 1 : Pression basse + debit eleve
    @Test
    void shouldDetectLeakWhenBothConditionsAreTrue() {
        LeakRule rule = newRule();

        assertTrue(rule.evaluate(
                reading("PRESSURE", 3, 1.5, 0)).isEmpty());

        assertTrue(rule.evaluate(
                reading("FLOW", 3, 45.0, 2)).isEmpty());

        var result = rule.evaluate(
                reading("PRESSURE", 3, 1.4, 10));

        assertEquals(1, result.size());
        assertEquals("LEAK_SUSPECTED",
                result.get(0).type());
        assertEquals(3, result.get(0).zoneId());
    }

    // TEST 2 : Pression basse seulement
    @Test
    void shouldNotDetectWithLowPressureOnly() {
        LeakRule rule = newRule();

        rule.evaluate(reading("PRESSURE", 3, 1.5, 0));

        assertTrue(rule.evaluate(
                reading("PRESSURE", 3, 1.4, 10)).isEmpty());
    }

    // TEST 3 : Debit eleve seulement
    @Test
    void shouldNotDetectWithHighFlowOnly() {
        LeakRule rule = newRule();

        assertTrue(rule.evaluate(
                reading("FLOW", 3, 45.0, 0)).isEmpty());
    }

    // TEST 4 : Retour a une pression normale
    @Test
    void shouldResetAfterNormalPressure() {
        LeakRule rule = newRule();

        rule.evaluate(reading("PRESSURE", 3, 1.5, 0));
        rule.evaluate(reading("FLOW", 3, 45.0, 1));

        assertTrue(rule.evaluate(
                reading("PRESSURE", 3, 2.5, 5)).isEmpty());

        assertTrue(rule.evaluate(
                reading("PRESSURE", 3, 1.5, 10)).isEmpty());

        assertEquals(1, rule.evaluate(
                reading("PRESSURE", 3, 1.4, 20)).size());
    }

    // TEST 5 : Zones independantes
    @Test
    void shouldNotCombineDifferentZones() {
        LeakRule rule = newRule();

        rule.evaluate(reading("PRESSURE", 3, 1.5, 0));
        rule.evaluate(reading("FLOW", 4, 45.0, 1));

        assertTrue(rule.evaluate(
                reading("PRESSURE", 3, 1.4, 10)).isEmpty());
    }

    // TEST 6 : Ne pas utiliser un debit trop ancien
    @Test
    void shouldNotUseStaleFlow() {
        LeakRule rule = newRule();

        // Debit eleve a t = 0
        rule.evaluate(reading("FLOW", 3, 45.0, 0));

        // Pression basse a t = 1
        rule.evaluate(reading("PRESSURE", 3, 1.5, 1));

        // Pression toujours basse a t = 40
        // Le dernier debit date de 40 secondes
        assertTrue(rule.evaluate(
                reading("PRESSURE", 3, 1.4, 40)
        ).isEmpty());
    }

    // TEST 7 : Ne pas utiliser une pression trop ancienne
    @Test
    void shouldNotUseStalePressure() {
        LeakRule rule = newRule();

        // Pression basse a t = 0
        rule.evaluate(reading("PRESSURE", 3, 1.5, 0));

        // Pression toujours basse a t = 10
        rule.evaluate(reading("PRESSURE", 3, 1.4, 10));

        // Debit eleve a t = 50
        // La derniere pression date de 40 secondes
        assertTrue(rule.evaluate(
                reading("FLOW", 3, 45.0, 50)
        ).isEmpty());
    }
}
