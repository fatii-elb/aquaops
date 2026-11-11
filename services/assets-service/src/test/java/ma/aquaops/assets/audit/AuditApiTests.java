package ma.aquaops.assets.audit;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

/**
 * SCRUM-46 / FR-24 : chaque action sensible laisse une ligne (qui, quand, quoi),
 * et le journal se consulte avec filtres et pagination, reserve aux superviseurs et administrateurs.
 * Chaque test utilise un nom d'utilisateur unique, pour ne pas dependre des lignes deja en base.
 */
@SpringBootTest
@Transactional
class AuditApiTests {

    @Autowired WebApplicationContext wac;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(wac).apply(springSecurity()).build();
    }

    private static RequestPostProcessor user(String name, String role) {
        return jwt().jwt(j -> j.claim("preferred_username", name))
                    .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private static String unique() { return "test-" + UUID.randomUUID().toString().substring(0, 8); }

    private long create(RequestPostProcessor who, String url, String json) throws Exception {
        String body = mvc.perform(post(url).with(who).contentType(MediaType.APPLICATION_JSON).content(json))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Matcher m = Pattern.compile("\"id\":(\\d+)").matcher(body);
        m.find();
        return Long.parseLong(m.group(1));
    }

    @Test
    void creationDeZoneLaisseUneLigneQuiQuandQuoi() throws Exception {
        String admin = unique();
        Instant before = Instant.now().minusSeconds(5);
        long zone = create(user(admin, "administrateur"), "/api/assets/zones", "{\"name\":\"Zone audit\"}");

        mvc.perform(get("/api/assets/audit").param("actor", admin).with(user("sup", "superviseur")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].actor").value(admin))
            .andExpect(jsonPath("$.content[0].actorRole").value("administrateur"))
            .andExpect(jsonPath("$.content[0].action").value("CREATE"))
            .andExpect(jsonPath("$.content[0].entityType").value("ZONE"))
            .andExpect(jsonPath("$.content[0].entityId").value(String.valueOf(zone)))
            .andExpect(jsonPath("$.content[0].details").value("nom : Zone audit"))
            .andExpect(jsonPath("$.content[0].source").value("assets-service"))
            .andExpect(jsonPath("$.content[0].occurredAt").exists());
        // la date est bien celle de l'action
        mvc.perform(get("/api/assets/audit").param("actor", admin).param("from", before.toString())
                .with(user("sup", "superviseur")))
            .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void modificationEtSuppressionSontTracees() throws Exception {
        String admin = unique();
        var who = user(admin, "administrateur");
        long zone = create(who, "/api/assets/zones", "{\"name\":\"Z1\"}");
        long pump = create(who, "/api/assets/pumps", "{\"name\":\"P1\",\"zoneId\":" + zone + "}");
        mvc.perform(put("/api/assets/pumps/" + pump).with(who).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"P1b\",\"zoneId\":" + zone + "}")).andExpect(status().isOk());
        mvc.perform(delete("/api/assets/pumps/" + pump).with(who)).andExpect(status().isNoContent());

        var sup = user("sup", "superviseur");
        mvc.perform(get("/api/assets/audit").param("actor", admin).param("entityType", "PUMP").with(sup))
            .andExpect(jsonPath("$.totalElements").value(3));
        mvc.perform(get("/api/assets/audit").param("actor", admin).param("action", "DELETE").with(sup))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].details").value("nom : P1b, zone " + zone));
        mvc.perform(get("/api/assets/audit").param("actor", admin).param("action", "UPDATE").with(sup))
            .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void interventionsSontTracees() throws Exception {
        String sup = unique();
        var who = user(sup, "superviseur");
        long id = create(who, "/api/assets/interventions", "{\"incidentId\":42}");
        mvc.perform(put("/api/assets/interventions/" + id + "/assign").with(who)
            .contentType(MediaType.APPLICATION_JSON).content("{\"team\":\"Equipe A\",\"assignedTo\":\"tech1\"}"))
            .andExpect(status().isOk());
        mvc.perform(put("/api/assets/interventions/" + id + "/status").with(who)
            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"IN_PROGRESS\"}"))
            .andExpect(status().isOk());

        mvc.perform(get("/api/assets/audit").param("actor", sup).param("entityType", "INTERVENTION")
                .with(user("admin", "administrateur")))
            .andExpect(jsonPath("$.totalElements").value(3));
        mvc.perform(get("/api/assets/audit").param("actor", sup).param("action", "STATUS_CHANGE")
                .with(user("admin", "administrateur")))
            .andExpect(jsonPath("$.content[0].details").value("PLANNED -> IN_PROGRESS"));
    }

    @Test
    void actionRefuseNeLaisseAucuneTrace() throws Exception {
        String op = unique();
        mvc.perform(post("/api/assets/zones").with(user(op, "operateur"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/assets/audit").param("actor", op).with(user("sup", "superviseur")))
            .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void paginationEtOrdreDuPlusRecentAuPlusAncien() throws Exception {
        String admin = unique();
        var who = user(admin, "administrateur");
        for (int i = 1; i <= 3; i++)
            create(who, "/api/assets/zones", "{\"name\":\"Z" + i + "\"}");

        var sup = user("sup", "superviseur");
        mvc.perform(get("/api/assets/audit").param("actor", admin).param("size", "2").with(sup))
            .andExpect(jsonPath("$.totalElements").value(3))
            .andExpect(jsonPath("$.totalPages").value(2))
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.content[0].details").value("nom : Z3"))
            .andExpect(jsonPath("$.content[1].details").value("nom : Z2"));
        mvc.perform(get("/api/assets/audit").param("actor", admin).param("size", "2").param("page", "1").with(sup))
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].details").value("nom : Z1"));
    }

    @Test
    void filtreParPeriodeExclutLesAutresDates() throws Exception {
        String admin = unique();
        create(user(admin, "administrateur"), "/api/assets/zones", "{\"name\":\"Z\"}");
        var sup = user("sup", "superviseur");
        Instant now = Instant.now();
        mvc.perform(get("/api/assets/audit").param("actor", admin)
                .param("from", now.plusSeconds(3600).toString()).with(sup))
            .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/assets/audit").param("actor", admin)
                .param("to", now.minusSeconds(3600).toString()).with(sup))
            .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void filtreParUtilisateurIgnoreLaCasseEtLesJokers() throws Exception {
        String admin = unique();
        create(user(admin, "administrateur"), "/api/assets/zones", "{\"name\":\"Z\"}");
        var sup = user("sup", "superviseur");
        mvc.perform(get("/api/assets/audit").param("actor", admin.toUpperCase()).with(sup))
            .andExpect(jsonPath("$.totalElements").value(1));
        // "%" est cherche tel quel, il ne vaut pas "tout"
        mvc.perform(get("/api/assets/audit").param("actor", "%" + admin).with(sup))
            .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void parametresInvalidesRenvoient400() throws Exception {
        var sup = user("sup", "superviseur");
        mvc.perform(get("/api/assets/audit").param("size", "0").with(sup)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/assets/audit").param("size", "1000").with(sup)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/assets/audit").param("page", "-1").with(sup)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/assets/audit")
                .param("from", "2026-12-02T00:00:00Z").param("to", "2026-12-01T00:00:00Z").with(sup))
            .andExpect(status().isBadRequest());
    }

    @Test
    void listeDesTypesDAction() throws Exception {
        create(user(unique(), "administrateur"), "/api/assets/zones", "{\"name\":\"Z\"}");
        mvc.perform(get("/api/assets/audit/actions").with(user("sup", "superviseur")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@ == 'CREATE')]").exists());
    }

    @Test
    void lectureReserveeAuxSuperviseursEtAdministrateurs() throws Exception {
        mvc.perform(get("/api/assets/audit")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/assets/audit").with(user("o", "operateur"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/assets/audit").with(user("t", "technicien"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/assets/audit").with(user("s", "superviseur"))).andExpect(status().isOk());
        mvc.perform(get("/api/assets/audit").with(user("a", "administrateur"))).andExpect(status().isOk());
        mvc.perform(get("/api/assets/audit/actions").with(user("o", "operateur"))).andExpect(status().isForbidden());
    }

    @Test
    void lApiNePermetAucuneEcritureDansLeJournal() throws Exception {
        var admin = user("a", "administrateur");
        mvc.perform(post("/api/assets/audit").with(admin).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
        mvc.perform(delete("/api/assets/audit/1").with(admin)).andExpect(status().isForbidden());
        mvc.perform(put("/api/assets/audit/1").with(admin).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
    }
}
