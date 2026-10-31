package ma.aquaops.telemetry.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import ma.aquaops.telemetry.readings.MeasureSink;
import ma.aquaops.telemetry.readings.Reading;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;

/** SCRUM-26 : valide -> sink, invalide -> rejet. Kafka est lancé dans le test (pas de Docker). */
@SpringBootTest(properties = "spring.kafka.consumer.auto-offset-reset=earliest")
@EmbeddedKafka(partitions = 1, topics = "sensor-readings",
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class SensorReadingsKafkaTests {

    private static final List<Reading> RECUS = new CopyOnWriteArrayList<>();

    @TestConfiguration
    static class SinkDeTest {
        @Bean
        @Primary
        MeasureSink sinkDeTest() {
            return RECUS::add;
        }
    }

    @Autowired KafkaTemplate<String, String> template;
    @Autowired SensorReadingsConsumer consommateur;

    @Test
    void uneMesureValideArriveAuSinkEtUneInvalideEstRejetee() throws Exception {
        String valide = "{\"eventVersion\":\"1\",\"sensorId\":12,\"zoneId\":3,\"type\":\"PRESSURE\","
                + "\"value\":2.4,\"unit\":\"bar\",\"measuredAt\":\"2026-10-12T21:30:00Z\"}";
        String invalide = "{\"eventVersion\":\"1\",\"sensorId\":12,\"zoneId\":3,\"type\":\"PRESSURE\","
                + "\"value\":\"abc\",\"measuredAt\":\"2026-10-12T21:30:00Z\"}";

        template.send("sensor-readings", valide).get();
        template.send("sensor-readings", invalide).get();

        long limite = System.currentTimeMillis() + 30_000;
        while ((RECUS.isEmpty() || consommateur.rejectedCount() < 1)
                && System.currentTimeMillis() < limite) {
            Thread.sleep(100);
        }

        assertEquals(1, RECUS.size());
        assertEquals(12L, RECUS.get(0).sensorId);
        assertTrue(consommateur.rejectedCount() >= 1);
    }
}