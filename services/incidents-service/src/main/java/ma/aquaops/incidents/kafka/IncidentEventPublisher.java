package ma.aquaops.incidents.kafka;

import ma.aquaops.incidents.incident.Incident;
import ma.aquaops.incidents.state.IncidentStatus;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Publie les evenements incident-events (contrat incident-events.v1), cle = incidentId. */
@Component
public class IncidentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(IncidentEventPublisher.class);
    public static final String TOPIC = "incident-events";
    public static final String CREATED = "INCIDENT_CREATED";
    static final String EVENT_VERSION = "1";
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final KafkaTemplate<String, String> kafka;

    public IncidentEventPublisher(KafkaTemplate<String, String> kafka) {
        this.kafka = kafka;
    }

    /** Type d'evenement pour un nouveau statut ; null si le contrat n'en prevoit pas. */
    public static String eventTypeFor(IncidentStatus status) {
        return switch (status) {
            case OPEN -> CREATED;
            case CONFIRMED -> "INCIDENT_CONFIRMED";
            case ASSIGNED -> "INCIDENT_ASSIGNED";
            case IN_PROGRESS -> "INCIDENT_STATUS_CHANGED";
            case RESOLVED -> "INCIDENT_RESOLVED";
            case CLOSED -> "INCIDENT_CLOSED";
        };
    }

    public static String buildPayload(Incident incident, String eventType, String actor) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("eventVersion", EVENT_VERSION);
        m.put("eventType", eventType);
        m.put("incidentId", incident.getId());
        m.put("zoneId", incident.getZoneId());
        m.put("status", incident.getStatus().name());
        m.put("priority", incident.getPriority().name());
        m.put("occurredAt", Instant.now().toString());
        if (incident.getMeasuredAt() != null) {
            m.put("measuredAt", incident.getMeasuredAt().toString());
        }
        if (actor != null && !actor.isBlank()) {
            m.put("actor", actor);
        }
        return MAPPER.writeValueAsString(m);
    }

    public void publish(Incident incident, String eventType, String actor) {
        String payload = buildPayload(incident, eventType, actor);
        kafka.send(TOPIC, String.valueOf(incident.getId()), payload).whenComplete((result, error) -> {
            if (error != null) {
                log.error("Echec de publication {} pour l'incident {} : {}", eventType, incident.getId(),
                        error.getMessage());
            }
        });
    }
}
