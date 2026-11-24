package ma.aquaops.telemetry.validation;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
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

    /**
     * La partition, l'offset et l'horodatage du message servent a ignorer un message livre deux fois ;
     * l'en-tete reading-id (s'il est present et bien forme) sert a ignorer une mesure envoyee deux fois.
     */
    @KafkaListener(topics = "sensor-readings", groupId = "telemetry-validation")
    public void onMessage(ConsumerRecord<String, String> record) {
        handle(record.value(), new KafkaSource(record.partition(), record.offset(), record.timestamp(),
                readingId(record)));
    }

    /** L'identifiant de la mesure, ou null : en-tete absent ou mal forme (la mesure est gardee, sans lui). */
    static String readingId(ConsumerRecord<String, String> record) {
        Header header = record.headers().lastHeader(KafkaSource.READING_ID_HEADER);
        if (header == null || header.value() == null) {
            return null;
        }
        String value = new String(header.value(), StandardCharsets.UTF_8);
        return KafkaSource.isReadingId(value) ? value : null;
    }

    /** Retourne true si la mesure a été acceptée. */
    public boolean handle(String json) {
        return handle(json, null);
    }

    /** Idem, avec l'origine Kafka du message (null hors Kafka). */
    public boolean handle(String json, KafkaSource source) {
        Map<String, Object> mesure;
        try {
            mesure = mapper.readValue(json, new TypeReference<Map<String, Object>>() { });
        } catch (RuntimeException e) {
            processor.reject("JSON illisible (" + e.getClass().getSimpleName() + ")");
            return false;
        }
        return processor.process(mesure, source);
    }

    public long acceptedCount() {
        return processor.acceptedCount();
    }

    public long rejectedCount() {
        return processor.rejectedCount();
    }
}