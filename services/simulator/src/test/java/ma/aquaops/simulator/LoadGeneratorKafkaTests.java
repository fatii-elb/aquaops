package ma.aquaops.simulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.Properties;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;

/** Le compteur du mode charge doit etre exact : acked = messages reellement ajoutes au topic. */
@SpringBootTest(properties = "simulator.interval-ms=3600000")
@EmbeddedKafka(partitions = 1, topics = "sensor-readings",
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class LoadGeneratorKafkaTests {

    @Autowired
    private LoadGenerator load;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Test
    void compteExactementLesMessagesEnvoyes() throws Exception {
        Thread.sleep(2000); // laisse passer l'envoi initial du publisher normal
        long avant = offsetDeFin();

        load.start(200, 3);
        long limite = System.currentTimeMillis() + 30_000;
        while (load.status().running() && System.currentTimeMillis() < limite) {
            Thread.sleep(100);
        }

        LoadGenerator.Status st = load.status();
        assertFalse(st.running(), "Le test de charge ne s'est pas termine");
        assertEquals(0, st.failed());
        assertEquals(600, st.attempted()); // 200 mesures/s pendant 3 s
        assertEquals(st.attempted(), st.acked());
        assertEquals(st.acked(), offsetDeFin() - avant);
    }

    private long offsetDeFin() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            TopicPartition tp = new TopicPartition("sensor-readings", 0);
            return consumer.endOffsets(List.of(tp)).get(tp);
        }
    }
}