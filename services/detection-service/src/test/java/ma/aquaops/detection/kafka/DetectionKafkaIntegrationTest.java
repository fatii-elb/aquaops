package ma.aquaops.detection.kafka;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
    "detection.kafka.sensor-topic=test-sensor-readings",
    "detection.kafka.anomaly-topic=test-anomaly-detected",
    "detection.kafka.consumer-group=test-detection-group"
})
@EmbeddedKafka(
    partitions = 1,
    topics = {"test-sensor-readings", "test-anomaly-detected"},
    bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@DirtiesContext
class DetectionKafkaIntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> template;

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Test
    void pumpFaultShouldPublishAnomaly() throws Exception {

        var props = KafkaTestUtils.consumerProps(
                broker,
                "test-verifier-" + UUID.randomUUID(),
                false
        );

        var factory = new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                new StringDeserializer()
        );

        try (Consumer<String, String> consumer =
                     factory.createConsumer()) {

            broker.consumeFromEmbeddedTopics(
                    consumer, "test-anomaly-detected"
            );

            String reading = """
                    {
                      "eventVersion": "1",
                      "sensorId": 15,
                      "zoneId": 3,
                      "type": "PUMP_STATE",
                      "pumpState": "FAULT",
                      "measuredAt": "2026-11-02T15:00:00Z"
                    }
                    """;

            template.send(
                    "test-sensor-readings", "3", reading
            ).get(10, TimeUnit.SECONDS);

            ConsumerRecord<String, String> result =
                    KafkaTestUtils.getSingleRecord(
                            consumer,
                            "test-anomaly-detected",
                            Duration.ofSeconds(15)
                    );

            assertEquals("3", result.key());
            assertTrue(result.value().contains(
                    "\"type\":\"PUMP_FAULT\""
            ));
            assertTrue(result.value().contains(
                    "\"sensorId\":15"
            ));
        }
    }
}
