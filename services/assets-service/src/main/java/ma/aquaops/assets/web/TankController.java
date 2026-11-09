package ma.aquaops.assets.web;

import jakarta.validation.Valid;
import java.util.List;
import ma.aquaops.assets.audit.AuditService;
import ma.aquaops.assets.model.Tank;
import ma.aquaops.assets.repository.TankRepository;
import ma.aquaops.assets.repository.ZoneRepository;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/assets/tanks")
public class TankController {
    private final TankRepository repo;
    private final ZoneRepository zones;
    private final AuditService audit;

    public TankController(TankRepository repo, ZoneRepository zones, AuditService audit) {
        this.repo = repo; this.zones = zones; this.audit = audit;
    }

    private void checkZone(Long zoneId) {
        if (!zones.existsById(zoneId))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Zone inexistante");
    }

    @GetMapping
    public List<Tank> all() { return repo.findAll(); }

    @GetMapping("/{id}")
    public Tank one(@PathVariable Long id) {
        return repo.findById(id).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Reservoir introuvable"));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Tank create(@Valid @RequestBody Tank x) {
        checkZone(x.zoneId); x.id = null;
        Tank saved = repo.save(x);
        audit.record("CREATE", "TANK", saved.id, "nom : " + saved.name + ", zone " + saved.zoneId);
        return saved;
    }

    @PutMapping("/{id}")
    @Transactional
    public Tank update(@PathVariable Long id, @Valid @RequestBody Tank x) {
        one(id); checkZone(x.zoneId); x.id = id;
        Tank saved = repo.save(x);
        audit.record("UPDATE", "TANK", id, "nom : " + saved.name + ", zone " + saved.zoneId);
        return saved;
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(@PathVariable Long id) {
        Tank old = one(id);
        repo.deleteById(id);
        audit.record("DELETE", "TANK", id, "nom : " + old.name + ", zone " + old.zoneId);
    }
}
