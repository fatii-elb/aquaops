package ma.aquaops.assets.audit;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import ma.aquaops.assets.kafka.IncidentEventsAuditConsumer;
import ma.aquaops.assets.model.AuditLog;
import ma.aquaops.assets.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Le consommateur de incident-events trace les changements d'incident (FR-24).
 * On appelle handle(...) directement : pas de broker Kafka dans les tests.
 * Regles du contrat (contracts/events/README.md) : champs inconnus ignores,
 * version inconnue ignoree, message illisible ignore sans exception. Une erreur d'ecriture
 * en base, elle, remonte (pour que Kafka rejoue le message).
 */
@SpringBootTest
@Transactional
class IncidentEventsAuditConsumerTests {

    @Autowired IncidentEventsAuditConsumer consumer;
    @Autowired AuditLogRepository repo;

    // Identifiant d'incident peu probable en base : les tests ne dependent pas des lignes existantes.
    private static long newIncidentId() { return 9_000_000_000L + (System.nanoTime() % 1_000_000_000L); }

    private List<AuditLog> rows(long incidentId) {
        return repo.findAll().stream().filter(a -> String.valueOf(incidentId).equals(a.entityId)
            && "INCIDENT".equals(a.entityType)).toList();
    }

    private static String event(String type, long id, String extra) {
        return "{\"eventVersion\":\"1\",\"eventType\":\"" + type + "\",\"incidentId\":" + id
            + ",\"zoneId\":3,\"status\":\"CONFIRMED\",\"occurredAt\":\"2026-11-23T10:00:00Z\"" + extra + "}";
    }

    @Test
    void evenementValideCreeUneLigneQuiQuandQuoi() {
        long id = newIncidentId();
        consumer.handle(event("INCIDENT_CONFIRMED", id, ",\"actor\":\"test-superviseur\",\"priority\":\"HIGH\""));

        List<AuditLog> r = rows(id);
        assertEquals(1, r.size());
        AuditLog a = r.get(0);
        assertEquals("test-superviseur", a.actor);
        assertEquals("INCIDENT_CONFIRMED", a.action);
        assertEquals(Instant.parse("2026-11-23T10:00:00Z"), a.occurredAt);
        assertEquals("incident-events", a.source);
        assertEquals("zone 3, statut CONFIRMED, priorite HIGH", a.details);
    }

    @Test
    void sansActeurLAuteurEstSystem() {
        long id = newIncidentId();
        consumer.handle(event("INCIDENT_CREATED", id, ""));
        assertEquals("system", rows(id).get(0).actor);
    }

    @Test
    void technicienAssigneApparaitDansLeDetail() {
        long id = newIncidentId();
        consumer.handle(event("INCIDENT_ASSIGNED", id, ",\"assignedTo\":\"test-technicien\""));
        assertTrue(rows(id).get(0).details.contains("assigne a test-technicien"));
    }

    @Test
    void memeEvenementRejoueNeCreePasUneDeuxiemeLigne() {
        long id = newIncidentId();
        String msg = event("INCIDENT_RESOLVED", id, "");
        consumer.handle(msg);
        consumer.handle(msg);
        assertEquals(1, rows(id).size());
    }

    @Test
    void deuxEvenementsDifferentsSontTousLesDeuxTraces() {
        long id = newIncidentId();
        consumer.handle(event("INCIDENT_CREATED", id, ""));
        consumer.handle(event("INCIDENT_CLOSED", id, ""));
        assertEquals(2, rows(id).size());
    }

    @Test
    void champInconnuEstIgnore() {
        long id = newIncidentId();
        consumer.handle(event("INCIDENT_CREATED", id, ",\"champFutur\":{\"a\":1}"));
        assertEquals(1, rows(id).size());
    }

    @Test
    void valeurDEnumerationInconnueEstTraceeSansPlanter() {
        long id = newIncidentId();
        consumer.handle(event("INCIDENT_REOUVERT", id, ""));
        assertEquals("INCIDENT_REOUVERT", rows(id).get(0).action);
    }

    @Test
    void versionInconnueEstIgnoree() {
        long id = newIncidentId();
        consumer.handle(event("INCIDENT_CREATED", id, "").replace("\"eventVersion\":\"1\"", "\"eventVersion\":\"2\""));
        assertTrue(rows(id).isEmpty());
    }

    @Test
    void messageIllisibleOuIncompletNeLevePasDException() {
        assertDoesNotThrow(() -> consumer.handle("pas du json"));
        assertDoesNotThrow(() -> consumer.handle("{}"));
        assertDoesNotThrow(() -> consumer.handle("{\"eventVersion\":\"1\"}"));
        assertDoesNotThrow(() -> consumer.handle(null));
    }

    /**
     * Une vraie erreur de base (ici : eventType plus long que la colonne action, 255 caracteres)
     * doit REMONTER, pour que Kafka rejoue le message au lieu de perdre la ligne d'audit.
     */
    @Test
    void uneErreurDEcritureEnBaseRemonteAuLieuDEtreAvalee() {
        long id = newIncidentId();
        String tropLong = "X".repeat(300);
        assertThrows(Exception.class, () -> consumer.handle(event(tropLong, id, "")));
    }

    @Test
    void dateIllisibleOuAbsenteEstRemplaceeParLaDateDeReception() {
        long id = newIncidentId();
        Instant before = Instant.now().minusSeconds(1);
        consumer.handle("{\"eventVersion\":\"1\",\"eventType\":\"INCIDENT_CREATED\",\"incidentId\":" + id
            + ",\"zoneId\":3,\"status\":\"OPEN\",\"occurredAt\":\"hier\"}");
        assertTrue(rows(id).get(0).occurredAt.isAfter(before));
    }
}
