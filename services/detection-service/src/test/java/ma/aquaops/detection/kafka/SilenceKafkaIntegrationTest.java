package ma.aquaops.detection.kafka;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
    "detection.kafka.sensor-topic=silence-sensor-readings",
    "detection.kafka.anomaly-topic=silence-anomaly-detected",
    "detection.kafka.consumer-group=silence-detection-group",
    "silence.threshold-seconds=3"
})
@EmbeddedKafka(
    partitions = 1,
    topics = {"silence-sensor-readings", "silence-anomaly-detected"},
    bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@DirtiesContext
class SilenceKafkaIntegrationTest {

    private static final Duration THRESHOLD = Duration.ofSeconds(3);
    private static final Duration TOLERANCE = Duration.ofSeconds(2);

    @Autowired
    private KafkaTemplate<String, String> template;

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Autowired
    private KafkaListenerEndpointRegistry registry;

    @Test
    void silentSensorPublishesAnomalyAtThresholdWithinTolerance()
            throws Exception {

        // Le consommateur du service doit être prêt avant d'envoyer
        // la mesure, sinon on mesurerait le démarrage et non le silence.
        for (MessageListenerContainer container
                : registry.getListenerContainers()) {
            ContainerTestUtils.waitForAssignment(
                    container, broker.getPartitionsPerTopic());
        }

        var props = KafkaTestUtils.consumerProps(
                broker, "silence-verifier-" + UUID.randomUUID(), false);

        var factory = new DefaultKafkaConsumerFactory<>(
                props, new StringDeserializer(), new StringDeserializer());

        try (Consumer<String, String> consumer = factory.createConsumer()) {

            broker.consumeFromEmbeddedTopics(
                    consumer, "silence-anomaly-detected");

            String reading = """
                    {
                      "eventVersion": "1",
                      "sensorId": 21,
                      "zoneId": 5,
                      "type": "PRESSURE",
                      "value": 3.1,
                      "unit": "bar",
                      "measuredAt": "2026-11-09T10:00:00Z"
                    }
                    """;

            Instant sentAt = Instant.now();
            template.send("silence-sensor-readings", "5", reading)
                    .get(10, TimeUnit.SECONDS);

            ConsumerRecord<String, String> record =
                    KafkaTestUtils.getSingleRecord(
                            consumer,
                            "silence-anomaly-detected",
                            Duration.ofSeconds(15));

            Duration elapsed = Duration.between(sentAt, Instant.now());

            assertEquals("5", record.key());
            assertTrue(record.value().contains("\"type\":\"SENSOR_SILENT\""));
            assertTrue(record.value().contains("\"sensorId\":21"));
            assertTrue(record.value().contains("\"zoneId\":5"));

            assertTrue(elapsed.compareTo(THRESHOLD) >= 0,
                    "anomalie trop tôt : " + elapsed);
            assertTrue(elapsed.compareTo(THRESHOLD.plus(TOLERANCE)) <= 0,
                    "anomalie trop tard : " + elapsed);
        }
    }
}