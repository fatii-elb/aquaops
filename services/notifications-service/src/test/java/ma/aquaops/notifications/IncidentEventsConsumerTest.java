package ma.aquaops.notifications;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = "incident-events", bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@DirtiesContext
class IncidentEventsConsumerTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private NotificationService notificationService;

    @Test
    void testNotificationReceived() throws Exception {
        String message = """
                {
                  "eventVersion": "1",
                  "eventType": "INCIDENT_CREATED",
                  "incidentId": 42,
                  "zoneId": 3,
                  "status": "OPEN",
                  "occurredAt": "2026-10-12T21:30:05Z"
                }
                """;

        kafkaTemplate.send("incident-events", message);

        // Attendre un peu que le message soit consommé
        Thread.sleep(2000);

        assertTrue(notificationService.getNotifications().stream()
                .anyMatch(n -> n.contains("INCIDENT_CREATED") && n.contains("42")),
                "La notification aurait dû être reçue");
    }
}