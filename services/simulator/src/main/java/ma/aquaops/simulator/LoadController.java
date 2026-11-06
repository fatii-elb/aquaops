package ma.aquaops.simulator;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Pilotage du mode charge (NFR-01). */
@RestController
@RequestMapping("/api/simulator/load")
public class LoadController {

    private final LoadGenerator load;

    public LoadController(LoadGenerator load) {
        this.load = load;
    }

    @GetMapping
    public LoadGenerator.Status etat() {
        return load.status();
    }

    @PostMapping("/start")
    public ResponseEntity<Object> demarrer(@RequestParam(defaultValue = "500") int rate,
                                           @RequestParam(defaultValue = "300") int seconds) {
        try {
            load.start(rate, seconds);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(java.util.Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", e.getMessage()));
        }
        return ResponseEntity.ok(load.status());
    }

    @PostMapping("/stop")
    public LoadGenerator.Status arreter() {
        load.stop();
        return load.status();
    }
}