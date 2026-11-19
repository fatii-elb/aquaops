package ma.aquaops.detection.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import ma.aquaops.detection.model.SensorReading;
import ma.aquaops.detection.threshold.ZoneThresholds;
import org.junit.jupiter.api.Test;

/** FR-13 : la regle de fuite lit les seuils de la zone a chaque mesure, sans etre recreee. */
class LeakRuleZoneThresholdsTest {

    private static final Instant T0 = Instant.parse("2026-12-07T10:00:00Z");

    private static ZoneThresholds thresholds(double minPressure, double maxFlow, long seconds) {
        return new ZoneThresholds(minPressure, maxFlow, seconds, 10, 2.0);
    }

    private static SensorReading reading(int zone, String type, double value, int secondsAfterT0) {
        return new SensorReading("1", type.equals("FLOW") ? 2 : 1, zone, type, value, "u", null,
            T0.plusSeconds(secondsAfterT0));
    }

    /** Pression basse pendant la duree configuree, puis debit eleve : fuite si les deux seuils sont depasses. */
    private static int leaks(LeakRule rule, int zone, double pressure, double flow, int base) {
        rule.evaluate(reading(zone, "PRESSURE", pressure, base));
        rule.evaluate(reading(zone, "PRESSURE", pressure, base + 10));
        return rule.evaluate(reading(zone, "FLOW", flow, base + 10)).size();
    }

    @Test
    void chaqueZoneAsesSeuils() {
        LeakRule rule = new LeakRule(zone -> zone == 1 ? thresholds(2.0, 40.0, 10) : thresholds(2.0, 60.0, 10),
            Clock.systemUTC());

        assertEquals(1, leaks(rule, 1, 1.0, 50.0, 0));  // 50 > 40 : fuite
        assertEquals(0, leaks(rule, 2, 1.0, 50.0, 0));  // 50 <= 60 : pas de fuite
    }

    @Test
    void unSeuilDeDebitModifieEstPrisEnCompteSansRecreerLaRegle() {
        AtomicReference<ZoneThresholds> current = new AtomicReference<>(thresholds(2.0, 60.0, 10));
        LeakRule rule = new LeakRule(zone -> current.get(), Clock.systemUTC());

        assertEquals(0, leaks(rule, 1, 1.0, 50.0, 0));
        current.set(thresholds(2.0, 40.0, 10));
        assertEquals(1, leaks(rule, 1, 1.0, 50.0, 100));
    }

    @Test
    void unSeuilDePressionModifieEstPrisEnCompteSansRecreerLaRegle() {
        AtomicReference<ZoneThresholds> current = new AtomicReference<>(thresholds(0.5, 40.0, 10));
        LeakRule rule = new LeakRule(zone -> current.get(), Clock.systemUTC());

        assertEquals(0, leaks(rule, 1, 1.0, 50.0, 0));   // 1.0 >= 0.5 : pression normale
        current.set(thresholds(2.0, 40.0, 10));
        assertEquals(1, leaks(rule, 1, 1.0, 50.0, 100)); // 1.0 < 2.0 : pression basse
    }

    @Test
    void laDureeDePressionBasseEstCelleDeLaZone() {
        LeakRule rule = new LeakRule(zone -> thresholds(2.0, 40.0, zone == 1 ? 10 : 30), Clock.systemUTC());

        assertEquals(1, leaks(rule, 1, 1.0, 50.0, 0)); // 10 s suffisent pour la zone 1
        assertEquals(0, leaks(rule, 2, 1.0, 50.0, 0)); // la zone 2 exige 30 s
    }

    @Test
    void leRetourALaNormaleUtiliseLesSeuilsDeLaZone() {
        AtomicReference<ZoneThresholds> current = new AtomicReference<>(thresholds(2.0, 40.0, 10));
        LeakRule rule = new LeakRule(zone -> current.get(), Clock.systemUTC());
        SensorReading normalPressure = reading(1, "PRESSURE", 1.5, 200);

        rule.evaluate(normalPressure);
        assertTrue(!rule.confirmsRecovery(normalPressure)); // 1.5 < 2.0
        current.set(thresholds(1.0, 40.0, 10));
        SensorReading again = reading(1, "PRESSURE", 1.5, 201);
        rule.evaluate(again);
        assertTrue(rule.confirmsRecovery(again));            // 1.5 >= 1.0
    }
}
