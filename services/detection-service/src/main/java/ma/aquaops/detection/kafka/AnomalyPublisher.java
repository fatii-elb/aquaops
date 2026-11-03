package ma.aquaops.detection.kafka;

import ma.aquaops.detection.model.Anomaly;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.concurrent.CompletableFuture;

@Component
public class AnomalyPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;
    private final String topic;

    public AnomalyPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            JsonMapper jsonMapper,
            @Value("${detection.kafka.anomaly-topic}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.jsonMapper = jsonMapper;
        this.topic = topic;
    }

    public CompletableFuture<SendResult<String, String>> publish(
            Anomaly anomaly) {

        String json = jsonMapper.writeValueAsString(anomaly);

        return kafkaTemplate.send(
                topic,
                anomaly.zoneId().toString(),
                json
        );
    }
}
