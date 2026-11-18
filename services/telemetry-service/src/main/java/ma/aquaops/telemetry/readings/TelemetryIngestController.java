package ma.aquaops.telemetry.readings;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ma.aquaops.telemetry.validation.MeasureValidator;
import ma.aquaops.telemetry.validation.ValidationResult;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@RestController
@RequestMapping("/api/telemetry/readings")
public class TelemetryIngestController {

    private static final String TOPIC = "sensor-readings";
    private static final Logger LOG = LoggerFactory.getLogger(TelemetryIngestController.class);
    private static final TypeReference<Map<String, Object>> MEASURE_TYPE = new TypeReference<>() { };

    private final KafkaTemplate<String, String> kafka;
    private final JsonMapper json = JsonMapper.builder().build();
    private final MeasureValidator validator = new MeasureValidator();

    public TelemetryIngestController(KafkaTemplate<String, String> kafka) {
        this.kafka = kafka;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<ResponseEntity<Void>> ingest(@RequestBody String payload) {
        Map<String, Object> measure;
        try {
            measure = json.readValue(payload, MEASURE_TYPE);
        } catch (RuntimeException e) {
            LOG.warn("Mesure d'ingestion rejetee : JSON illisible ({})", e.getClass().getSimpleName());
            return CompletableFuture.completedFuture(ResponseEntity.badRequest().build());
        }

        ValidationResult validation = validator.validate(measure);
        if (!validation.isValid()) {
            LOG.warn("Mesure d'ingestion rejetee : {}", validation.errors());
            return CompletableFuture.completedFuture(ResponseEntity.badRequest().build());
        }

        String sensorId = String.valueOf(measure.get("sensorId"));
        try {
            return kafka.send(TOPIC, sensorId, payload).handle((result, error) -> {
                if (error != null) {
                    LOG.error("Echec de publication de la mesure pour sensorId={}", sensorId, error);
                    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).<Void>build();
                }
                return ResponseEntity.accepted().<Void>build();
            });
        } catch (RuntimeException e) {
            LOG.error("Echec de publication de la mesure pour sensorId={}", sensorId, e);
            return CompletableFuture.completedFuture(
                    ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build());
        }
    }
}
