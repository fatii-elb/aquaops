package ma.aquaops.simulator.scenario;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Pilotage des scenarios de panne (FR-06). */
@RestController
@RequestMapping("/api/simulator/scenarios")
public class ScenarioController {

    private final ScenarioRegistry registry;

    public ScenarioController(ScenarioRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    public Map<String, Object> etat() {
        return Map.of("disponibles", registry.noms(), "actif", registry.actif());
    }

    @PostMapping("/{nom}/start")
    public ResponseEntity<Map<String, Object>> demarrer(@PathVariable String nom) {
        try {
            registry.demarrer(nom);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
        return ResponseEntity.ok(etat());
    }

    @PostMapping("/stop")
    public Map<String, Object> arreter() {
        registry.arreter();
        return etat();
    }
}
