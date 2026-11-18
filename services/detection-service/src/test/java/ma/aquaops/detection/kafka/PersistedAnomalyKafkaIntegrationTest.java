package ma.aquaops.detection.kafka;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import ma.aquaops.detection.anomaly.AnomalyStatus;
import ma.aquaops.detection.anomaly.StoredAnomaly;
import ma.aquaops.detection.anomaly.StoredAnomalyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;

/** BE-1 : de la mesure Kafka a la base : anomalie enregistree ouverte, puis resolue au retour a la normale. */
@SpringBootTest(properties = {
    "detection.kafka.sensor-topic=test-persist-sensor-readings",
    "detection.kafka.anomaly-topic=test-persist-anomaly-detected",
    "detection.kafka.consumer-group=test-persist-detection-group"
})
@EmbeddedKafka(
    partitions = 1,
    topics = {"test-persist-sensor-readings", "test-persist-anomaly-detected"},
    bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@DirtiesContext
class PersistedAnomalyKafkaIntegrationTest {

    @Autowired KafkaTemplate<String, String> template;
    @Autowired StoredAnomalyRepository repository;

    private static String pump(String state, String measuredAt) {
        return """
            {"eventVersion":"1","sensorId":15,"zoneId":7001,"type":"PUMP_STATE",
             "pumpState":"%s","measuredAt":"%s"}
            """.formatted(state, measuredAt);
    }

    private StoredAnomaly awaitStatus(AnomalyStatus wanted) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            Optional<StoredAnomaly> found = repository.findAll().stream()
                .filter(a -> a.zoneId == 7001 && a.status == wanted).findFirst();
            if (found.isPresent()) {
                return found.get();
            }
            Thread.sleep(200);
        }
        fail("Aucune anomalie de la zone 7001 au statut " + wanted);
        return null;
    }

    @Test
    void uneAnomalieDePompeEstEnregistreeOuverteEtPuisResolue() throws Exception {
        template.send("test-persist-sensor-readings", "7001", pump("FAULT", "2026-11-09T15:00:00Z"))
            .get(10, TimeUnit.SECONDS);

        StoredAnomaly open = awaitStatus(AnomalyStatus.OPEN);
        assertEquals("PUMP_FAULT", open.type);
        assertEquals(15, open.sensorId);
        assertNotNull(open.anomalyId);

        template.send("test-persist-sensor-readings", "7001", pump("NORMAL", "2026-11-09T15:00:05Z"))
            .get(10, TimeUnit.SECONDS);

        StoredAnomaly resolved = awaitStatus(AnomalyStatus.RESOLVED);
        assertEquals(open.anomalyId, resolved.anomalyId);
        assertNotNull(resolved.resolvedAt);
    }
}
