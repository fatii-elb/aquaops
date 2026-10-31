package ma.aquaops.notifications;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class IncidentEventsConsumer {

    private final NotificationController notificationController;
    private final JsonMapper jsonMapper;

    public IncidentEventsConsumer(NotificationController notificationController, JsonMapper jsonMapper) {
        this.notificationController = notificationController;
        this.jsonMapper = jsonMapper;
    }

    @KafkaListener(topics = "incident-events", groupId = "notifications-service")
    public void handle(String message) {
        try {
            var node = jsonMapper.readTree(message);
            var eventType = node.path("eventType").asString("");
            var incidentId = node.path("incidentId").asInt(0);
            var zoneId = node.path("zoneId").asInt(0);

            String notification = "Événement: " + eventType + " - Incident " + incidentId + " (Zone " + zoneId + ")";
            notificationController.sendNotification(notification);
            System.out.println("Notification envoyée: " + notification);
        } catch (Exception e) {
            System.err.println("Erreur lors du traitement du message: " + e.getMessage());
        }
    }
}