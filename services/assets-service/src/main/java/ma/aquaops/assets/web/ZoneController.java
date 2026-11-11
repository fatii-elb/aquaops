package ma.aquaops.assets.web;

import jakarta.validation.Valid;
import java.util.List;
import ma.aquaops.assets.audit.AuditService;
import ma.aquaops.assets.model.Zone;
import ma.aquaops.assets.repository.ZoneRepository;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/assets/zones")
public class ZoneController {
    private final ZoneRepository repo;
    private final AuditService audit;

    public ZoneController(ZoneRepository repo, AuditService audit) { this.repo = repo; this.audit = audit; }

    @GetMapping
    public List<Zone> all() { return repo.findAll(); }

    @GetMapping("/{id}")
    public Zone one(@PathVariable Long id) {
        return repo.findById(id).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Zone introuvable"));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Zone create(@Valid @RequestBody Zone z) {
        z.id = null;
        Zone saved = repo.save(z);
        audit.record("CREATE", "ZONE", saved.id, "nom : " + saved.name);
        return saved;
    }

    @PutMapping("/{id}")
    @Transactional
    public Zone update(@PathVariable Long id, @Valid @RequestBody Zone z) {
        one(id);
        z.id = id;
        Zone saved = repo.save(z);
        audit.record("UPDATE", "ZONE", id, "nom : " + saved.name);
        return saved;
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(@PathVariable Long id) {
        Zone old = one(id);
        repo.deleteById(id);
        audit.record("DELETE", "ZONE", id, "nom : " + old.name);
    }
}
