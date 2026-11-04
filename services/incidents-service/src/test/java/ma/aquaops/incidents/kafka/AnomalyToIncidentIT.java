package ma.aquaops.incidents.kafka;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ma.aquaops.incidents.incident.Incident;
import ma.aquaops.incidents.incident.IncidentRepository;
import ma.aquaops.incidents.incident.Priority;
import ma.aquaops.incidents.state.IncidentStatus;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Test de bout en bout (SCRUM-37 : incident cree en 5 s au plus).
 * Il faut PostgreSQL (base aquaops_incidents) et Kafka demarres :  AQUAOPS_IT=1 ./mvnw -o test -Dtest=AnomalyToIncidentIT
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "AQUAOPS_IT", matches = "1")
class AnomalyToIncidentIT {

    @Autowired
    KafkaTemplate<String, String> kafka;

    @Autowired
    IncidentRepository repository;

    private static String anomaly(String id, String type) {
        return "{\"eventVersion\":1,\"anomalyId\":\"" + id + "\",\"type\":\"" + type + "\",\"zoneId\":3,"
                + "\"measuredAt\":\"" + java.time.Instant.now() + "\",\"detectedAt\":\"" + java.time.Instant.now() + "\"}";
    }

    private Optional<Incident> waitFor(String anomalyId, Duration max) throws InterruptedException {
        long end = System.nanoTime() + max.toNanos();
        while (System.nanoTime() < end) {
            Optional<Incident> found = repository.findByAnomalyId(anomalyId);
            if (found.isPresent()) {
                return found;
            }
            Thread.sleep(100);
        }
        return Optional.empty();
    }

    @Test
    void anomalyBecomesAnIncidentWithinFiveSeconds() throws Exception {
        String id = "it-" + UUID.randomUUID();
        long start = System.nanoTime();
        kafka.send("anomaly-detected", id, anomaly(id, "LEAK_SUSPECTED")).get();
        Incident incident = waitFor(id, Duration.ofSeconds(5)).orElseThrow(
                () -> new AssertionError("aucun incident cree en 5 s"));
        long ms = (System.nanoTime() - start) / 1_000_000;
        System.out.println("Incident cree en " + ms + " ms");
        assertEquals(IncidentStatus.OPEN, incident.getStatus());
        assertEquals(Priority.CRITICAL, incident.getPriority());
        assertTrue(ms < 5000);
    }

    @Test
    void sameAnomalySentTwiceCreatesOneIncident() throws Exception {
        String id = "it-" + UUID.randomUUID();
        kafka.send("anomaly-detected", id, anomaly(id, "PUMP_FAULT")).get();
        kafka.send("anomaly-detected", id, anomaly(id, "PUMP_FAULT")).get();
        waitFor(id, Duration.ofSeconds(5)).orElseThrow();
        Thread.sleep(1500);
        long count = repository.findAll().stream().filter(i -> id.equals(i.getAnomalyId())).count();
        assertEquals(1, count);
    }

    @Test
    void garbageMessageDoesNotBreakTheConsumer() throws Exception {
        kafka.send("anomaly-detected", "bad", "ceci n'est pas du json").get();
        String id = "it-" + UUID.randomUUID();
        kafka.send("anomaly-detected", id, anomaly(id, "LOW_LEVEL")).get();
        assertTrue(waitFor(id, Duration.ofSeconds(5)).isPresent());
    }
}
