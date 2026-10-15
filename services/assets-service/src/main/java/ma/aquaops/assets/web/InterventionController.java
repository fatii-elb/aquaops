package ma.aquaops.assets.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import ma.aquaops.assets.model.Intervention;
import ma.aquaops.assets.model.InterventionStatus;
import ma.aquaops.assets.repository.InterventionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/assets/interventions")
public class InterventionController {
    private final InterventionRepository repo;

    public InterventionController(InterventionRepository repo) { this.repo = repo; }

    /** Corps de la requete d'affectation. */
    public record AssignRequest(@NotBlank String team) {}

    @GetMapping
    public List<Intervention> all(@RequestParam(name = "incidentId", required = false) Long incidentId) {
        return incidentId == null ? repo.findAll() : repo.findByIncidentId(incidentId);
    }

    @GetMapping("/{id}")
    public Intervention one(@PathVariable Long id) {
        return repo.findById(id).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Intervention introuvable"));
    }

    /** Cree une intervention Planifiee, liee a un incident, sans equipe. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Intervention create(@Valid @RequestBody Intervention x) {
        x.id = null;
        x.team = null;
        x.status = InterventionStatus.PLANNED;
        x.createdAt = Instant.now();
        return repo.save(x);
    }

    /** Affecte (ou reaffecte) l'intervention a une equipe. */
    @PutMapping("/{id}/assign")
    public Intervention assign(@PathVariable Long id, @Valid @RequestBody AssignRequest r) {
        Intervention i = one(id);
        if (i.status == InterventionStatus.DONE)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Intervention deja terminee");
        i.team = r.team().trim();
        return repo.save(i);
    }
}
