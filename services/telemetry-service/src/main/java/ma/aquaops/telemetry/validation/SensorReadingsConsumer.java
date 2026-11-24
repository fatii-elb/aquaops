package ma.aquaops.telemetry.validation;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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
     * RC-04 : le lot entier rendu par Kafka (au plus max.poll.records) est traite d'un coup, les mesures
     * valides sont ecrites en une transaction. ack-mode batch : les offsets du lot ne sont valides
     * qu'apres son enregistrement ; en cas d'erreur, le lot est rejoue (doublons ignores en base).
     * La partition, l'offset et l'horodatage du message servent a ignorer un message livre deux fois ;
     * l'en-tete reading-id (s'il est present et bien forme) sert a ignorer une mesure envoyee deux fois.
     */
    @KafkaListener(topics = "sensor-readings", groupId = "telemetry-validation", batch = "true")
    public void onMessages(List<ConsumerRecord<String, String>> records) {
        List<MeasureProcessor.Incoming> lot = new ArrayList<>(records.size());
        for (ConsumerRecord<String, String> record : records) {
            Map<String, Object> mesure = lire(record.value());
            if (mesure != null) {
                lot.add(new MeasureProcessor.Incoming(mesure, new KafkaSource(record.partition(), record.offset(),
                        record.timestamp(), readingId(record))));
            }
        }
        processor.processAll(lot);
    }

    /** Un seul message : un lot d'un message. */
    public void onMessage(ConsumerRecord<String, String> record) {
        onMessages(List.of(record));
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
        Map<String, Object> mesure = lire(json);
        return mesure != null && processor.process(mesure, source);
    }

    /** Le JSON lu, ou null (rejete et compte) s'il est illisible ou vaut null. */
    private Map<String, Object> lire(String json) {
        Map<String, Object> mesure;
        try {
            mesure = mapper.readValue(json, new TypeReference<Map<String, Object>>() { });
        } catch (RuntimeException e) {
            processor.reject("JSON illisible (" + e.getClass().getSimpleName() + ")");
            return null;
        }
        if (mesure == null) {
            processor.reject("message vide (null)");
        }
        return mesure;
    }

    public long acceptedCount() {
        return processor.acceptedCount();
    }

    public long rejectedCount() {
        return processor.rejectedCount();
    }
}