
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
    "detection.kafka.sensor-topic=dedup-test-readings",
    "detection.kafka.anomaly-topic=dedup-test-anomalies",
    "detection.kafka.consumer-group=dedup-test-group"
})
@EmbeddedKafka(
    partitions = 1,
    topics = {
        "dedup-test-readings",
        "dedup-test-anomalies"
    },
    bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@DirtiesContext
class DeduplicationKafkaIntegrationTest {

    private static final Instant START =
            Instant.parse("2026-11-02T10:00:00Z");

    @Autowired
    private KafkaTemplate<String, String> template;

    @Autowired
    private EmbeddedKafkaBroker broker;

    private void sendPump(
            String state,
            int seconds) throws Exception {

        String json = """
                {
                  "eventVersion": "1",
                  "sensorId": 15,
                  "zoneId": 3,
                  "type": "PUMP_STATE",
                  "pumpState": "%s",
                  "measuredAt": "%s"
                }
                """.formatted(
                        state,
                        START.plusSeconds(seconds)
                );

        template.send(
                "dedup-test-readings",
                "3",
                json
        ).get(10, TimeUnit.SECONDS);
    }

    @Test
    void shouldDeduplicateAndReopenAfterRecovery()
            throws Exception {

        var props = KafkaTestUtils.consumerProps(
                broker,
                "dedup-verifier-" + UUID.randomUUID(),
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
                    consumer,
                    "dedup-test-anomalies"
            );

            // 100 mesures de pompe en defaut
            for (int i = 0; i < 100; i++) {
                sendPump("FAULT", i);
            }

            // Recuperation
            sendPump("NORMAL", 100);

            // Nouveau defaut apres recuperation
            sendPump("FAULT", 101);

            // Horodatage de la derniere anomalie
            String finalTimestamp =
                    START.plusSeconds(101).toString();

            List<String> anomalies = new ArrayList<>();
            boolean finalFaultReceived = false;

            long deadline = System.nanoTime()
                    + Duration.ofSeconds(30).toNanos();

            // Lire jusqu'au dernier evenement attendu
            while (!finalFaultReceived
                    && System.nanoTime() < deadline) {

                var records = consumer.poll(
                        Duration.ofMillis(500)
                );

                for (ConsumerRecord<String, String> record
                        : records) {

                    String anomaly = record.value();
                    anomalies.add(anomaly);

                    if (anomaly.contains(
                            "\"measuredAt\":\""
                                    + finalTimestamp + "\"")) {
                        finalFaultReceived = true;
                    }
                }
            }

            assertTrue(
                    finalFaultReceived,
                    "La nouvelle anomalie apres recuperation "
                            + "n'a pas ete recue"
            );

            // Une anomalie initiale + une apres recuperation
            assertEquals(
                    2,
                    anomalies.size(),
                    "Kafka a publie des anomalies en double"
            );

            for (String anomaly : anomalies) {
                assertTrue(anomaly.contains(
                        "\"type\":\"PUMP_FAULT\""
                ));
                assertTrue(anomaly.contains(
                        "\"zoneId\":3"
                ));
            }
        }
    }
}
