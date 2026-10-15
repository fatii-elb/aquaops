package ma.aquaops.assets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import ma.aquaops.assets.model.Intervention;
import ma.aquaops.assets.model.InterventionStatus;
import ma.aquaops.assets.repository.InterventionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Transactional
class InterventionTests {

    @Autowired WebApplicationContext wac;
    @Autowired InterventionRepository repo;
    MockMvc mvc;

    @BeforeEach
    void setup() { mvc = MockMvcBuilders.webAppContextSetup(wac).build(); }

    private long createIntervention(long incidentId) throws Exception {
        String body = mvc.perform(post("/api/assets/interventions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"incidentId\":" + incidentId + "}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        Matcher m = Pattern.compile("\"id\":(\\d+)").matcher(body);
        m.find();
        return Long.parseLong(m.group(1));
    }

    @Test
    void interventionCreeePuisAffecteeAUneEquipe() throws Exception {
        mvc.perform(post("/api/assets/interventions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"incidentId\":42,\"status\":\"DONE\",\"team\":\"X\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.incidentId").value(42))
            .andExpect(jsonPath("$.status").value("PLANNED"))   // statut impose par le serveur
            .andExpect(jsonPath("$.team").doesNotExist());       // pas d'equipe a la creation

        long id = createIntervention(42);
        mvc.perform(put("/api/assets/interventions/" + id + "/assign")
                .contentType(MediaType.APPLICATION_JSON).content("{\"team\":\"Equipe A\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.team").value("Equipe A"));
        mvc.perform(get("/api/assets/interventions/" + id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.team").value("Equipe A"));
    }

    @Test
    void leLienIncidentInterventionEstVerifiableEnBase() throws Exception {
        long id = createIntervention(777);
        var liste = repo.findByIncidentId(777L);
        assertEquals(1, liste.size());
        assertEquals(id, liste.get(0).id);
        assertEquals(777L, liste.get(0).incidentId);
        assertEquals(InterventionStatus.PLANNED, liste.get(0).status);
    }

    @Test
    void listeFiltreeParIncident() throws Exception {
        createIntervention(101);
        createIntervention(101);
        createIntervention(102);
        mvc.perform(get("/api/assets/interventions").param("incidentId", "101"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void sansIncidentRefusee() throws Exception {
        mvc.perform(post("/api/assets/interventions").contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void affectationSansEquipeRefusee() throws Exception {
        long id = createIntervention(5);
        mvc.perform(put("/api/assets/interventions/" + id + "/assign")
                .contentType(MediaType.APPLICATION_JSON).content("{\"team\":\"\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void affecterUneInterventionInexistante404() throws Exception {
        mvc.perform(put("/api/assets/interventions/999999/assign")
                .contentType(MediaType.APPLICATION_JSON).content("{\"team\":\"Equipe A\"}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void affecterUneInterventionTerminee409() throws Exception {
        long id = createIntervention(6);
        Intervention i = repo.findById(id).orElseThrow();
        i.status = InterventionStatus.DONE;
        repo.saveAndFlush(i);
        mvc.perform(put("/api/assets/interventions/" + id + "/assign")
                .contentType(MediaType.APPLICATION_JSON).content("{\"team\":\"Equipe A\"}"))
            .andExpect(status().isConflict());
    }
}
