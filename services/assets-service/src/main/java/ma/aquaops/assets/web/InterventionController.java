package ma.aquaops.assets.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import ma.aquaops.assets.audit.AuditService;
import ma.aquaops.assets.model.Intervention;
import ma.aquaops.assets.model.InterventionStatus;
import ma.aquaops.assets.repository.InterventionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/assets/interventions")
public class InterventionController {
    private final InterventionRepository repo;
    private final AuditService audit;

    public InterventionController(InterventionRepository repo, AuditService audit) {
        this.repo = repo; this.audit = audit;
    }

    /** Corps de la requete d'affectation : equipe obligatoire, technicien facultatif. */
    public record AssignRequest(@NotBlank String team, String assignedTo) {}

    /** Corps de la requete de changement de statut. */
    public record StatusRequest(@NotNull InterventionStatus status) {}

    @GetMapping
    public List<Intervention> all(@RequestParam(name = "incidentId", required = false) Long incidentId) {
        return incidentId == null ? repo.findAll() : repo.findByIncidentId(incidentId);
    }

    @GetMapping("/{id}")
    public Intervention one(@PathVariable Long id) {
        return repo.findById(id).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Intervention introuvable"));
    }

    /** Cree une intervention Planifiee, liee a un incident, sans equipe ni technicien. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Intervention create(@Valid @RequestBody Intervention x) {
        x.id = null;
        x.team = null;
        x.assignedTo = null;
        x.status = InterventionStatus.PLANNED;
        x.createdAt = Instant.now();
        Intervention saved = repo.save(x);
        audit.record("CREATE", "INTERVENTION", saved.id, "incident " + saved.incidentId);
        return saved;
    }

    /** Affecte (ou reaffecte) l'intervention a une equipe et, si fourni, a un technicien. */
    @PutMapping("/{id}/assign")
    @Transactional
    public Intervention assign(@PathVariable Long id, @Valid @RequestBody AssignRequest r) {
        Intervention i = one(id);
        if (i.status == InterventionStatus.DONE)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Intervention deja terminee");
        i.team = r.team().trim();
        i.assignedTo = (r.assignedTo() == null || r.assignedTo().isBlank()) ? null : r.assignedTo().trim();
        Intervention saved = repo.save(i);
        audit.record("ASSIGN", "INTERVENTION", id,
            "equipe " + saved.team + (saved.assignedTo == null ? "" : ", technicien " + saved.assignedTo));
        return saved;
    }

    /**
     * Change le statut (SCRUM-42). Seul le technicien assigne, un superviseur ou un
     * administrateur peut le faire (sinon 403). Transition invalide : 409.
     */
    @PutMapping("/{id}/status")
    @Transactional
    public Intervention changeStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest r,
                                     Authentication auth) {
        Intervention i = one(id);
        if (!canChangeStatus(auth, i))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Seul le technicien assigne ou un superviseur peut changer le statut");
        if (!i.status.canMoveTo(r.status()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Transition interdite : " + i.status + " -> " + r.status());
        InterventionStatus before = i.status;
        i.status = r.status();
        Intervention saved = repo.save(i);
        audit.record("STATUS_CHANGE", "INTERVENTION", id, before + " -> " + saved.status);
        return saved;
    }

    private boolean canChangeStatus(Authentication auth, Intervention i) {
        if (auth == null) return false;
        boolean superviseur = auth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch(a -> a.equals("ROLE_superviseur") || a.equals("ROLE_administrateur"));
        if (superviseur) return true;
        if (i.assignedTo == null || !(auth instanceof JwtAuthenticationToken token)) return false;
        return i.assignedTo.equals(token.getToken().getClaimAsString("preferred_username"));
    }
}
