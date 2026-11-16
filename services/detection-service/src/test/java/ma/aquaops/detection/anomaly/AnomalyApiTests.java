package ma.aquaops.detection.anomaly;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/** GET /api/detection/anomalies : filtres, pagination, tri, validation et droits. Zones 9001 a 9003 : propres a ce test. */
@SpringBootTest
@Transactional
class AnomalyApiTests {

    private static final String URL = "/api/detection/anomalies";
    private static final Instant T0 = Instant.parse("2026-11-09T10:00:00Z");

    @Autowired WebApplicationContext wac;
    @Autowired StoredAnomalyRepository repository;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(wac).apply(springSecurity()).build();
        // zone 9001 : fuite ouverte (10:00), pompe resolue (10:05), silence ouvert (10:10) ; zone 9002 : fuite resolue (10:15)
        save("a1", "LEAK_SUSPECTED", 9001, 12, AnomalyStatus.OPEN, T0);
        save("a2", "PUMP_FAULT", 9001, 5, AnomalyStatus.RESOLVED, T0.plusSeconds(300));
        save("a3", "SENSOR_SILENT", 9001, 7, AnomalyStatus.OPEN, T0.plusSeconds(600));
        save("a4", "LEAK_SUSPECTED", 9002, 20, AnomalyStatus.RESOLVED, T0.plusSeconds(900));
    }

    private void save(String id, String type, int zone, int sensor, AnomalyStatus status, Instant detectedAt) {
        StoredAnomaly a = new StoredAnomaly();
        a.anomalyId = id + "-" + zone;
        a.type = type;
        a.zoneId = zone;
        a.sensorId = sensor;
        a.status = status;
        a.dedupKey = AnomalyStore.dedupKey(type, zone, sensor) + ":" + id;
        a.measuredAt = detectedAt.minusSeconds(2);
        a.detectedAt = detectedAt;
        a.resolvedAt = status == AnomalyStatus.RESOLVED ? detectedAt.plusSeconds(60) : null;
        repository.save(a);
    }

    private RequestPostProcessor role(String r) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + r));
    }

    /* ---------------- droits ---------------- */

    @Test
    void sansJetonRenvoie401() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    @Test
    void lectureOuverteAuxQuatreRoles() throws Exception {
        for (String r : List.of("operateur", "superviseur", "technicien", "administrateur")) {
            mvc.perform(get(URL).param("zoneId", "9001").with(role(r))).andExpect(status().isOk());
        }
    }

    @Test
    void unRoleInconnuRenvoie403() throws Exception {
        mvc.perform(get(URL).with(role("visiteur"))).andExpect(status().isForbidden());
    }

    @Test
    void lAnomalieEstEnLectureSeule() throws Exception {
        mvc.perform(delete(URL + "/a1-9001").with(role("administrateur")))
            .andExpect(status().isForbidden());
    }

    /* ---------------- contenu et filtres ---------------- */

    @Test
    void renvoieLesChampsEtLaGravite() throws Exception {
        mvc.perform(get(URL).param("zoneId", "9001").param("type", "LEAK_SUSPECTED").with(role("operateur")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].id").value("a1-9001"))
            .andExpect(jsonPath("$.content[0].type").value("LEAK_SUSPECTED"))
            .andExpect(jsonPath("$.content[0].zoneId").value(9001))
            .andExpect(jsonPath("$.content[0].sensorId").value(12))
            .andExpect(jsonPath("$.content[0].severity").value("CRITICAL"))
            .andExpect(jsonPath("$.content[0].status").value("OPEN"))
            .andExpect(jsonPath("$.content[0].detectedAt").value("2026-11-09T10:00:00Z"));
    }

    @Test
    void filtreParZone() throws Exception {
        mvc.perform(get(URL).param("zoneId", "9002").with(role("operateur")))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].id").value("a4-9002"));
    }

    @Test
    void filtreParStatutSansTenirCompteDeLaCasse() throws Exception {
        mvc.perform(get(URL).param("zoneId", "9001").param("status", "open").with(role("operateur")))
            .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get(URL).param("zoneId", "9001").param("status", "RESOLVED").with(role("operateur")))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].type").value("PUMP_FAULT"));
    }

    @Test
    void filtresCombines() throws Exception {
        mvc.perform(get(URL).param("zoneId", "9001").param("type", "SENSOR_SILENT").param("status", "OPEN")
                .with(role("operateur")))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].id").value("a3-9001"));
    }

    @Test
    void filtreParPeriodeSurLaDateDeDetection() throws Exception {
        mvc.perform(get(URL).param("zoneId", "9001")
                .param("from", "2026-11-09T10:04:00Z").param("to", "2026-11-09T10:11:00Z")
                .with(role("operateur")))
            .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get(URL).param("zoneId", "9001").param("from", "2026-11-09T10:06:00Z")
                .with(role("operateur")))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].id").value("a3-9001"));
    }

    /* ---------------- tri et pagination ---------------- */

    @Test
    void parDefautLesPlusRecentesEnPremier() throws Exception {
        mvc.perform(get(URL).param("zoneId", "9001").with(role("operateur")))
            .andExpect(jsonPath("$.content[0].id").value("a3-9001"))
            .andExpect(jsonPath("$.content[2].id").value("a1-9001"));
    }

    @Test
    void triCroissantSurUneColonne() throws Exception {
        mvc.perform(get(URL).param("zoneId", "9001").param("sort", "detectedAt,asc").with(role("operateur")))
            .andExpect(jsonPath("$.content[0].id").value("a1-9001"));
        mvc.perform(get(URL).param("zoneId", "9001").param("sort", "type,asc").with(role("operateur")))
            .andExpect(jsonPath("$.content[0].type").value("LEAK_SUSPECTED"))
            .andExpect(jsonPath("$.content[2].type").value("SENSOR_SILENT"));
    }

    @Test
    void pagination() throws Exception {
        mvc.perform(get(URL).param("zoneId", "9001").param("size", "2").param("page", "0").with(role("operateur")))
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.totalElements").value(3))
            .andExpect(jsonPath("$.totalPages").value(2))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(2));
        mvc.perform(get(URL).param("zoneId", "9001").param("size", "2").param("page", "1").with(role("operateur")))
            .andExpect(jsonPath("$.content.length()").value(1));
    }

    /* ---------------- validation (400) ---------------- */

    @Test
    void parametresInvalidesRenvoient400() throws Exception {
        mvc.perform(get(URL).param("status", "ferme").with(role("operateur"))).andExpect(status().isBadRequest());
        mvc.perform(get(URL).param("size", "0").with(role("operateur"))).andExpect(status().isBadRequest());
        mvc.perform(get(URL).param("size", "201").with(role("operateur"))).andExpect(status().isBadRequest());
        mvc.perform(get(URL).param("page", "-1").with(role("operateur"))).andExpect(status().isBadRequest());
        mvc.perform(get(URL).param("sort", "password,asc").with(role("operateur"))).andExpect(status().isBadRequest());
        mvc.perform(get(URL).param("sort", "type,sideways").with(role("operateur"))).andExpect(status().isBadRequest());
        mvc.perform(get(URL).param("from", "2026-11-10T00:00:00Z").param("to", "2026-11-09T00:00:00Z")
                .with(role("operateur")))
            .andExpect(status().isBadRequest());
        mvc.perform(get(URL).param("from", "pas-une-date").with(role("operateur"))).andExpect(status().isBadRequest());
    }
}
