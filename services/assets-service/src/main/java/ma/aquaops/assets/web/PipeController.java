package ma.aquaops.assets.web;

import jakarta.validation.Valid;
import java.util.List;
import ma.aquaops.assets.audit.AuditService;
import ma.aquaops.assets.model.Pipe;
import ma.aquaops.assets.repository.PipeRepository;
import ma.aquaops.assets.repository.ZoneRepository;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/assets/pipes")
public class PipeController {
    private final PipeRepository repo;
    private final ZoneRepository zones;
    private final AuditService audit;

    public PipeController(PipeRepository repo, ZoneRepository zones, AuditService audit) {
        this.repo = repo; this.zones = zones; this.audit = audit;
    }

    private void checkZone(Long zoneId) {
        if (!zones.existsById(zoneId))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Zone inexistante");
    }

    @GetMapping
    public List<Pipe> all() { return repo.findAll(); }

    @GetMapping("/{id}")
    public Pipe one(@PathVariable Long id) {
        return repo.findById(id).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Conduite introuvable"));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Pipe create(@Valid @RequestBody Pipe x) {
        checkZone(x.zoneId); x.id = null;
        Pipe saved = repo.save(x);
        audit.record("CREATE", "PIPE", saved.id, "nom : " + saved.name + ", zone " + saved.zoneId);
        return saved;
    }

    @PutMapping("/{id}")
    @Transactional
    public Pipe update(@PathVariable Long id, @Valid @RequestBody Pipe x) {
        one(id); checkZone(x.zoneId); x.id = id;
        Pipe saved = repo.save(x);
        audit.record("UPDATE", "PIPE", id, "nom : " + saved.name + ", zone " + saved.zoneId);
        return saved;
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(@PathVariable Long id) {
        Pipe old = one(id);
        repo.deleteById(id);
        audit.record("DELETE", "PIPE", id, "nom : " + old.name + ", zone " + old.zoneId);
    }
}
