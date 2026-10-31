package ma.aquaops.notifications;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AuditLogTest {

    @Test
    void testAuditLogCreation() {
        // Préparer
        String who = "system";
        Instant when = Instant.now();
        String what = "INCIDENT_CREATED sur l'incident 99 (zone 5)";

        // Agir
        AuditLog auditLog = new AuditLog(who, when, what);

        // Vérifier
        assertNotNull(auditLog);
        assertEquals(who, auditLog.getWho());
        assertEquals(when, auditLog.getWhen());
        assertEquals(what, auditLog.getWhat());
    }
}