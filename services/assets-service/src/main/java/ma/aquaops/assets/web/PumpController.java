package ma.aquaops.assets.web;

import jakarta.validation.Valid;
import java.util.List;
import ma.aquaops.assets.model.Pump;
import ma.aquaops.assets.repository.PumpRepository;
import ma.aquaops.assets.repository.ZoneRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/assets/pumps")
public class PumpController {
    private final PumpRepository repo;
    private final ZoneRepository zones;

    public PumpController(PumpRepository repo, ZoneRepository zones) {
        this.repo = repo; this.zones = zones;
    }

    private void checkZone(Long zoneId) {
        if (!zones.existsById(zoneId))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Zone inexistante");
    }

    @GetMapping
    public List<Pump> all() { return repo.findAll(); }

    @GetMapping("/{id}")
    public Pump one(@PathVariable Long id) {
        return repo.findById(id).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Pompe introuvable"));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Pump create(@Valid @RequestBody Pump x) {
        checkZone(x.zoneId); x.id = null; return repo.save(x);
    }

    @PutMapping("/{id}")
    public Pump update(@PathVariable Long id, @Valid @RequestBody Pump x) {
        one(id); checkZone(x.zoneId); x.id = id; return repo.save(x);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { one(id); repo.deleteById(id); }
}
