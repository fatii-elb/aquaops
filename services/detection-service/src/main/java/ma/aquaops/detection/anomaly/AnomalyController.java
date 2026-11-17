package ma.aquaops.detection.anomaly;

import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Consultation des anomalies detectees (BE-1). Lecture seule, ouverte aux quatre roles (voir SecurityConfig).
 * Filtres : zone, type, statut, periode (sur la date de detection). Pagination et tri.
 */
@RestController
@RequestMapping("/api/detection/anomalies")
public class AnomalyController {

    private static final int MAX_SIZE = 200;
    /** Colonnes triables (liste blanche : jamais un nom de champ saisi par l'appelant). */
    private static final Set<String> SORTABLE = Set.of("detectedAt", "zoneId", "type", "status");

    /** Anomalie telle que la voit l'ecran. La gravite est calculee a partir du type. */
    public record AnomalyView(String id, String type, Integer zoneId, Integer sensorId, String severity,
                              String status, Instant measuredAt, Instant detectedAt, Instant resolvedAt) {
        static AnomalyView of(StoredAnomaly a) {
            return new AnomalyView(a.anomalyId, a.type, a.zoneId, a.sensorId, Severity.forType(a.type).name(),
                    a.status.name(), a.measuredAt, a.detectedAt, a.resolvedAt);
        }
    }

    /** Page de resultats : meme format que le journal d'audit des actifs. */
    public record AnomalyPage(List<AnomalyView> content, int page, int size, long totalElements, int totalPages) {}

    private final StoredAnomalyRepository repository;

    public AnomalyController(StoredAnomalyRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public AnomalyPage search(
            @RequestParam(required = false) Integer zoneId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "detectedAt,desc") String sort) {

        if (page < 0 || size < 1 || size > MAX_SIZE) {
            throw bad("page >= 0 et 1 <= size <= " + MAX_SIZE);
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw bad("from doit preceder to");
        }
        AnomalyStatus wantedStatus = parseStatus(status);
        Sort order = parseSort(sort);

        Specification<StoredAnomaly> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (zoneId != null) {
                p.add(cb.equal(root.get("zoneId"), zoneId));
            }
            if (notBlank(type)) {
                p.add(cb.equal(root.get("type"), type.trim()));
            }
            if (wantedStatus != null) {
                p.add(cb.equal(root.get("status"), wantedStatus));
            }
            if (from != null) {
                p.add(cb.greaterThanOrEqualTo(root.<Instant>get("detectedAt"), from));
            }
            if (to != null) {
                p.add(cb.lessThanOrEqualTo(root.<Instant>get("detectedAt"), to));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };

        Page<StoredAnomaly> result = repository.findAll(spec, PageRequest.of(page, size, order));
        return new AnomalyPage(result.getContent().stream().map(AnomalyView::of).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    private static AnomalyStatus parseStatus(String status) {
        if (!notBlank(status)) {
            return null;
        }
        try {
            return AnomalyStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw bad("status doit valoir OPEN ou RESOLVED");
        }
    }

    /** "champ,asc" ou "champ,desc" ; l'identifiant departage deux lignes de meme valeur. */
    private static Sort parseSort(String sort) {
        String[] parts = sort.split(",");
        String field = parts[0].trim();
        String direction = parts.length > 1 ? parts[1].trim().toLowerCase(Locale.ROOT) : "asc";
        if (parts.length > 2 || !SORTABLE.contains(field) || !(direction.equals("asc") || direction.equals("desc"))) {
            throw bad("sort doit etre de la forme champ,asc|desc avec champ parmi " + SORTABLE);
        }
        Sort.Direction dir = direction.equals("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(dir, field).and(Sort.by(Sort.Direction.DESC, "id"));
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
