package ma.aquaops.detection.threshold;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/** FR-13 : API GET et PUT des seuils par zone, droits, validation et persistance. Zones 8101 a 8199 : propres a ce test. */
@SpringBootTest
@Transactional
class ThresholdApiTests {

    private static final String URL = "/api/detection/thresholds";
    private static final String VALID = """
        {"leakMinPressure":1.5,"leakMaxFlow":55.0,"leakDurationSeconds":8,"silenceThresholdSeconds":12,"tankMinLevel":3.5}""";

    @Autowired WebApplicationContext wac;
    @Autowired ZoneThresholdRepository repository;
    @Autowired ThresholdCache cache;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(wac).apply(springSecurity()).build();
    }

    private RequestPostProcessor role(String r) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + r));
    }

    /* ---------------- droits ---------------- */

    @Test
    void sansJetonRenvoie401() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mvc.perform(put(URL + "/8101").contentType(MediaType.APPLICATION_JSON).content(VALID))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void lectureOuverteAuxQuatreRoles() throws Exception {
        for (String r : List.of("operateur", "superviseur", "technicien", "administrateur")) {
            mvc.perform(get(URL).with(role(r))).andExpect(status().isOk());
            mvc.perform(get(URL + "/8101").with(role(r))).andExpect(status().isOk());
        }
    }

    @Test
    void unRoleInconnuNePeutPasLire() throws Exception {
        mvc.perform(get(URL).with(role("visiteur"))).andExpect(status().isForbidden());
    }

    @Test
    void seulLAdministrateurPeutModifier() throws Exception {
        for (String r : List.of("operateur", "superviseur", "technicien", "visiteur")) {
            mvc.perform(put(URL + "/8102").with(role(r)).contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isForbidden());
        }
        assertEquals(false, repository.existsById(8102));
        mvc.perform(put(URL + "/8102").with(role("administrateur")).contentType(MediaType.APPLICATION_JSON).content(VALID))
            .andExpect(status().isOk());
    }

    @Test
    void lesAutresMethodesSontRefusees() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(URL + "/8101")
                .with(role("administrateur")))
            .andExpect(status().isForbidden());
    }

    /* ---------------- lecture ---------------- */

    @Test
    void uneZoneSansSeuilsPersonnalisesRenvoieLesValeursParDefaut() throws Exception {
        mvc.perform(get(URL + "/8103").with(role("operateur")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.zoneId").value(8103))
            .andExpect(jsonPath("$.custom").value(false))
            .andExpect(jsonPath("$.thresholds.leakMinPressure").value(2.0))
            .andExpect(jsonPath("$.thresholds.leakMaxFlow").value(40.0))
            .andExpect(jsonPath("$.thresholds.leakDurationSeconds").value(10))
            .andExpect(jsonPath("$.thresholds.tankMinLevel").value(2.0));
    }

    @Test
    void laListeDonneLesValeursParDefautEtLesZonesPersonnalisees() throws Exception {
        mvc.perform(put(URL + "/8104").with(role("administrateur")).contentType(MediaType.APPLICATION_JSON).content(VALID))
            .andExpect(status().isOk());

        mvc.perform(get(URL).with(role("superviseur")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.defaults.leakMaxFlow").value(40.0))
            .andExpect(jsonPath("$.zones[?(@.zoneId==8104)].custom").value(true))
            .andExpect(jsonPath("$.zones[?(@.zoneId==8104)].thresholds.leakMaxFlow").value(55.0));
    }

    /* ---------------- modification ---------------- */

    @Test
    void modifierPersisteLesSeuilsEtLesRelit() throws Exception {
        mvc.perform(put(URL + "/8105").with(role("administrateur")).contentType(MediaType.APPLICATION_JSON).content(VALID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.custom").value(true))
            .andExpect(jsonPath("$.thresholds.leakMinPressure").value(1.5))
            .andExpect(jsonPath("$.thresholds.silenceThresholdSeconds").value(12))
            .andExpect(jsonPath("$.thresholds.tankMinLevel").value(3.5))
            .andExpect(jsonPath("$.updatedBy").isNotEmpty())
            .andExpect(jsonPath("$.updatedAt").isNotEmpty());

        ZoneThresholdEntity stored = repository.findById(8105).orElseThrow();
        assertEquals(1.5, stored.leakMinPressure);
        assertEquals(55.0, stored.leakMaxFlow);
        assertEquals(8L, stored.leakDurationSeconds);
        assertEquals(12L, stored.silenceThresholdSeconds);
        assertEquals(3.5, stored.tankMinLevel);

        mvc.perform(get(URL + "/8105").with(role("operateur")))
            .andExpect(jsonPath("$.custom").value(true))
            .andExpect(jsonPath("$.thresholds.leakMaxFlow").value(55.0));
    }

    @Test
    void unSecondEnvoiRemplaceLesValeursSansCreerDeDoublon() throws Exception {
        String second = """
            {"leakMinPressure":1.0,"leakMaxFlow":70.0,"leakDurationSeconds":20,"silenceThresholdSeconds":30,"tankMinLevel":1.0}""";
        mvc.perform(put(URL + "/8106").with(role("administrateur")).contentType(MediaType.APPLICATION_JSON).content(VALID))
            .andExpect(status().isOk());
        mvc.perform(put(URL + "/8106").with(role("administrateur")).contentType(MediaType.APPLICATION_JSON).content(second))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.thresholds.leakMaxFlow").value(70.0));

        assertEquals(1, repository.findAll().stream().filter(e -> e.zoneId == 8106).count());
        assertEquals(70.0, repository.findById(8106).orElseThrow().leakMaxFlow);
    }

    @Test
    void lesSeuilsModifiesSontImmediatementVisiblesDansLeCacheDeCetteInstance() throws Exception {
        mvc.perform(put(URL + "/8107").with(role("administrateur")).contentType(MediaType.APPLICATION_JSON).content(VALID))
            .andExpect(status().isOk());

        assertEquals(3.5, cache.forZone(8107).tankMinLevel());
        assertEquals(2.0, cache.forZone(8199).tankMinLevel()); // une autre zone garde les valeurs par defaut
    }

    /* ---------------- validation (400) ---------------- */

    @Test
    void desSeuilsInvalidesRenvoient400AvecLaListeDesErreurs() throws Exception {
        String bad = """
            {"leakMinPressure":-1,"leakMaxFlow":0,"leakDurationSeconds":0,"silenceThresholdSeconds":5000,"tankMinLevel":-2}""";
        mvc.perform(put(URL + "/8108").with(role("administrateur")).contentType(MediaType.APPLICATION_JSON).content(bad))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.length()").value(5))
            .andExpect(jsonPath("$.errors[0].field").value("leakMinPressure"))
            .andExpect(jsonPath("$.errors[0].message").isNotEmpty());
        assertEquals(false, repository.existsById(8108));
    }

    @Test
    void unChampAbsentRenvoie400() throws Exception {
        mvc.perform(put(URL + "/8109").with(role("administrateur")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"leakMinPressure\":1.5}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.length()").value(4));
    }

    @Test
    void unCorpsVideOuIllisibleRenvoie400() throws Exception {
        mvc.perform(put(URL + "/8110").with(role("administrateur")).contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest());
        mvc.perform(put(URL + "/8110").with(role("administrateur")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"leakMinPressure\":\"abc\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void unIdentifiantDeZoneInvalideRenvoie400() throws Exception {
        mvc.perform(put(URL + "/0").with(role("administrateur")).contentType(MediaType.APPLICATION_JSON).content(VALID))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("zoneId"));
        mvc.perform(get(URL + "/-3").with(role("operateur"))).andExpect(status().isBadRequest());
        mvc.perform(get(URL + "/abc").with(role("operateur"))).andExpect(status().isBadRequest());
    }
}
