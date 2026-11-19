package ma.aquaops.detection.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import ma.aquaops.detection.model.SensorReading;
import ma.aquaops.detection.rules.LowLevelRule;
import ma.aquaops.detection.threshold.ThresholdProvider;
import ma.aquaops.detection.threshold.ZoneThresholds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** FR-12 dans le moteur existant : deduplication et retour a la normale, sans second moteur. */
class LowLevelEngineTest {

    private static final Instant T0 = Instant.parse("2026-12-16T10:00:00Z");

    private final List<String> recovered = new ArrayList<>();
    private DetectionEngine engine;

    @BeforeEach
    void setUp() {
        ThresholdProvider thresholds = ThresholdProvider.fixed(new ZoneThresholds(2.0, 40.0, 10, 10, 2.0));
        engine = new DetectionEngine(List.of(new LowLevelRule(thresholds, Clock.systemUTC())));
        engine.setRecoveryListener(new AnomalyRecoveryListener() {
            @Override
            public void onRecovered(int zoneId, String type) {
                recovered.add(zoneId + ":" + type);
            }
        });
    }

    private SensorReading level(double value, int secondsAfterT0) {
        return new SensorReading("1", 3, 5, "LEVEL", value, "m", null, T0.plusSeconds(secondsAfterT0));
    }

    @Test
    void centMesuresBassesDonnentUneSeuleAnomalie() {
        int published = 0;
        for (int i = 0; i < 100; i++) {
            published += engine.process(level(1.0, i)).size();
        }
        assertEquals(1, published);
    }

    @Test
    void uneAnomalieEstDeNouveauPossibleApresLeRetourALaNormale() {
        assertEquals(1, engine.process(level(1.0, 0)).size());
        assertTrue(engine.process(level(4.0, 1)).isEmpty());
        assertEquals(List.of("5:LOW_LEVEL"), recovered);
        assertEquals(1, engine.process(level(1.0, 2)).size());
    }

    @Test
    void unNiveauNormalNeFaitRien() {
        assertTrue(engine.process(level(4.0, 0)).isEmpty());
        assertTrue(recovered.isEmpty());
    }

    @Test
    void leRetourALaNormaleNEstSignaleQuUneFois() {
        engine.process(level(1.0, 0));
        engine.process(level(4.0, 1));
        engine.process(level(4.0, 2));
        assertEquals(1, recovered.size());
    }
}
