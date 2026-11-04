package ma.aquaops.incidents.kafka;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import ma.aquaops.incidents.incident.Incident;
import ma.aquaops.incidents.incident.Priority;
import ma.aquaops.incidents.state.IncidentStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Verifie que ce qu'on publie respecte contracts/events/incident-events.v1.json (lu dans le depot). */
class IncidentEventsContractTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static JsonNode contract;

    @BeforeAll
    static void loadContract() throws IOException {
        Path p = Path.of("../../contracts/events/incident-events.v1.json");
        assumeTrue(Files.exists(p), "contrat introuvable : " + p.toAbsolutePath());
        contract = MAPPER.readTree(Files.readString(p));
    }

    private static List<String> enumOf(String property) {
        JsonNode node = contract.findValue(property);
        List<String> values = new ArrayList<>();
        if (node != null && node.has("enum")) {
            for (JsonNode v : node.get("enum")) {
                values.add(v.asString());
            }
        }
        return values;
    }

    private static Incident sample(IncidentStatus status, Priority priority) {
        Incident i = new Incident();
        i.setId(42L);
        i.setZoneId(3L);
        i.setStatus(status);
        i.setPriority(priority);
        i.setMeasuredAt(java.time.Instant.parse("2026-11-03T09:00:00Z"));
        return i;
    }

    @Test
    void payloadHasEveryRequiredField() {
        String json = IncidentEventPublisher.buildPayload(
                sample(IncidentStatus.OPEN, Priority.CRITICAL), IncidentEventPublisher.CREATED, "alice");
        JsonNode event = MAPPER.readTree(json);
        for (JsonNode required : contract.path("required")) {
            assertTrue(event.has(required.asString()), "champ obligatoire absent : " + required.asString());
        }
    }

    @Test
    void everyEventTypeWeCanPublishIsInTheContract() {
        List<String> allowed = enumOf("eventType");
        assumeTrue(!allowed.isEmpty(), "pas d'enum eventType dans le contrat");
        for (IncidentStatus s : IncidentStatus.values()) {
            String type = IncidentEventPublisher.eventTypeFor(s);
            assertTrue(allowed.contains(type), "eventType " + type + " (statut " + s + ") absent du contrat " + allowed);
        }
    }

    @Test
    void statusAndPriorityValuesAreInTheContract() {
        List<String> statuses = enumOf("status");
        if (!statuses.isEmpty()) {
            for (IncidentStatus s : IncidentStatus.values()) {
                assertTrue(statuses.contains(s.name()), "statut absent du contrat : " + s);
            }
        }
        List<String> priorities = enumOf("priority");
        if (!priorities.isEmpty()) {
            for (Priority p : Priority.values()) {
                assertTrue(priorities.contains(p.name()), "priorite absente du contrat : " + p + " / " + priorities);
            }
        }
    }

    @Test
    void eventVersionTypeMatches() {
        JsonNode version = contract.findValue("eventVersion");
        assumeTrue(version != null && version.has("type"));
        String json = IncidentEventPublisher.buildPayload(
                sample(IncidentStatus.OPEN, Priority.LOW), IncidentEventPublisher.CREATED, null);
        JsonNode event = MAPPER.readTree(json);
        String expected = version.get("type").asString();
        assertEquals(expected.equals("integer"), event.get("eventVersion").isIntegralNumber(),
                "type de eventVersion different du contrat (" + expected + ")");
    }
}
