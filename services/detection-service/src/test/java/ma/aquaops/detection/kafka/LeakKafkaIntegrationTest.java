
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
    "detection.kafka.sensor-topic=leak-test-readings",
    "detection.kafka.anomaly-topic=leak-test-anomalies",
    "detection.kafka.consumer-group=leak-test-detection"
})
@EmbeddedKafka(
    partitions = 1,
    topics = {
        "leak-test-readings",
        "leak-test-anomalies"
    },
    bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@DirtiesContext
class LeakKafkaIntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> template;

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Test
    void shouldPublishLeakSuspected() throws Exception {

        var properties = KafkaTestUtils.consumerProps(
                broker,
                "leak-verifier-" + UUID.randomUUID(),
                false
        );

        var factory = new DefaultKafkaConsumerFactory<>(
                properties,
                new StringDeserializer(),
                new StringDeserializer()
        );

        try (Consumer<String, String> consumer =
                     factory.createConsumer()) {

            broker.consumeFromEmbeddedTopics(
                    consumer,
                    "leak-test-anomalies"
            );

            String pressureStart = """
                {
                  "eventVersion":"1",
                  "sensorId":15,
                  "zoneId":3,
                  "type":"PRESSURE",
                  "value":1.5,
                  "unit":"bar",
                  "measuredAt":"2026-11-02T15:00:00Z"
                }
                """;

            String highFlow = """
                {
                  "eventVersion":"1",
                  "sensorId":16,
                  "zoneId":3,
                  "type":"FLOW",
                  "value":45.0,
                  "unit":"m3/h",
                  "measuredAt":"2026-11-02T15:00:02Z"
                }
                """;

            String pressureAfterTenSeconds = """
                {
                  "eventVersion":"1",
                  "sensorId":15,
                  "zoneId":3,
                  "type":"PRESSURE",
                  "value":1.4,
                  "unit":"bar",
                  "measuredAt":"2026-11-02T15:00:10Z"
                }
                """;

            template.send(
                    "leak-test-readings", "3", pressureStart
            ).get(10, TimeUnit.SECONDS);

            template.send(
                    "leak-test-readings", "3", highFlow
            ).get(10, TimeUnit.SECONDS);

            template.send(
                    "leak-test-readings", "3",
                    pressureAfterTenSeconds
            ).get(10, TimeUnit.SECONDS);

            ConsumerRecord<String, String> result =
                    KafkaTestUtils.getSingleRecord(
                            consumer,
                            "leak-test-anomalies",
                            Duration.ofSeconds(15)
                    );

            assertEquals("3", result.key());

            assertTrue(result.value().contains(
                    "\"type\":\"LEAK_SUSPECTED\""
            ));

            assertTrue(result.value().contains(
                    "\"zoneId\":3"
            ));
        }
    }
}
