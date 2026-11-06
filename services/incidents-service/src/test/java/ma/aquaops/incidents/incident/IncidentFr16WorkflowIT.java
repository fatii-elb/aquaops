package ma.aquaops.incidents.incident;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import ma.aquaops.incidents.kafka.IncidentEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * SCRUM-38 / FR-16 de bout en bout cote HTTP : vrai contexte Spring, vrai PostgreSQL, MockMvc.
 * Creer, confirmer, assigner, commenter, lire l'historique, clore ; puis les refus.
 * PAS de @Transactional : chaque requete valide ou annule pour de vrai ; la base est relue en SQL.
 * Ne tourne que si AQUAOPS_IT=1, avec INCIDENTS_DB_URL, DB_USER et DB_PASSWORD vers une base JETABLE.
 * Kafka : consommateurs arretes, publieur remplace par un mock (aucun envoi reel).
 * Les auteurs (actor, author) sont fournis par le client et NE SONT PAS authentifies.
 */
@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.bootstrap-servers=127.0.0.1:1"
})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "AQUAOPS_IT", matches = "1")
class IncidentFr16WorkflowIT {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    IncidentEventPublisher publisher;

    @BeforeEach
    void resetPublisher() {
        reset(publisher);
    }

    /** Envoie la requete, journalise "FR16-HTTP <methode> <url> -> <statut> <corps>" et rend la reponse. */
    private MvcResult call(String label, MockHttpServletRequestBuilder request, int expectedStatus) throws Exception {
        MvcResult r = mvc.perform(request).andReturn();
        String body = r.getResponse().getContentAsString();
        System.out.println("FR16-HTTP " + label + " -> " + r.getResponse().getStatus() + " " + body);
        assertEquals(expectedStatus, r.getResponse().getStatus(), label + " : " + body);
        return r;
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, String body) {
        return b.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static JsonNode body(MvcResult r) throws Exception {
        return JSON.readTree(r.getResponse().getContentAsString());
    }

    private long creerIncident() throws Exception {
        long zone = ThreadLocalRandom.current().nextLong(1_000_000L, 9_000_000_000L);
        MvcResult r = call("POST /api/incidents", json(post("/api/incidents"),
                "{\"zoneId\":" + zone + ",\"type\":\"PUMP_FAULT\",\"description\":\"SCRUM-38 IT\",\"actor\":\"operateur-it\"}"),
                201);
        return body(r).get("id").asLong();
    }

    private String statutEnBase(long id) {
        return jdbc.queryForObject("select status from incidents where id = ?", String.class, id);
    }

    private List<Map<String, Object>> historiqueEnBase(long id) {
        return jdbc.queryForList("select id, action, from_status, to_status, author, comment, occurred_at "
                + "from incident_history where incident_id = ? order by occurred_at, id", id);
    }

    private int lignes(long id) {
        return jdbc.queryForObject("select count(*) from incident_history where incident_id = ?", Integer.class, id);
    }

    @Test
    void parcoursCompletCreerConfirmerAssignerCommenterLireClore() throws Exception {
        Instant debut = Instant.now().truncatedTo(ChronoUnit.MICROS);

        // 1. Creer
        long id = creerIncident();
        assertEquals("OPEN", statutEnBase(id));
        assertEquals(0, lignes(id), "la creation n'ecrit pas d'historique");

        // 2. Confirmer
        call("PUT confirm", json(put("/api/incidents/" + id), "{\"status\":\"CONFIRMED\",\"actor\":\"superviseur-it\"}"), 200);
        // 3. Assigner
        call("PUT assign", json(put("/api/incidents/" + id),
                "{\"status\":\"ASSIGNED\",\"assignee\":\"technicien-it\",\"actor\":\"superviseur-it\"}"), 200);
        // 4. Commenter
        MvcResult c = call("POST comment", json(post("/api/incidents/" + id + "/comments"),
                "{\"text\":\"Vanne V12 fermee, equipe sur place\",\"author\":\"technicien-it\"}"), 201);
        JsonNode comment = body(c);
        assertEquals("COMMENTED", comment.get("action").asString());
        assertTrue(comment.get("fromStatus").isNull() && comment.get("toStatus").isNull());
        assertEquals("ASSIGNED", statutEnBase(id), "un commentaire ne change pas le statut");

        // 5. Lire l'historique
        JsonNode h = body(call("GET history", get("/api/incidents/" + id + "/history"), 200));
        assertEquals(3, h.size());
        assertEquals(List.of("CONFIRMED", "ASSIGNED", "COMMENTED"), texts(h, "action"));
        assertEquals(List.of("superviseur-it", "superviseur-it", "technicien-it"), texts(h, "author"));
        assertEquals("OPEN", h.get(0).get("fromStatus").asString());
        assertEquals("CONFIRMED", h.get(0).get("toStatus").asString());
        assertEquals("CONFIRMED", h.get(1).get("fromStatus").asString());
        assertEquals("ASSIGNED", h.get(1).get("toStatus").asString());
        assertEquals("Vanne V12 fermee, equipe sur place", h.get(2).get("comment").asString());
        List<Instant> heures = new ArrayList<>();
        h.forEach(e -> heures.add(Instant.parse(e.get("occurredAt").asString())));
        for (int i = 0; i < heures.size(); i++) {
            assertFalse(heures.get(i).isBefore(debut), "heure avant le debut du test");
            if (i > 0) {
                assertFalse(heures.get(i).isBefore(heures.get(i - 1)), "ordre chronologique");
            }
        }

        // 6. Clore (ASSIGNED -> CLOSED est autorise par IncidentStateMachine)
        call("PUT close", json(put("/api/incidents/" + id), "{\"status\":\"CLOSED\",\"actor\":\"superviseur-it\"}"), 200);

        // 7. Etat final relu dans PostgreSQL
        assertEquals("CLOSED", statutEnBase(id));
        assertEquals("technicien-it", jdbc.queryForObject("select assignee from incidents where id = ?", String.class, id));
        List<Map<String, Object>> rows = historiqueEnBase(id);
        assertEquals(List.of("CONFIRMED", "ASSIGNED", "COMMENTED", "CLOSED"), rows.stream().map(r -> r.get("action")).toList());
        assertEquals("ASSIGNED", rows.get(3).get("from_status"));
        assertEquals("CLOSED", rows.get(3).get("to_status"));
        rows.forEach(r -> System.out.println("FR16-DB " + id + " " + r));

        // Kafka : un evenement par creation et par changement de statut, aucun pour le commentaire
        verify(publisher).publish(any(Incident.class), eq("INCIDENT_CREATED"), eq("operateur-it"));
        verify(publisher).publish(any(Incident.class), eq("INCIDENT_CONFIRMED"), eq("superviseur-it"));
        verify(publisher).publish(any(Incident.class), eq("INCIDENT_ASSIGNED"), eq("superviseur-it"));
        verify(publisher).publish(any(Incident.class), eq("INCIDENT_CLOSED"), eq("superviseur-it"));
        verify(publisher, times(4)).publish(any(), any(), any());

        // Incident clos : nouveau commentaire refuse, rien d'ecrit, rien publie
        reset(publisher);
        call("POST comment on CLOSED", json(post("/api/incidents/" + id + "/comments"),
                "{\"text\":\"apres fermeture\",\"author\":\"technicien-it\"}"), 400);
        assertEquals(4, lignes(id));
        verify(publisher, never()).publish(any(), any(), any());
    }

    @Test
    void commentairesInvalidesDonnent400EtNEcriventRien() throws Exception {
        long id = creerIncident();
        reset(publisher);
        call("POST comment blank text", json(post("/api/incidents/" + id + "/comments"),
                "{\"text\":\"   \",\"author\":\"technicien-it\"}"), 400);
        call("POST comment missing author", json(post("/api/incidents/" + id + "/comments"),
                "{\"text\":\"ok\"}"), 400);
        call("POST comment 2001 chars", json(post("/api/incidents/" + id + "/comments"),
                "{\"text\":\"" + "x".repeat(2001) + "\",\"author\":\"technicien-it\"}"), 400);
        call("POST comment author 256 chars", json(post("/api/incidents/" + id + "/comments"),
                "{\"text\":\"ok\",\"author\":\"" + "a".repeat(256) + "\"}"), 400);
        call("POST comment unreadable JSON", json(post("/api/incidents/" + id + "/comments"), "{pas du json"), 400);
        assertEquals(0, lignes(id));
        assertEquals("OPEN", statutEnBase(id));
        verify(publisher, never()).publish(any(), any(), any());
    }

    @Test
    void incidentInconnuDonne404() throws Exception {
        long inconnu = jdbc.queryForObject("select coalesce(max(id), 0) + 1000000 from incidents", Long.class);
        call("POST comment unknown", json(post("/api/incidents/" + inconnu + "/comments"),
                "{\"text\":\"ok\",\"author\":\"technicien-it\"}"), 404);
        call("GET history unknown", get("/api/incidents/" + inconnu + "/history"), 404);
        assertEquals(0, lignes(inconnu));
    }

    @Test
    void changementsDeStatutRefusesNEcriventPasDHistorique() throws Exception {
        long id = creerIncident();
        reset(publisher);
        call("PUT invalid transition OPEN->RESOLVED", json(put("/api/incidents/" + id),
                "{\"status\":\"RESOLVED\",\"actor\":\"superviseur-it\"}"), 400);
        call("PUT missing actor", json(put("/api/incidents/" + id), "{\"status\":\"CONFIRMED\"}"), 400);
        call("PUT confirm (valid)", json(put("/api/incidents/" + id),
                "{\"status\":\"CONFIRMED\",\"actor\":\"superviseur-it\"}"), 200);
        call("PUT ASSIGNED without assignee", json(put("/api/incidents/" + id),
                "{\"status\":\"ASSIGNED\",\"actor\":\"superviseur-it\"}"), 400);
        assertEquals("CONFIRMED", statutEnBase(id));
        assertEquals(List.of("CONFIRMED"), historiqueEnBase(id).stream().map(r -> r.get("action")).toList(),
                "seule la transition acceptee est dans l'historique");
        verify(publisher, times(1)).publish(any(), any(), any());
    }

    private static List<String> texts(JsonNode array, String field) {
        List<String> out = new ArrayList<>();
        array.forEach(e -> out.add(e.get(field).asString()));
        return out;
    }
}
