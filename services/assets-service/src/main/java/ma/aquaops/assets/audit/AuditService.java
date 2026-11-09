package ma.aquaops.assets.audit;

import java.time.Instant;
import java.util.List;
import ma.aquaops.assets.model.AuditLog;
import ma.aquaops.assets.repository.AuditLogRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

/**
 * Ecrit dans le journal d'audit (SCRUM-46, FR-24).
 *
 * Les controleurs l'appellent dans la meme transaction que l'ecriture metier :
 * si l'audit echoue, l'action est annulee (aucune action sensible sans trace).
 * Pour tracer une autre action (par exemple les seuils, FR-13), appeler record(...).
 */
@Service
public class AuditService {

    public static final String SOURCE_ASSETS = "assets-service";
    public static final String SOURCE_INCIDENTS = "incident-events";

    /** Du plus puissant au moins puissant : on garde le premier role present. */
    private static final List<String> ROLES = List.of("administrateur", "superviseur", "technicien", "operateur");

    private final AuditLogRepository repo;

    public AuditService(AuditLogRepository repo) { this.repo = repo; }

    /** Trace une action faite par l'utilisateur connecte (lu dans le jeton). */
    public AuditLog record(String action, String entityType, Object entityId, String details) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return save(Instant.now(), actorOf(auth), roleOf(auth), action, entityType, entityId, details, SOURCE_ASSETS);
    }

    /** Trace une action dont on connait deja l'auteur et la date (evenements Kafka). */
    public AuditLog recordAs(Instant occurredAt, String actor, String action, String entityType,
                             Object entityId, String details, String source) {
        return save(occurredAt, actor, null, action, entityType, entityId, details, source);
    }

    private AuditLog save(Instant occurredAt, String actor, String role, String action,
                          String entityType, Object entityId, String details, String source) {
        AuditLog a = new AuditLog();
        a.occurredAt = occurredAt;
        a.actor = (actor == null || actor.isBlank()) ? "system" : actor;
        a.actorRole = role;
        a.action = action;
        a.entityType = entityType;
        a.entityId = entityId == null ? null : String.valueOf(entityId);
        a.details = truncate(details, 1000);
        a.source = source;
        return repo.save(a);
    }

    static String actorOf(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) return "system";
        if (auth instanceof JwtAuthenticationToken token) {
            String name = token.getToken().getClaimAsString("preferred_username");
            if (name != null && !name.isBlank()) return name;
        }
        String name = auth.getName();
        return (name == null || name.isBlank() || "anonymousUser".equals(name)) ? "system" : name;
    }

    static String roleOf(Authentication auth) {
        if (auth == null) return null;
        List<String> mine = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
        return ROLES.stream().filter(r -> mine.contains("ROLE_" + r)).findFirst().orElse(null);
    }

    private static String truncate(String s, int max) {
        return (s == null || s.length() <= max) ? s : s.substring(0, max);
    }
}
