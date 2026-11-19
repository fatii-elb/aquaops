package ma.aquaops.detection.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import ma.aquaops.detection.model.Anomaly;
import ma.aquaops.detection.model.SensorReading;
import ma.aquaops.detection.threshold.ThresholdProvider;
import ma.aquaops.detection.threshold.ZoneThresholds;
import org.junit.jupiter.api.Test;

/** FR-12 : anomalie sous le seuil minimal de la zone, aucune au-dessus ni a egalite. */
class LowLevelRuleTest {

    private static final Instant T0 = Instant.parse("2026-12-16T10:00:00Z");

    private static ZoneThresholds withMinLevel(double minLevel) {
        return new ZoneThresholds(2.0, 40.0, 10, 10, minLevel);
    }

    private static SensorReading level(int zone, Double value) {
        return new SensorReading("1", 3, zone, "LEVEL", value, "m", null, T0);
    }

    private final LowLevelRule rule = new LowLevelRule(ThresholdProvider.fixed(withMinLevel(2.0)), Clock.systemUTC());

    @Test
    void unNiveauStrictementSousLeSeuilCreeUneAnomalie() {
        List<Anomaly> anomalies = rule.evaluate(level(1, 1.99));

        assertEquals(1, anomalies.size());
        Anomaly a = anomalies.get(0);
        assertEquals("LOW_LEVEL", a.type());
        assertEquals(1, a.zoneId());
        assertEquals(3, a.sensorId());
        assertEquals(T0, a.measuredAt());
        assertNotNull(a.anomalyId());
        assertNotNull(a.detectedAt());
    }

    @Test
    void unNiveauEgalAuSeuilNeCreePasDAnomalie() {
        assertTrue(rule.evaluate(level(1, 2.0)).isEmpty());
    }

    @Test
    void unNiveauAuDessusDuSeuilNeCreePasDAnomalie() {
        assertTrue(rule.evaluate(level(1, 5.0)).isEmpty());
    }

    @Test
    void unNiveauNulOuNegatifEstUneAnomalie() {
        assertEquals(1, rule.evaluate(level(1, 0.0)).size());
        assertEquals(1, rule.evaluate(level(1, -0.5)).size());
    }

    @Test
    void lesAutresTypesDeMesureSontIgnores() {
        SensorReading pressure = new SensorReading("1", 3, 1, "PRESSURE", 0.1, "bar", null, T0);
        SensorReading pump = new SensorReading("1", 3, 1, "PUMP_STATE", null, null, "FAULT", T0);
        assertTrue(rule.evaluate(pressure).isEmpty());
        assertTrue(rule.evaluate(pump).isEmpty());
    }

    @Test
    void lesMesuresIncompletesOuInvalidesSontIgnorees() {
        assertTrue(rule.evaluate(level(1, null)).isEmpty());
        assertTrue(rule.evaluate(level(1, Double.NaN)).isEmpty());
        assertTrue(rule.evaluate(new SensorReading("1", 3, null, "LEVEL", 0.1, "m", null, T0)).isEmpty());
        assertTrue(rule.evaluate(new SensorReading("1", 3, 1, "LEVEL", 0.1, "m", null, null)).isEmpty());
    }

    @Test
    void chaqueZoneUtiliseSonPropreSeuil() {
        LowLevelRule perZone = new LowLevelRule(
            zone -> withMinLevel(zone == 1 ? 2.0 : 8.0), Clock.systemUTC());

        assertTrue(perZone.evaluate(level(1, 5.0)).isEmpty());
        assertEquals(1, perZone.evaluate(level(2, 5.0)).size());
    }

    @Test
    void unSeuilModifieEstPrisEnCompteSansRecreerLaRegle() {
        AtomicReference<ZoneThresholds> current = new AtomicReference<>(withMinLevel(2.0));
        LowLevelRule dynamic = new LowLevelRule(zone -> current.get(), Clock.systemUTC());

        assertTrue(dynamic.evaluate(level(1, 5.0)).isEmpty());
        current.set(withMinLevel(6.0));
        assertEquals(1, dynamic.evaluate(level(1, 5.0)).size());
        current.set(withMinLevel(5.0)); // egalite : plus d'anomalie
        assertTrue(dynamic.evaluate(level(1, 5.0)).isEmpty());
    }

    @Test
    void leRetourALaNormaleEstConfirmeAuSeuilEtAuDessus() {
        assertTrue(rule.confirmsRecovery(level(1, 2.0)));
        assertTrue(rule.confirmsRecovery(level(1, 4.0)));
        assertFalse(rule.confirmsRecovery(level(1, 1.9)));
        assertFalse(rule.confirmsRecovery(level(1, null)));
        assertFalse(rule.confirmsRecovery(new SensorReading("1", 3, 1, "FLOW", 9.0, "m3", null, T0)));
        assertFalse(rule.confirmsRecovery(null));
    }
}
