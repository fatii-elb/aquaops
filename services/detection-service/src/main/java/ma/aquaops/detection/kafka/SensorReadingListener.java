package ma.aquaops.detection.kafka;

import ma.aquaops.detection.engine.DetectionEngine;
import ma.aquaops.detection.model.SensorReading;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

@Component
public class SensorReadingListener {

    private final DetectionEngine engine;
    private final AnomalyPublisher publisher;
    private final JsonMapper jsonMapper;

    public SensorReadingListener(
            DetectionEngine engine,
            AnomalyPublisher publisher,
            JsonMapper jsonMapper) {

        this.engine = engine;
        this.publisher = publisher;
        this.jsonMapper = jsonMapper;
    }

    @KafkaListener(
            topics = "${detection.kafka.sensor-topic}",
            groupId = "${detection.kafka.consumer-group}"
    )
    public void onReading(String json) {

        SensorReading reading =
                jsonMapper.readValue(json, SensorReading.class);

        engine.process(reading)
                .forEach(publisher::publish);
    }
}
