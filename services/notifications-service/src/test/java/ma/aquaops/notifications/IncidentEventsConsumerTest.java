package ma.aquaops.notifications;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;

import static org.junit.jupiter.api.Assertions.assertTrue;

// earliest pour CE test seulement : le consommateur lit le message meme s'il a ete
// publie avant l'attribution de sa partition. Sans danger ici : @EmbeddedKafka et
// @DirtiesContext donnent un broker neuf, le topic ne contient que ce message.
@SpringBootTest(properties = "spring.kafka.consumer.auto-offset-reset=earliest")
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

        // Attendre la notification, 15 s au plus : rejoindre le groupe Kafka
        // peut prendre plusieurs secondes sur une machine lente (CI).
        long limite = System.currentTimeMillis() + 15_000;
        while (!notificationRecue() && System.currentTimeMillis() < limite) {
            Thread.sleep(100);
        }

        assertTrue(notificationRecue(), "La notification aurait dû être reçue");
    }

    private boolean notificationRecue() {
        return notificationService.getNotifications().stream()
                .anyMatch(n -> n.contains("INCIDENT_CREATED") && n.contains("42"));
    }
}