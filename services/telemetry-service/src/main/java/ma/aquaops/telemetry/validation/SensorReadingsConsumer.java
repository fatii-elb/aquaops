package ma.aquaops.telemetry.validation;

import java.util.Map;
import ma.aquaops.telemetry.readings.MeasureSink;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lit le topic sensor-readings (SCRUM-26), valide chaque mesure et l'envoie
 * au MeasureSink. Les messages illisibles ou invalides sont rejetés et journalisés.
 */
@Component
public class SensorReadingsConsumer {

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final MeasureProcessor processor;

    public SensorReadingsConsumer(MeasureSink sink) {
        this.processor = new MeasureProcessor(new MeasureValidator(), sink);
    }

    @KafkaListener(topics = "sensor-readings", groupId = "telemetry-validation")
    public void onMessage(String json) {
        handle(json);
    }

    /** Retourne true si la mesure a été acceptée. */
    public boolean handle(String json) {
        Map<String, Object> mesure;
        try {
            mesure = mapper.readValue(json, new TypeReference<Map<String, Object>>() { });
        } catch (RuntimeException e) {
            processor.reject("JSON illisible (" + e.getClass().getSimpleName() + ")");
            return false;
        }
        return processor.process(mesure);
    }

    public long acceptedCount() {
        return processor.acceptedCount();
    }

    public long rejectedCount() {
        return processor.rejectedCount();
    }
}