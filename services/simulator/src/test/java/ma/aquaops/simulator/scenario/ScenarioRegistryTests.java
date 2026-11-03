package ma.aquaops.simulator.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

/**
 * Tests unitaires du registre avec une horloge faussee : aucune attente reelle.
 * C'est ce qui justifie l'injection du Clock dans ScenarioRegistry.
 */
class ScenarioRegistryTests {

    private static final Instant DEPART = Instant.parse("2026-11-03T10:00:00Z");

    /**
     * Horloge de test : commence sur Clock.fixed(DEPART, UTC), puis est remplacee
     * par une seconde horloge avancee (Clock.offset). Necessaire car le registre
     * recoit son Clock une seule fois, au constructeur.
     */
    private static final class HorlogeDeTest extends Clock {
        private Clock courante = Clock.fixed(DEPART, ZoneOffset.UTC);

        void avancer(Duration duree) {
            courante = Clock.offset(courante, duree);
        }

        @Override
        public Instant instant() {
            return courante.instant();
        }

        @Override
        public ZoneId getZone() {
            return courante.getZone();
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return courante.withZone(zone);
        }
    }

    @Test
    void laFuiteEstActive30sApresLeDemarrage() {
        // Préparer
        HorlogeDeTest horloge = new HorlogeDeTest();
        ScenarioRegistry registry = new ScenarioRegistry(horloge);
        registry.demarrer("fuite");

        // Agir
        horloge.avancer(Duration.ofSeconds(30));
        Sample pression = registry.mesure(ReadingType.PRESSURE);

        // Vérifier
        assertEquals("fuite", registry.actif());
        assertEquals(LeakProfile.PRESSION_FUITE, pression.value());
    }

    @Test
    void revientAuNormalAuDelaDeLaDureeDuScenario() {
        // Préparer
        HorlogeDeTest horloge = new HorlogeDeTest();
        ScenarioRegistry registry = new ScenarioRegistry(horloge);
        registry.demarrer("fuite");

        // Agir : plus de 2 minutes plus tard
        horloge.avancer(new LeakProfile().duration().plusSeconds(1));
        Sample pression = registry.mesure(ReadingType.PRESSURE);

        // Vérifier
        assertEquals(NormalProfile.PRESSION_NORMALE, pression.value());
        assertEquals("normal", registry.actif());
    }

    @Test
    void refuseUnScenarioInconnuEtGardeLeScenarioPrecedent() {
        // Préparer
        HorlogeDeTest horloge = new HorlogeDeTest();
        ScenarioRegistry registry = new ScenarioRegistry(horloge);
        registry.demarrer("fuite");
        horloge.avancer(Duration.ofSeconds(30));

        // Agir
        assertThrows(IllegalArgumentException.class, () -> registry.demarrer("inconnu"));

        // Vérifier : toujours la fuite, et son heure de demarrage n'a pas bouge
        assertEquals("fuite", registry.actif());
        assertEquals(LeakProfile.PRESSION_FUITE, registry.mesure(ReadingType.PRESSURE).value());
    }
}
