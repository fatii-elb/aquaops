
package ma.aquaops.detection.engine;

import ma.aquaops.detection.model.SensorReading;
import ma.aquaops.detection.rules.LeakRule;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LeakRecoveryTest {

    private final Instant start =
            Instant.parse("2026-11-02T10:00:00Z");

    private DetectionEngine newEngine() {
        return new DetectionEngine(
                List.of(new LeakRule(
                        2.0,
                        40.0,
                        Duration.ofSeconds(10),
                        Clock.fixed(start, ZoneOffset.UTC)
                ))
        );
    }

    private SensorReading reading(
            String type,
            double value,
            long seconds) {

        return new SensorReading(
                "1",
                15,
                3,
                type,
                value,
                "PRESSURE".equals(type) ? "bar" : "m3/h",
                null,
                start.plusSeconds(seconds)
        );
    }

    // TEST 1 : Recuperation apres pression normale
    @Test
    void shouldReopenAfterPressureRecovery() {

        DetectionEngine engine = newEngine();

        engine.process(reading("PRESSURE", 1.5, 0));
        engine.process(reading("FLOW", 45.0, 2));

        // Premiere fuite
        assertEquals(1, engine.process(
                reading("PRESSURE", 1.4, 10)
        ).size());

        // Fuite persistante : aucun doublon
        assertTrue(engine.process(
                reading("PRESSURE", 1.3, 11)
        ).isEmpty());

        // Retour de la pression a la normale
        assertTrue(engine.process(
                reading("PRESSURE", 2.5, 12)
        ).isEmpty());

        // Nouvelle periode de pression basse
        engine.process(reading("PRESSURE", 1.5, 13));
        engine.process(reading("FLOW", 46.0, 14));

        // Nouvelle fuite : nouvelle anomalie
        assertEquals(1, engine.process(
                reading("PRESSURE", 1.4, 23)
        ).size());
    }

    // TEST 2 : Recuperation apres debit normal
    @Test
    void shouldReopenAfterFlowRecovery() {

        DetectionEngine engine = newEngine();

        engine.process(reading("PRESSURE", 1.5, 0));
        engine.process(reading("FLOW", 45.0, 2));

        // Premiere anomalie
        assertEquals(1, engine.process(
                reading("PRESSURE", 1.4, 10)
        ).size());

        // Debit normal : recuperation
        assertTrue(engine.process(
                reading("FLOW", 25.0, 11)
        ).isEmpty());

        // Nouveau debit eleve :
        // la pression est toujours basse
        assertEquals(1, engine.process(
                reading("FLOW", 46.0, 12)
        ).size());
    }

    // TEST 3 : Ne pas accepter une ancienne recuperation
    @Test
    void shouldIgnoreOldRecoveryReading() {

        DetectionEngine engine = newEngine();

        engine.process(reading("PRESSURE", 1.5, 0));
        engine.process(reading("FLOW", 45.0, 2));

        assertEquals(1, engine.process(
                reading("PRESSURE", 1.4, 10)
        ).size());

        engine.process(reading("PRESSURE", 1.3, 11));

        // Mesure normale ancienne : doit etre ignoree
        assertTrue(engine.process(
                reading("PRESSURE", 2.5, 9)
        ).isEmpty());

        // Fuite toujours ouverte
        assertTrue(engine.process(
                reading("PRESSURE", 1.3, 12)
        ).isEmpty());
    }
}
