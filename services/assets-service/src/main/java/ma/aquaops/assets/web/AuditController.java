package ma.aquaops.assets.web;

import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import ma.aquaops.assets.model.AuditLog;
import ma.aquaops.assets.repository.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Consultation du journal d'audit (SCRUM-46, FR-24, FR-5 de l'ecran).
 * Lecture seule. Reserve aux roles superviseur et administrateur (voir SecurityConfig).
 */
@RestController
@RequestMapping("/api/assets/audit")
public class AuditController {
    private static final int MAX_SIZE = 200;

    private final AuditLogRepository repo;

    public AuditController(AuditLogRepository repo) { this.repo = repo; }

    /** Page de resultats : format stable, independant de la serialisation interne de Spring Data. */
    public record AuditPage(List<AuditLog> content, int page, int size, long totalElements, int totalPages) {}

    @GetMapping
    public AuditPage search(
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > MAX_SIZE)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "page >= 0 et 1 <= size <= " + MAX_SIZE);
        if (from != null && to != null && from.isAfter(to))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from doit preceder to");

        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (notBlank(actor))
                p.add(cb.like(cb.lower(root.get("actor")), "%" + escape(actor.trim().toLowerCase()) + "%", '\\'));
            if (notBlank(action))
                p.add(cb.equal(root.get("action"), action.trim()));
            if (notBlank(entityType))
                p.add(cb.equal(root.get("entityType"), entityType.trim()));
            if (from != null)
                p.add(cb.greaterThanOrEqualTo(root.<Instant>get("occurredAt"), from));
            if (to != null)
                p.add(cb.lessThanOrEqualTo(root.<Instant>get("occurredAt"), to));
            return cb.and(p.toArray(new Predicate[0]));
        };

        // Les plus recentes d'abord ; l'id departage deux lignes de meme date.
        Page<AuditLog> r = repo.findAll(spec,
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt", "id")));
        return new AuditPage(r.getContent(), r.getNumber(), r.getSize(), r.getTotalElements(), r.getTotalPages());
    }

    /** Types d'action presents dans le journal : alimente le filtre de l'ecran. */
    @GetMapping("/actions")
    public List<String> actions() { return repo.findDistinctActions(); }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
