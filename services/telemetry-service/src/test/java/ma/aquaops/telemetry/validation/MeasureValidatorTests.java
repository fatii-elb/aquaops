package ma.aquaops.telemetry.validation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MeasureValidatorTests {

    private final MeasureValidator validateur = new MeasureValidator();

    private Map<String, Object> pression() {
        Map<String, Object> m = new HashMap<>();
        m.put("eventVersion", "1");
        m.put("sensorId", 12);
        m.put("zoneId", 3);
        m.put("type", "PRESSURE");
        m.put("value", 2.4);
        m.put("unit", "bar");
        m.put("measuredAt", "2026-10-12T21:30:00Z");
        return m;
    }

    private Map<String, Object> etatPompe() {
        Map<String, Object> m = new HashMap<>();
        m.put("eventVersion", "1");
        m.put("sensorId", 15);
        m.put("zoneId", 3);
        m.put("type", "PUMP_STATE");
        m.put("pumpState", "FAULT");
        m.put("measuredAt", "2026-10-12T21:30:01Z");
        return m;
    }

    @Test
    void accepteUneMesureDePressionValide() {
        assertTrue(validateur.validate(pression()).isValid());
    }

    @Test
    void accepteUnEtatDePompeValide() {
        assertTrue(validateur.validate(etatPompe()).isValid());
    }

    @Test
    void ignoreLesChampsInconnus() {
        Map<String, Object> m = pression();
        m.put("champInventeParUnAutreService", "x");
        assertTrue(validateur.validate(m).isValid());
    }

    @Test
    void refuseUnMessageVide() {
        assertFalse(validateur.validate(null).isValid());
    }

    @Test
    void refuseUneVersionInconnue() {
        Map<String, Object> m = pression();
        m.put("eventVersion", "2");
        assertFalse(validateur.validate(m).isValid());
    }

    @Test
    void refuseUnCapteurSansIdentifiant() {
        Map<String, Object> m = pression();
        m.remove("sensorId");
        assertFalse(validateur.validate(m).isValid());
    }

    @Test
    void refuseUneZoneNegative() {
        Map<String, Object> m = pression();
        m.put("zoneId", -1);
        assertFalse(validateur.validate(m).isValid());
    }

    @Test
    void refuseUnTypeInconnu() {
        Map<String, Object> m = pression();
        m.put("type", "TEMPERATURE");
        assertFalse(validateur.validate(m).isValid());
    }

    @Test
    void refuseUneMesureSansValeur() {
        Map<String, Object> m = pression();
        m.remove("value");
        assertFalse(validateur.validate(m).isValid());
    }

    @Test
    void refuseUneValeurNegative() {
        Map<String, Object> m = pression();
        m.put("value", -5.0);
        assertFalse(validateur.validate(m).isValid());
    }

    @Test
    void refuseUneValeurQuiNestPasUnNombre() {
        Map<String, Object> m = pression();
        m.put("value", "abc");
        assertFalse(validateur.validate(m).isValid());
    }

    @Test
    void refuseUnEtatDePompeSansEtat() {
        Map<String, Object> m = etatPompe();
        m.remove("pumpState");
        assertFalse(validateur.validate(m).isValid());
    }

    @Test
    void refuseUneDateMalformee() {
        Map<String, Object> m = pression();
        m.put("measuredAt", "hier soir");
        assertFalse(validateur.validate(m).isValid());
    }

    @Test
    void refuseUneMesureSansDate() {
        Map<String, Object> m = pression();
        m.remove("measuredAt");
        assertFalse(validateur.validate(m).isValid());
    }
}