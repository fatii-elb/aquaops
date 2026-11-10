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
class AuditLogTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void testAuditLogSaved() throws Exception {
        String message = """
                {
                  "eventVersion": "1",
                  "eventType": "INCIDENT_CREATED",
                  "incidentId": 99,
                  "zoneId": 5,
                  "status": "OPEN",
                  "occurredAt": "2026-10-12T21:30:05Z"
                }
                """;

        kafkaTemplate.send("incident-events", message);

        // Attendre plus longtemps (jusqu'à 10 secondes)
        boolean found = false;
        for (int i = 0; i < 20; i++) {
            Thread.sleep(500);
            found = auditLogRepository.findAll().stream()
                    .anyMatch(a -> a.getWhat().contains("99") && a.getWhat().contains("INCIDENT_CREATED"));
            if (found) {
                break;
            }
        }

        assertTrue(found, "L'audit aurait dû être enregistré en base");
    }
}