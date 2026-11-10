package ma.aquaops.notifications;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Duration;

import static org.awaitility.Awaitility.await;

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

    @Autowired
    private KafkaListenerEndpointRegistry registry;

    @BeforeEach
    void waitForConsumer() {
        // On n'envoie rien tant que le consommateur n'est pas vraiment prêt
        for (MessageListenerContainer container : registry.getListenerContainers()) {
            ContainerTestUtils.waitForAssignment(container, 1);
        }
    }

    @Test
    void testNotificationReceived() {
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

        // Réessaie toutes les 200 ms, jusqu'à 30 s maximum (CI lente)
        await().atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(200))
                .until(() -> notificationService.getNotifications().stream()
                        .anyMatch(n -> n.contains("INCIDENT_CREATED") && n.contains("42")));
    }
}