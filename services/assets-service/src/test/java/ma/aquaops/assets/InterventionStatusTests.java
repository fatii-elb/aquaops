package ma.aquaops.assets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import ma.aquaops.assets.model.InterventionStatus;
import ma.aquaops.assets.repository.InterventionRepository;
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

/** SCRUM-42 : statut d'intervention, droits (faux JWT) et transitions. */
@SpringBootTest
@Transactional
class InterventionStatusTests {

    @Autowired WebApplicationContext wac;
    @Autowired InterventionRepository repo;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(wac).apply(springSecurity()).build();
    }

    /** Faux jeton Keycloak : un role et un nom d'utilisateur (preferred_username). */
    private RequestPostProcessor as(String role, String username) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))
                    .jwt(j -> j.claim("preferred_username", username));
    }

    private long createUnassigned() throws Exception {
        String body = mvc.perform(post("/api/assets/interventions").with(as("superviseur", "sup1"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"incidentId\":50}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        Matcher m = Pattern.compile("\"id\":(\\d+)").matcher(body);
        m.find();
        return Long.parseLong(m.group(1));
    }

    private long createAssignedTo(String technician) throws Exception {
        long id = createUnassigned();
        mvc.perform(put("/api/assets/interventions/" + id + "/assign").with(as("superviseur", "sup1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"team\":\"Equipe A\",\"assignedTo\":\"" + technician + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.assignedTo").value(technician));
        return id;
    }

    private org.springframework.test.web.servlet.ResultActions setStatus(long id, String value,
                                                                         RequestPostProcessor who) throws Exception {
        return mvc.perform(put("/api/assets/interventions/" + id + "/status").with(who)
            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + value + "\"}"));
    }

    private InterventionStatus statusInDb(long id) {
        return repo.findById(id).orElseThrow().status;
    }

    @Test
    void leTechnicienAssigneFaitAvancerLeStatut() throws Exception {
        long id = createAssignedTo("tech1");
        setStatus(id, "IN_PROGRESS", as("technicien", "tech1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        setStatus(id, "DONE", as("technicien", "tech1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DONE"));
    }

    @Test
    void unAutreTechnicienEstRefuse403() throws Exception {
        long id = createAssignedTo("tech1");
        setStatus(id, "IN_PROGRESS", as("technicien", "tech2")).andExpect(status().isForbidden());
        assertEquals(InterventionStatus.PLANNED, statusInDb(id)); // rien n'a change
    }

    @Test
    void interventionSansTechnicienAssigneRefuseAuTechnicien403() throws Exception {
        long id = createUnassigned();
        setStatus(id, "IN_PROGRESS", as("technicien", "tech1")).andExpect(status().isForbidden());
    }

    @Test
    void unSuperviseurPeutChangerLeStatutMemeSansEtreAssigne() throws Exception {
        long id = createAssignedTo("tech1");
        setStatus(id, "IN_PROGRESS", as("superviseur", "sup2")).andExpect(status().isOk());
        assertEquals(InterventionStatus.IN_PROGRESS, statusInDb(id));
    }

    @Test
    void unOperateurEstRefuse403() throws Exception {
        long id = createAssignedTo("op1");
        setStatus(id, "IN_PROGRESS", as("operateur", "op1")).andExpect(status().isForbidden());
    }

    @Test
    void leTechnicienNePeutPasAffecterUneIntervention403() throws Exception {
        long id = createUnassigned();
        mvc.perform(put("/api/assets/interventions/" + id + "/assign").with(as("technicien", "tech1"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"team\":\"Equipe A\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void transitionsInvalidesRefusees409() throws Exception {
        long id = createAssignedTo("tech1");
        setStatus(id, "DONE", as("technicien", "tech1")).andExpect(status().isConflict());        // saut
        setStatus(id, "PLANNED", as("technicien", "tech1")).andExpect(status().isConflict());     // meme statut
        setStatus(id, "IN_PROGRESS", as("technicien", "tech1")).andExpect(status().isOk());
        setStatus(id, "PLANNED", as("technicien", "tech1")).andExpect(status().isConflict());     // retour
        setStatus(id, "DONE", as("technicien", "tech1")).andExpect(status().isOk());
        setStatus(id, "IN_PROGRESS", as("technicien", "tech1")).andExpect(status().isConflict()); // apres Terminee
        assertEquals(InterventionStatus.DONE, statusInDb(id));
    }

    @Test
    void statutInconnuOuAbsentDonne400() throws Exception {
        long id = createAssignedTo("tech1");
        setStatus(id, "ANNULEE", as("technicien", "tech1")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/assets/interventions/" + id + "/status").with(as("technicien", "tech1"))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void interventionInexistante404() throws Exception {
        setStatus(999999, "IN_PROGRESS", as("superviseur", "sup1")).andExpect(status().isNotFound());
    }

    @Test
    void sansJeton401() throws Exception {
        mvc.perform(put("/api/assets/interventions/1/status")
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"IN_PROGRESS\"}"))
            .andExpect(status().isUnauthorized());
    }
}
