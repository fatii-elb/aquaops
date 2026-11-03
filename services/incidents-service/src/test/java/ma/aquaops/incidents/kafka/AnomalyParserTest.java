package ma.aquaops.incidents.kafka;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AnomalyParserTest {

    private final AnomalyParser parser = new AnomalyParser();

    private static final String VALID = """
            {"eventVersion":1,"anomalyId":"a-1","type":"PUMP_FAULT","zoneId":2,
             "measuredAt":"2026-11-03T09:00:00Z","detectedAt":"2026-11-03T09:00:01Z"}""";

    @Test
    void fixtureIsParsed() throws IOException {
        String json = Files.readString(Path.of("src/test/resources/fixtures/anomaly-leak.json"));
        AnomalyDetectedEvent e = parser.parse(json).orElseThrow();
        assertEquals("fixture-leak-0001", e.anomalyId());
        assertEquals("LEAK_SUSPECTED", e.type());
        assertEquals(3L, e.zoneId());
        assertEquals("pressure-zone-3", e.sensorId());
        assertEquals(Instant.parse("2026-11-03T09:00:00Z"), e.measuredAt());
    }

    @Test
    void optionalFieldsMayBeAbsent() {
        AnomalyDetectedEvent e = parser.parse(VALID).orElseThrow();
        assertEquals("a-1", e.anomalyId());
        assertEquals(null, e.sensorId());
    }

    @Test
    void zoneIdAsTextIsAccepted() {
        assertTrue(parser.parse(VALID.replace("\"zoneId\":2", "\"zoneId\":\"2\"")).isPresent());
    }

    @Test
    void missingRequiredFieldIsIgnored() {
        for (String field : AnomalyParser.REQUIRED) {
            String broken = VALID.replace("\"" + field + "\"", "\"x_" + field + "\"");
            assertEquals(Optional.empty(), parser.parse(broken), "champ retire : " + field);
        }
    }

    @Test
    void unknownVersionIsIgnored() {
        assertEquals(Optional.empty(), parser.parse(VALID.replace("\"eventVersion\":1", "\"eventVersion\":2")));
    }

    @Test
    void garbageIsIgnored() {
        assertEquals(Optional.empty(), parser.parse("pas du json"));
        assertEquals(Optional.empty(), parser.parse(""));
        assertEquals(Optional.empty(), parser.parse(null));
        assertEquals(Optional.empty(), parser.parse(VALID.replace("2026-11-03T09:00:00Z", "hier")));
    }
}
