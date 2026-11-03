
package ma.aquaops.detection.engine;

import ma.aquaops.detection.model.SensorReading;
import ma.aquaops.detection.rules.PumpFaultRule;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DetectionEngineDeduplicationTest {

    private final Instant start =
            Instant.parse("2026-11-02T10:00:00Z");

    // Creer le moteur de detection pour les tests
    private DetectionEngine newEngine() {

        Clock clock = Clock.fixed(
                start,
                ZoneOffset.UTC
        );

        return new DetectionEngine(
                List.of(new PumpFaultRule(clock))
        );
    }

    // Simuler une mesure de pompe
    private SensorReading pump(
            int zone,
            String state,
            long seconds) {

        return new SensorReading(
                "1",
                15,
                zone,
                "PUMP_STATE",
                null,
                null,
                state,
                start.plusSeconds(seconds)
        );
    }

    // TEST 1 : 100 defauts -> 1 anomalie
    @Test
    void shouldPublishOnlyOnceForHundredFaults() {

        DetectionEngine engine = newEngine();

        int published = 0;

        for (int i = 0; i < 100; i++) {

            published += engine.process(
                    pump(3, "FAULT", i)
            ).size();
        }

        assertEquals(1, published);
    }

    // TEST 2 : Nouvelle anomalie apres recuperation
    @Test
    void shouldPublishAgainAfterRecovery() {

        DetectionEngine engine = newEngine();

        assertEquals(1, engine.process(
                pump(3, "FAULT", 0)
        ).size());

        assertTrue(engine.process(
                pump(3, "FAULT", 1)
        ).isEmpty());

        assertTrue(engine.process(
                pump(3, "NORMAL", 2)
        ).isEmpty());

        assertEquals(1, engine.process(
                pump(3, "FAULT", 3)
        ).size());
    }

    // TEST 3 : Zones independantes
    @Test
    void shouldKeepZonesIndependent() {

        DetectionEngine engine = newEngine();

        assertEquals(1, engine.process(
                pump(3, "FAULT", 0)
        ).size());

        assertEquals(1, engine.process(
                pump(4, "FAULT", 1)
        ).size());

        assertTrue(engine.process(
                pump(3, "FAULT", 2)
        ).isEmpty());
    }

    // TEST 4 : Ignorer un ancien NORMAL
    @Test
    void shouldIgnoreOldNormalReading() {

        DetectionEngine engine = newEngine();

        // Premier defaut a t = 10
        assertEquals(1, engine.process(
                pump(3, "FAULT", 10)
        ).size());

        // Ancien NORMAL a t = 5
        assertTrue(engine.process(
                pump(3, "NORMAL", 5)
        ).isEmpty());

        // Le defaut doit rester ouvert
        assertTrue(engine.process(
                pump(3, "FAULT", 11)
        ).isEmpty());
    }

    // TEST 5 : Ignorer un ancien FAULT
    @Test
    void shouldIgnoreOldFaultAfterRecovery() {

        DetectionEngine engine = newEngine();

        // Premier defaut
        assertEquals(1, engine.process(
                pump(3, "FAULT", 10)
        ).size());

        // Retour a la normale
        assertTrue(engine.process(
                pump(3, "NORMAL", 12)
        ).isEmpty());

        // Ancien FAULT arrive en retard
        assertTrue(engine.process(
                pump(3, "FAULT", 11)
        ).isEmpty());

        // Nouveau defaut reel
        assertEquals(1, engine.process(
                pump(3, "FAULT", 13)
        ).size());
    }
}
