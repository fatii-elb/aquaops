package ma.aquaops.detection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Test exemple du cadre commun (TECH-5).
 * Test JUnit simple : pas de Spring, pas de base de données, pas de Kafka.
 * Schéma : préparer, agir, vérifier. À copier puis à remplacer par de vrais tests.
 */
class ExempleTests {

    @Test
    void convertitUneValeurEnBar() {
        // Préparer
        int valeurBrute = 24;
        // Agir
        double enBar = valeurBrute / 10.0;
        // Vérifier
        assertEquals(2.4, enBar, 0.0001);
    }

    @Test
    void refuseUneValeurNonNumerique() {
        assertThrows(NumberFormatException.class, () -> Integer.parseInt("abc"));
    }
}
