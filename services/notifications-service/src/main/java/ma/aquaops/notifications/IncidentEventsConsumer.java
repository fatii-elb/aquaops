package ma.aquaops.notifications;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

@Component
public class IncidentEventsConsumer {

    private final NotificationController notificationController;
    private final AuditLogRepository auditLogRepository;
    private final JsonMapper jsonMapper;

    public IncidentEventsConsumer(NotificationController notificationController, AuditLogRepository auditLogRepository, JsonMapper jsonMapper) {
        this.notificationController = notificationController;
        this.auditLogRepository = auditLogRepository;
        this.jsonMapper = jsonMapper;
    }

    @KafkaListener(topics = "incident-events", groupId = "notifications-service")
    public void handle(String message) {
        try {
            var node = jsonMapper.readTree(message);
            
            // 1. Vérification de la version du contrat
            var eventVersion = node.path("eventVersion").asString("");
            if (!"1".equals(eventVersion)) {
                System.out.println("Version d'événement inconnue ignorée : " + eventVersion);
                return; // On ignore le message
            }

            var eventType = node.path("eventType").asString("");
            var incidentId = node.path("incidentId").asInt(0);
            var zoneId = node.path("zoneId").asInt(0);
            var occurredAtStr = node.path("occurredAt").asString("");
            
            Instant occurredAt;
            try {
                occurredAt = Instant.parse(occurredAtStr);
            } catch (Exception e) {
                occurredAt = Instant.now();
            }

            // 2. Notification
            String notification = "Événement: " + eventType + " - Incident " + incidentId + " (Zone " + zoneId + ")";
            notificationController.sendNotification(notification);
            System.out.println("Notification envoyée: " + notification);

            // 3. Audit
            String what = eventType + " sur l'incident " + incidentId + " (zone " + zoneId + ")";
            AuditLog auditLog = new AuditLog("system", occurredAt, what);
            auditLogRepository.save(auditLog);
            System.out.println("Audit enregistré: " + what);

        } catch (Exception e) {
            System.err.println("Erreur lors du traitement du message: " + e.getMessage());
        }
    }
}