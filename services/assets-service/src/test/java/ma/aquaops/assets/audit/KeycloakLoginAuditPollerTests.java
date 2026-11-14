package ma.aquaops.assets.audit;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import ma.aquaops.assets.model.AuditLog;
import ma.aquaops.assets.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Audit des connexions : les evenements de Keycloak deviennent des lignes (qui, quand, quoi).
 * On appelle importEvents(...) directement : pas de Keycloak dans les tests.
 * Le delai initial tres long empeche le planificateur de lancer une vraie lecture pendant les tests.
 */
@SpringBootTest(properties = {
    "assets.audit.keycloak.enabled=true",
    "assets.audit.keycloak.initial-delay-ms=3600000",
    "assets.audit.keycloak.client-secret=test-only"
})
@Transactional
class KeycloakLoginAuditPollerTests {

    @Autowired KeycloakLoginAuditPoller poller;
    @Autowired AuditLogRepository repo;

    private static String event(long time, String type, String user, String userId, String extra) {
        return "{\"time\":" + time + ",\"type\":\"" + type + "\",\"clientId\":\"aquaops-frontend\","
            + "\"userId\":\"" + userId + "\",\"ipAddress\":\"10.0.0.5\","
            + "\"details\":{\"username\":\"" + user + "\"}" + extra + "}";
    }

    private List<AuditLog> rows(String userId) {
        return repo.findAll().stream()
            .filter(a -> KeycloakLoginAuditPoller.SOURCE.equals(a.source) && userId.equals(a.entityId)).toList();
    }

    @Test
    void uneConnexionCreeUneLigneQuiQuandQuoi() {
        String userId = UUID.randomUUID().toString();
        long time = Instant.parse("2026-12-01T09:30:00Z").toEpochMilli();

        int added = poller.importEvents("[" + event(time, "LOGIN", "superviseur1", userId, "") + "]", null);

        assertEquals(1, added);
        AuditLog a = rows(userId).get(0);
        assertEquals("superviseur1", a.actor);
        assertEquals("LOGIN", a.action);
        assertEquals("USER", a.entityType);
        assertEquals(Instant.ofEpochMilli(time), a.occurredAt);
        assertTrue(a.details.contains("10.0.0.5"));
    }

    @Test
    void echecDeConnexionGardeLErreur() {
        String userId = UUID.randomUUID().toString();
        long time = Instant.parse("2026-12-01T09:31:00Z").toEpochMilli();

        poller.importEvents("[" + event(time, "LOGIN_ERROR", "admin1", userId, ",\"error\":\"invalid_user_credentials\"") + "]", null);

        assertTrue(rows(userId).get(0).details.contains("invalid_user_credentials"));
    }

    @Test
    void memeEvenementLuDeuxFoisNeCreeQuUneLigne() {
        String userId = UUID.randomUUID().toString();
        String body = "[" + event(Instant.parse("2026-12-01T09:32:00Z").toEpochMilli(), "LOGOUT", "technicien1", userId, "") + "]";

        assertEquals(1, poller.importEvents(body, null));
        assertEquals(0, poller.importEvents(body, null));
        assertEquals(1, rows(userId).size());
    }

    @Test
    void evenementsPlusAnciensQueLeDernierCopieSontIgnores() {
        String userId = UUID.randomUUID().toString();
        long time = Instant.parse("2026-12-01T09:33:00Z").toEpochMilli();

        int added = poller.importEvents("[" + event(time, "LOGIN", "operateur1", userId, "") + "]", Instant.ofEpochMilli(time + 1000));

        assertEquals(0, added);
    }

    @Test
    void reponseIllisibleOuIncompleteNeCreeRien() {
        assertEquals(0, poller.importEvents("{\"error\":\"nope\"}", null));
        assertEquals(0, poller.importEvents("[{\"type\":\"LOGIN\"},{\"time\":5},{}]", null));
    }
}
