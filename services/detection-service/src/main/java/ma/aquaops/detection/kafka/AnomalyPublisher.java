package ma.aquaops.detection.kafka;

import ma.aquaops.detection.anomaly.AnomalyStore;
import ma.aquaops.detection.model.Anomaly;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.concurrent.CompletableFuture;

@Component
public class AnomalyPublisher {

    private static final Logger log =
            LoggerFactory.getLogger(AnomalyPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;
    private final String topic;

    // Facultatif : conserve l'anomalie pour l'API de consultation (BE-1)
    private AnomalyStore store;

    public AnomalyPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            JsonMapper jsonMapper,
            @Value("${detection.kafka.anomaly-topic}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.jsonMapper = jsonMapper;
        this.topic = topic;
    }

    @Autowired(required = false)
    public void setStore(AnomalyStore store) {
        this.store = store;
    }

    public CompletableFuture<SendResult<String, String>> publish(
            Anomaly anomaly) {

        persist(anomaly);

        String json = jsonMapper.writeValueAsString(anomaly);

        return kafkaTemplate.send(
                topic,
                anomaly.zoneId().toString(),
                json
        );
    }

    // Une base indisponible ne doit jamais bloquer la publication Kafka.
    private void persist(Anomaly anomaly) {
        if (store == null) {
            return;
        }
        try {
            store.record(anomaly);
        } catch (RuntimeException e) {
            log.warn("Anomalie {} non enregistree : {}",
                    anomaly.anomalyId(), e.toString());
        }
    }
}
