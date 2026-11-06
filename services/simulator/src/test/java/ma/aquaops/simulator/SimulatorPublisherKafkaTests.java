package ma.aquaops.simulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;

import tools.jackson.databind.json.JsonMapper;

/**
 * Vérifie que le simulateur publie dans sensor-readings des messages
 * conformes au contrat v1 (contracts/events/sensor-readings.v1.json).
 * Kafka est un broker de test en mémoire : ni Docker ni Kafka local requis.
 */
@SpringBootTest(properties = "simulator.interval-ms=200")
@EmbeddedKafka(partitions = 1, topics = "sensor-readings",
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class SimulatorPublisherKafkaTests {

    private static final Set<String> CHAMPS_AUTORISES = Set.of(
            "eventVersion", "sensorId", "zoneId", "type", "value", "unit", "pumpState", "measuredAt");
    private static final Set<String> TYPES = Set.of("PRESSURE", "FLOW", "LEVEL", "PUMP_STATE");
    private static final Set<String> ETATS_POMPE = Set.of("NORMAL", "FAULT");

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Test
    @SuppressWarnings("unchecked")
    void publieDesMesuresConformesAuContratV1() throws Exception {
        // Agir : lire au moins 7 messages (un cycle complet des 7 capteurs)
        List<ConsumerRecord<String, String>> recus = lire(7, Duration.ofSeconds(30));

        // Vérifier
        assertTrue(recus.size() >= 7, "Moins de 7 messages reçus dans sensor-readings : " + recus.size());
        JsonMapper json = JsonMapper.builder().build();
        for (ConsumerRecord<String, String> r : recus) {
            Map<String, Object> m = json.readValue(r.value(), Map.class);

            assertEquals("1", m.get("eventVersion"));
            assertTrue(m.get("sensorId") instanceof Integer, "sensorId doit être un entier");
            assertTrue(m.get("zoneId") instanceof Integer, "zoneId doit être un entier");
            assertTrue(CHAMPS_AUTORISES.containsAll(m.keySet()), "Champ inconnu dans : " + r.value());
            assertEquals(String.valueOf(m.get("sensorId")), r.key(), "La clé Kafka doit être le sensorId");

            String type = (String) m.get("type");
            assertTrue(TYPES.contains(type), "Type inconnu : " + type);
            if ("PUMP_STATE".equals(type)) {
                assertTrue(ETATS_POMPE.contains(m.get("pumpState")), "pumpState invalide : " + r.value());
                assertFalse(m.containsKey("value"), "PUMP_STATE ne porte pas de value");
            } else {
                assertTrue(m.get("value") instanceof Number, "value doit être un nombre : " + r.value());
                assertTrue(m.get("unit") instanceof String, "unit manquante : " + r.value());
                assertFalse(m.containsKey("pumpState"), "pumpState réservé à PUMP_STATE");
            }

            Instant.parse((String) m.get("measuredAt")); // lève une exception si le format est invalide
        }
    }

    private List<ConsumerRecord<String, String>> lire(int minimum, Duration delai) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-simulator");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        List<ConsumerRecord<String, String>> recus = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of("sensor-readings"));
            long fin = System.currentTimeMillis() + delai.toMillis();
            while (recus.size() < minimum && System.currentTimeMillis() < fin) {
                consumer.poll(Duration.ofMillis(500)).forEach(recus::add);
            }
        }
        return recus;
    }
}