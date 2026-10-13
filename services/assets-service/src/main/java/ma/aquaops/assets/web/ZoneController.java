package ma.aquaops.assets.web;

import jakarta.validation.Valid;
import java.util.List;
import ma.aquaops.assets.model.Zone;
import ma.aquaops.assets.repository.ZoneRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/assets/zones")
public class ZoneController {
    private final ZoneRepository repo;

    public ZoneController(ZoneRepository repo) { this.repo = repo; }

    @GetMapping
    public List<Zone> all() { return repo.findAll(); }

    @GetMapping("/{id}")
    public Zone one(@PathVariable Long id) {
        return repo.findById(id).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Zone introuvable"));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Zone create(@Valid @RequestBody Zone z) { z.id = null; return repo.save(z); }

    @PutMapping("/{id}")
    public Zone update(@PathVariable Long id, @Valid @RequestBody Zone z) {
        one(id); z.id = id; return repo.save(z);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { one(id); repo.deleteById(id); }
}
