package ma.aquaops.detection.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import ma.aquaops.detection.model.SensorReading;
import ma.aquaops.detection.rules.PumpFaultRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** BE-1 : le moteur previent l'ecouteur une seule fois, au vrai retour a la normale. */
class RecoveryListenerTest {

    private static final Instant T0 = Instant.parse("2026-11-09T10:00:00Z");

    private AnomalyRecoveryListener listener;
    private DetectionEngine engine;

    @BeforeEach
    void setUp() {
        listener = mock(AnomalyRecoveryListener.class);
        engine = new DetectionEngine(List.of(new PumpFaultRule(Clock.systemUTC())));
        engine.setRecoveryListener(listener);
    }

    private SensorReading pump(String state, int secondsAfterT0) {
        return new SensorReading("1", 5, 3, "PUMP_STATE", null, null, state, T0.plusSeconds(secondsAfterT0));
    }

    @Test
    void previentUneFoisQuandLaPompeRevientALaNormale() {
        engine.process(pump("FAULT", 0));
        engine.process(pump("NORMAL", 1));
        engine.process(pump("NORMAL", 2));

        verify(listener, times(1)).onRecovered(3, "PUMP_FAULT");
    }

    @Test
    void neFaitRienSansAnomalieOuverte() {
        engine.process(pump("NORMAL", 0));

        verify(listener, never()).onRecovered(3, "PUMP_FAULT");
    }

    @Test
    void uneAnomalieEstDeNouveauPubliableApresRetourALaNormale() {
        assertEquals(1, engine.process(pump("FAULT", 0)).size());
        engine.process(pump("NORMAL", 1));
        assertEquals(1, engine.process(pump("FAULT", 2)).size());
    }

    @Test
    void unEchecDeLEcouteurNArretePasLaDetection() {
        doThrow(new IllegalStateException("base indisponible")).when(listener).onRecovered(3, "PUMP_FAULT");

        engine.process(pump("FAULT", 0));
        assertTrue(engine.process(pump("NORMAL", 1)).isEmpty());
        assertEquals(1, engine.process(pump("FAULT", 2)).size());
    }

    @Test
    void leMoteurFonctionneSansEcouteur() {
        DetectionEngine alone = new DetectionEngine(List.of(new PumpFaultRule(Clock.systemUTC())));

        assertEquals(1, alone.process(pump("FAULT", 0)).size());
        assertFalse(alone.process(pump("NORMAL", 1)).iterator().hasNext());
    }

    @Test
    void leDeduplicateurIndiqueSiUneAnomalieEtaitOuverte() {
        AnomalyDeduplicator dedup = new AnomalyDeduplicator();

        assertFalse(dedup.recover(1, "LEAK_SUSPECTED"));
        dedup.tryOpen(1, "LEAK_SUSPECTED");
        assertTrue(dedup.recover(1, "LEAK_SUSPECTED"));
        assertFalse(dedup.recover(1, "LEAK_SUSPECTED"));
    }
}
