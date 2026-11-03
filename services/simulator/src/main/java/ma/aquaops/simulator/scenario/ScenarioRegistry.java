package ma.aquaops.simulator.scenario;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Garde le scenario actif et son heure de demarrage.
 * L'heure vient d'un Clock injecte, pour que les tests puissent fausser le temps.
 */
@Component
public class ScenarioRegistry {

    private final Clock clock;
    private final ScenarioProfile normal = new NormalProfile();
    private final Map<String, ScenarioProfile> profils = new LinkedHashMap<>();

    private ScenarioProfile actif;
    private Instant demarreA;

    public ScenarioRegistry(Clock clock) {
        this.clock = clock;
        for (ScenarioProfile p : List.of(normal, new LeakProfile(), new PumpFaultProfile(),
                new SensorSilentProfile(), new LoadPeakProfile())) {
            profils.put(p.name(), p);
        }
        this.actif = normal;
        this.demarreA = clock.instant();
    }

    /** Noms des scenarios disponibles. */
    public List<String> noms() {
        return List.copyOf(profils.keySet());
    }

    /** Nom du scenario actif ; "normal" si aucun ou si sa duree est depassee. */
    public synchronized String actif() {
        return profilCourant().name();
    }

    /** @throws IllegalArgumentException si le nom est inconnu */
    public synchronized void demarrer(String nom) {
        ScenarioProfile p = profils.get(nom);
        if (p == null) {
            throw new IllegalArgumentException("Scenario inconnu : " + nom);
        }
        this.actif = p;
        this.demarreA = clock.instant();
    }

    public synchronized void arreter() {
        this.actif = normal;
        this.demarreA = clock.instant();
    }

    /** Ce que le capteur du type donne doit emettre maintenant. */
    public synchronized Sample mesure(ReadingType type) {
        ScenarioProfile p = profilCourant();
        return p.sampleAt(type, ecoule());
    }

    private ScenarioProfile profilCourant() {
        if (actif != normal && ecoule().compareTo(actif.duration()) > 0) {
            arreter();
        }
        return actif;
    }

    private Duration ecoule() {
        return Duration.between(demarreA, clock.instant());
    }
}
