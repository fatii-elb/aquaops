package ma.aquaops.assets.audit;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import ma.aquaops.assets.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Audit des connexions (FR-24, reste de SCRUM-46) : copie les evenements LOGIN, LOGIN_ERROR et LOGOUT
 * de Keycloak dans audit_log, avec la source "keycloak".
 *
 * Keycloak n'envoie pas ses evenements a Kafka sans extension : on les lit par son API d'administration,
 * toutes les 30 s, avec le compte de service "aquaops-audit" (role view-events seulement, voir le realm).
 * Reprise : on repart de la date de la derniere ligne deja copiee ; un evenement lu deux fois
 * n'est enregistre qu'une fois.
 * Desactive par defaut : assets.audit.keycloak.enabled=true (variable AUDIT_KEYCLOAK_ENABLED).
 * Une panne de Keycloak est journalisee et reessayee au tour suivant : elle ne touche pas le reste du service.
 */
@Component
@ConditionalOnProperty(name = "assets.audit.keycloak.enabled", havingValue = "true")
public class KeycloakLoginAuditPoller {
    private static final Logger log = LoggerFactory.getLogger(KeycloakLoginAuditPoller.class);

    public static final String SOURCE = "keycloak";
    private static final int MAX_EVENTS = 500;

    private final AuditService audit;
    private final AuditLogRepository repo;
    private final JsonMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private final String baseUrl;
    private final String realm;
    private final String clientId;
    private final String clientSecret;

    public KeycloakLoginAuditPoller(AuditService audit, AuditLogRepository repo, JsonMapper json,
            @Value("${assets.audit.keycloak.base-url}") String baseUrl,
            @Value("${assets.audit.keycloak.realm}") String realm,
            @Value("${assets.audit.keycloak.client-id}") String clientId,
            @Value("${assets.audit.keycloak.client-secret:}") String clientSecret) {
        this.audit = audit; this.repo = repo; this.json = json;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.realm = realm; this.clientId = clientId; this.clientSecret = clientSecret;
    }

    @Scheduled(fixedDelayString = "${assets.audit.keycloak.poll-delay-ms:30000}",
               initialDelayString = "${assets.audit.keycloak.initial-delay-ms:10000}")
    public void poll() {
        if (clientSecret.isBlank()) {
            log.warn("audit des connexions : KEYCLOAK_AUDIT_CLIENT_SECRET est vide, rien n'est lu");
            return;
        }
        try {
            Instant last = repo.findLastOccurredAtBySource(SOURCE);
            int added = importEvents(fetchEvents(fetchToken(), last), last);
            if (added > 0) log.info("audit des connexions : {} evenement(s) copie(s)", added);
        } catch (Exception e) {
            log.warn("audit des connexions : lecture de Keycloak impossible, nouvel essai au prochain tour ({})",
                e.getMessage());
        }
    }

    /**
     * Enregistre les evenements d'une reponse de GET /admin/realms/{realm}/events.
     * Ignore les evenements deja copies et ceux qui sont illisibles. Retourne le nombre de lignes creees.
     */
    int importEvents(String body, Instant last) {
        JsonNode events = json.readTree(body);
        if (events == null || !events.isArray()) return 0;
        int added = 0;
        for (JsonNode e : events) {
            long time = e.path("time").asLong(0L);
            String type = e.path("type").asString("");
            if (time <= 0L || type.isBlank()) continue;
            Instant occurredAt = Instant.ofEpochMilli(time);
            if (last != null && occurredAt.isBefore(last)) continue;

            String userId = e.path("userId").asString("");
            String actor = e.path("details").path("username").asString("");
            if (actor.isBlank()) actor = userId.isBlank() ? "inconnu" : userId;
            String entityId = userId.isBlank() ? actor : userId;
            if (repo.existsBySourceAndActionAndEntityIdAndOccurredAt(SOURCE, type, entityId, occurredAt)) continue;

            audit.recordAs(occurredAt, actor, type, "USER", entityId, describe(e), SOURCE);
            added++;
        }
        return added;
    }

    /** Resume lisible : client, adresse IP, erreur. Jamais de jeton ni de mot de passe. */
    static String describe(JsonNode e) {
        StringBuilder sb = new StringBuilder();
        String client = e.path("clientId").asString("");
        if (!client.isBlank()) sb.append("client ").append(client);
        String ip = e.path("ipAddress").asString("");
        if (!ip.isBlank()) sb.append(sb.length() > 0 ? ", " : "").append("ip ").append(ip);
        String error = e.path("error").asString("");
        if (!error.isBlank()) sb.append(sb.length() > 0 ? ", " : "").append("erreur ").append(error);
        return sb.toString();
    }

    private String fetchToken() throws Exception {
        String form = "grant_type=client_credentials"
            + "&client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
            + "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8);
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/realms/" + realm + "/protocol/openid-connect/token"))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form)).build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) throw new IllegalStateException("jeton refuse (HTTP " + res.statusCode() + ")");
        String token = json.readTree(res.body()).path("access_token").asString("");
        if (token.isBlank()) throw new IllegalStateException("reponse sans access_token");
        return token;
    }

    private String fetchEvents(String token, Instant last) throws Exception {
        // dateFrom : jour entier (AAAA-MM-JJ) ; importEvents() filtre ensuite a la milliseconde.
        LocalDate from = (last != null ? last : Instant.now().minus(Duration.ofDays(7))).atZone(ZoneOffset.UTC).toLocalDate();
        String url = baseUrl + "/admin/realms/" + realm + "/events?type=LOGIN&type=LOGIN_ERROR&type=LOGOUT"
            + "&dateFrom=" + from + "&max=" + MAX_EVENTS;
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
            .header("Authorization", "Bearer " + token).GET().build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) throw new IllegalStateException("evenements refuses (HTTP " + res.statusCode() + ")");
        return res.body();
    }
}
