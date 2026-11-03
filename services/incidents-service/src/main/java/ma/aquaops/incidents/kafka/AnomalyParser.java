package ma.aquaops.incidents.kafka;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lit un message anomaly-detected. Message invalide, version inconnue ou champ manquant :
 * journalise puis ignore (Optional vide), jamais d'exception, donc jamais de boucle infinie.
 * Champs inconnus ignores (regle des contrats).
 */
@Component
public class AnomalyParser {

    private static final Logger log = LoggerFactory.getLogger(AnomalyParser.class);
    static final List<String> REQUIRED =
            List.of("eventVersion", "anomalyId", "type", "zoneId", "detectedAt", "measuredAt");
    static final String SUPPORTED_VERSION = "1";

    private final JsonMapper mapper = JsonMapper.builder().build();

    public Optional<AnomalyDetectedEvent> parse(String payload) {
        if (payload == null || payload.isBlank()) {
            log.warn("Message anomaly-detected vide, ignore");
            return Optional.empty();
        }
        try {
            JsonNode n = mapper.readTree(payload);
            for (String field : REQUIRED) {
                JsonNode v = n.path(field);
                if (v.isMissingNode() || v.isNull()) {
                    log.warn("anomaly-detected ignore : champ obligatoire absent '{}'", field);
                    return Optional.empty();
                }
            }
            String version = n.path("eventVersion").asString();
            if (!SUPPORTED_VERSION.equals(version)) {
                log.warn("anomaly-detected ignore : eventVersion {} non supportee", version);
                return Optional.empty();
            }
            JsonNode zone = n.path("zoneId");
            long zoneId = zone.isNumber() ? zone.asLong() : Long.parseLong(zone.asString().trim());
            JsonNode sensor = n.path("sensorId");
            String sensorId = (sensor.isMissingNode() || sensor.isNull()) ? null : sensor.asString();
            JsonNode sev = n.path("severity");
            String severity = (sev.isMissingNode() || sev.isNull()) ? null : sev.asString();
            return Optional.of(new AnomalyDetectedEvent(
                    n.path("anomalyId").asString(),
                    n.path("type").asString(),
                    zoneId,
                    sensorId,
                    Instant.parse(n.path("detectedAt").asString()),
                    Instant.parse(n.path("measuredAt").asString()),
                    severity));
        } catch (RuntimeException e) { // JSON invalide, date ou nombre illisible
            log.warn("anomaly-detected ignore : message illisible ({})", e.getMessage());
            return Optional.empty();
        }
    }
}
