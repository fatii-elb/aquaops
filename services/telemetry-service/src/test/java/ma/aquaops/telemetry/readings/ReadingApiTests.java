package ma.aquaops.telemetry.readings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import ma.aquaops.telemetry.TelemetryServiceApplication;

@SpringBootTest(classes = TelemetryServiceApplication.class)
class ReadingApiTests {

    private static final long SENSOR = 9_000_001L;
    private static final long OTHER_SENSOR = 9_000_002L;

    @Autowired WebApplicationContext wac;
    @Autowired MeasureSink sink;
    @Autowired ReadingRepository repository;
    @Autowired JdbcTemplate jdbc;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(wac).build();
        jdbc.update("delete from readings where sensor_id in (?, ?)", SENSOR, OTHER_SENSOR);
    }

    @Test
    void uneMesureEnvoyeeAuSinkEstLueEnBase() {
        Instant t = Instant.parse("2026-10-20T10:00:00Z");
        sink.record(new Reading(SENSOR, 3L, "PRESSURE", 2.4, "bar", null, t));

        var page = repository.findBySensorIdAndMeasuredAtBetweenOrderByMeasuredAtAsc(
                SENSOR, t.minusSeconds(1), t.plusSeconds(1),
                org.springframework.data.domain.PageRequest.of(0, 10));
        assertEquals(1, page.getTotalElements());
        assertEquals(2.4, page.getContent().get(0).value);
    }

    @Test
    void lApiRenvoieLaBonnePeriodePourLeBonCapteur() throws Exception {
        Instant t = Instant.parse("2026-10-20T10:00:00Z");
        sink.record(new Reading(SENSOR, 3L, "PRESSURE", 2.0, "bar", null, t));
        sink.record(new Reading(SENSOR, 3L, "PRESSURE", 2.1, "bar", null, t.plusSeconds(60)));
        sink.record(new Reading(SENSOR, 3L, "PRESSURE", 2.2, "bar", null, t.plusSeconds(7200))); // hors periode
        sink.record(new Reading(OTHER_SENSOR, 3L, "PRESSURE", 9.9, "bar", null, t)); // autre capteur

        mvc.perform(get("/api/telemetry/readings")
                        .param("sensor", String.valueOf(SENSOR))
                        .param("from", "2026-10-20T09:59:00Z")
                        .param("to", "2026-10-20T11:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].value").value(2.0))
                .andExpect(jsonPath("$.content[1].value").value(2.1));
    }

    @Test
    void laPaginationLimiteLeNombreDeResultats() throws Exception {
        Instant t = Instant.parse("2026-10-20T10:00:00Z");
        for (int i = 0; i < 5; i++) {
            sink.record(new Reading(SENSOR, 3L, "FLOW", 1.0 + i, "m3/h", null, t.plusSeconds(i)));
        }
        mvc.perform(get("/api/telemetry/readings")
                        .param("sensor", String.valueOf(SENSOR))
                        .param("from", "2026-10-20T09:00:00Z")
                        .param("to", "2026-10-20T11:00:00Z")
                        .param("size", "2").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    void parametreManquantOuIncoherentDonne400() throws Exception {
        mvc.perform(get("/api/telemetry/readings").param("sensor", "1"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/telemetry/readings")
                        .param("sensor", "1")
                        .param("from", "2026-10-21T00:00:00Z")
                        .param("to", "2026-10-20T00:00:00Z"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/telemetry/readings")
                        .param("sensor", "1")
                        .param("from", "2026-10-20T00:00:00Z")
                        .param("to", "2026-10-21T00:00:00Z")
                        .param("size", "5000"))
                .andExpect(status().isBadRequest());
    }
}
