package ma.aquaops.assets.kafka;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import ma.aquaops.assets.audit.AuditService;
import ma.aquaops.assets.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Trace dans le journal d'audit les changements d'incident (SCRUM-46, FR-24).
 * Lit le topic incident-events (contrat contracts/events/incident-events.v1.json).
 *
 * Groupe de consommateurs propre a ce service : notifications-service lit le meme topic
 * avec son propre groupe, chacun recoit donc tous les messages.
 * Regles de contracts/events/README.md : champs inconnus ignores, version inconnue
 * journalisee puis ignoree. Un message illisible ou incomplet est ignore sans exception ;
 * en revanche une erreur d'ecriture en base remonte, pour que Kafka rejoue le message
 * (une ligne d'audit ne doit pas se perdre).
 */
@Component
public class IncidentEventsAuditConsumer {
    private static final Logger log = LoggerFactory.getLogger(IncidentEventsAuditConsumer.class);

    private final AuditService audit;
    private final AuditLogRepository repo;
    private final JsonMapper json;

    public IncidentEventsAuditConsumer(AuditService audit, AuditLogRepository repo, JsonMapper json) {
        this.audit = audit; this.repo = repo; this.json = json;
    }

    @KafkaListener(topics = "incident-events", groupId = "assets-service-audit",
                   autoStartup = "${assets.audit.kafka.enabled:true}")
    public void handle(String message) {
        // 1. Lecture du message. Un message illisible le restera a chaque nouvelle tentative :
        //    on le journalise et on l'ignore, sans exception.
        JsonNode node;
        try {
            node = json.readTree(message);
        } catch (Exception e) {
            log.error("incident-events : message illisible, ignore ({})", e.getMessage());
            return;
        }
        if (node == null) {
            log.warn("incident-events : message vide, ignore");
            return;
        }

        String version = node.path("eventVersion").asString("");
        if (!"1".equals(version)) {
            log.warn("incident-events : eventVersion inconnue '{}', message ignore", version);
            return;
        }
        String eventType = node.path("eventType").asString("");
        long incidentId = node.path("incidentId").asLong(0L);
        if (eventType.isBlank() || incidentId == 0L) {
            log.warn("incident-events : eventType ou incidentId manquant, message ignore");
            return;
        }

        Instant occurredAt;
        try {
            occurredAt = Instant.parse(node.path("occurredAt").asString(""));
        } catch (Exception e) {
            occurredAt = Instant.now();
        }

        // 2. Ecriture dans le journal. PAS de try/catch ici : si la base est indisponible,
        //    l'exception remonte, Kafka ne considere pas le message comme traite et le rejoue
        //    (voir KafkaRetryConfig). Avaler l'erreur ferait perdre la ligne d'audit pour toujours.
        String actor = node.path("actor").asString("");
        String source = AuditService.SOURCE_INCIDENTS;
        if (repo.existsBySourceAndActionAndEntityIdAndOccurredAt(
                source, eventType, String.valueOf(incidentId), occurredAt)) {
            return; // meme evenement rejoue par Kafka
        }
        audit.recordAs(occurredAt, actor, eventType, "INCIDENT", incidentId, describe(node), source);
    }

    /** Resume lisible : zone, statut, priorite, technicien. */
    static String describe(JsonNode node) {
        List<String> parts = new ArrayList<>();
        long zone = node.path("zoneId").asLong(0L);
        if (zone != 0L) parts.add("zone " + zone);
        String status = node.path("status").asString("");
        if (!status.isBlank()) parts.add("statut " + status);
        String priority = node.path("priority").asString("");
        if (!priority.isBlank()) parts.add("priorite " + priority);
        String assignedTo = node.path("assignedTo").asString("");
        if (!assignedTo.isBlank()) parts.add("assigne a " + assignedTo);
        return String.join(", ", parts);
    }
}
