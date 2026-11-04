package ma.aquaops.incidents.kafka;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ma.aquaops.incidents.incident.Incident;
import ma.aquaops.incidents.incident.Priority;
import ma.aquaops.incidents.state.IncidentStatus;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Le contrat incident-events.v1.json exige eventVersion = "1" (chaine), pas le nombre 1. */
class IncidentEventVersionTest {

    @Test
    void eventVersionIsTheStringOne() {
        Incident incident = new Incident();
        incident.setId(42L);
        incident.setZoneId(3L);
        incident.setStatus(IncidentStatus.OPEN);
        incident.setPriority(Priority.CRITICAL);

        String json = IncidentEventPublisher.buildPayload(incident, IncidentEventPublisher.CREATED, null);
        JsonNode event = JsonMapper.builder().build().readTree(json);

        assertTrue(event.get("eventVersion").isString(), "eventVersion doit etre une chaine : " + json);
        assertEquals("1", event.get("eventVersion").asString());
        assertTrue(json.contains("\"eventVersion\":\"1\""), json);
    }
}
