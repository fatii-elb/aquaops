package ma.aquaops.assets.web;

import jakarta.validation.Valid;
import java.util.List;
import ma.aquaops.assets.audit.AuditService;
import ma.aquaops.assets.model.Pump;
import ma.aquaops.assets.repository.PumpRepository;
import ma.aquaops.assets.repository.ZoneRepository;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/assets/pumps")
public class PumpController {
    private final PumpRepository repo;
    private final ZoneRepository zones;
    private final AuditService audit;

    public PumpController(PumpRepository repo, ZoneRepository zones, AuditService audit) {
        this.repo = repo; this.zones = zones; this.audit = audit;
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
    @Transactional
    public Pump create(@Valid @RequestBody Pump x) {
        checkZone(x.zoneId); x.id = null;
        Pump saved = repo.save(x);
        audit.record("CREATE", "PUMP", saved.id, "nom : " + saved.name + ", zone " + saved.zoneId);
        return saved;
    }

    @PutMapping("/{id}")
    @Transactional
    public Pump update(@PathVariable Long id, @Valid @RequestBody Pump x) {
        one(id); checkZone(x.zoneId); x.id = id;
        Pump saved = repo.save(x);
        audit.record("UPDATE", "PUMP", id, "nom : " + saved.name + ", zone " + saved.zoneId);
        return saved;
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(@PathVariable Long id) {
        Pump old = one(id);
        repo.deleteById(id);
        audit.record("DELETE", "PUMP", id, "nom : " + old.name + ", zone " + old.zoneId);
    }
}
