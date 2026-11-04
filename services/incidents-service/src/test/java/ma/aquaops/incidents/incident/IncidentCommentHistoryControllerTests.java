package ma.aquaops.incidents.incident;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import ma.aquaops.incidents.history.IncidentAction;
import ma.aquaops.incidents.history.IncidentHistoryEntry;
import ma.aquaops.incidents.history.IncidentHistoryRecorder;
import ma.aquaops.incidents.history.IncidentHistoryRepository;
import ma.aquaops.incidents.kafka.IncidentEventPublisher;
import ma.aquaops.incidents.state.IncidentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * SCRUM-38 / FR-16 : POST /api/incidents/{id}/comments et GET /api/incidents/{id}/history.
 * Sans Spring ni base : vrais controleur, ApiExceptionHandler, IncidentService et
 * IncidentHistoryRecorder ; seuls les depots, le publieur Kafka et l'horloge sont remplaces.
 */
class IncidentCommentHistoryControllerTests {

    private static final Instant T0 = Instant.parse("2026-11-04T10:00:00Z");

    private IncidentRepository incidents;
    private IncidentHistoryRepository historique;
    private IncidentEventPublisher publisher;
    private HorlogeDeTest horloge;
    private IncidentHistoryRecorder recorder;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        incidents = mock(IncidentRepository.class);
        historique = mock(IncidentHistoryRepository.class);
        publisher = mock(IncidentEventPublisher.class);
        when(incidents.save(any(Incident.class))).thenAnswer(call -> call.getArgument(0));
        when(historique.save(any(IncidentHistoryEntry.class))).thenAnswer(call -> call.getArgument(0));
        horloge = new HorlogeDeTest(T0);
        recorder = new IncidentHistoryRecorder(historique, horloge);
        IncidentService service = new IncidentService(incidents, publisher, recorder);
        mvc = MockMvcBuilders.standaloneSetup(new IncidentController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
        when(incidents.findById(99L)).thenReturn(Optional.empty());
    }

    private Incident incident(IncidentStatus status) {
        Incident i = new Incident();
        i.setId(5L);
        i.setZoneId(1L);
        i.setType("PUMP_FAULT");
        i.setStatus(status);
        i.setPriority(Priority.HIGH);
        when(incidents.findById(5L)).thenReturn(Optional.of(i));
        return i;
    }

    private static String commentaire(String text, String author) {
        return "{\"text\":" + json(text) + ",\"author\":" + json(author) + "}";
    }

    private static String json(String s) {
        return s == null ? "null" : "\"" + s + "\"";
    }

    // ---- POST /comments ----

    @Test
    void commentaireValideDonne201EtUneLigneCommented() throws Exception {
        // Preparer
        Incident i = incident(IncidentStatus.ASSIGNED);

        // Agir et Verifier
        mvc.perform(post("/api/incidents/5/comments").contentType(MediaType.APPLICATION_JSON)
                        .content(commentaire("  vanne fermee  ", " technicien-1 ")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.incidentId").value(5))
                .andExpect(jsonPath("$.action").value("COMMENTED"))
                .andExpect(jsonPath("$.fromStatus").value(nullValue()))
                .andExpect(jsonPath("$.toStatus").value(nullValue()))
                .andExpect(jsonPath("$.author").value("technicien-1"))
                .andExpect(jsonPath("$.comment").value("vanne fermee"))
                .andExpect(jsonPath("$.occurredAt").value("2026-11-04T10:00:00Z"));
        verify(historique).save(any(IncidentHistoryEntry.class));
        assertStatutInchange(i, IncidentStatus.ASSIGNED);
        verify(publisher, never()).publish(any(), any(), any());
    }

    private void assertStatutInchange(Incident i, IncidentStatus attendu) {
        org.junit.jupiter.api.Assertions.assertEquals(attendu, i.getStatus());
        verify(incidents, never()).save(any(Incident.class));
    }

    @Test
    void commentaireSansTexteDonne400() throws Exception {
        incident(IncidentStatus.OPEN);
        for (String texte : new String[] {null, "", "   "}) {
            mvc.perform(post("/api/incidents/5/comments").contentType(MediaType.APPLICATION_JSON)
                            .content(commentaire(texte, "technicien-1")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("le commentaire ne peut pas etre vide"));
        }
        verify(historique, never()).save(any());
    }

    @Test
    void commentaireDe2001CaracteresDonne400EtDe2000EstAccepte() throws Exception {
        incident(IncidentStatus.OPEN);
        mvc.perform(post("/api/incidents/5/comments").contentType(MediaType.APPLICATION_JSON)
                        .content(commentaire("x".repeat(2001), "technicien-1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("commentaire trop long (2000 caracteres maximum)"));
        verify(historique, never()).save(any());
        mvc.perform(post("/api/incidents/5/comments").contentType(MediaType.APPLICATION_JSON)
                        .content(commentaire("x".repeat(2000), "technicien-1")))
                .andExpect(status().isCreated());
    }

    @Test
    void auteurVideOuTropLongDonne400() throws Exception {
        incident(IncidentStatus.OPEN);
        for (String auteur : new String[] {null, "", "   "}) {
            mvc.perform(post("/api/incidents/5/comments").contentType(MediaType.APPLICATION_JSON)
                            .content(commentaire("texte", auteur)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("l'auteur est obligatoire"));
        }
        mvc.perform(post("/api/incidents/5/comments").contentType(MediaType.APPLICATION_JSON)
                        .content(commentaire("texte", "a".repeat(256))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("auteur trop long (255 caracteres maximum)"));
        verify(historique, never()).save(any());
    }

    @Test
    void corpsIllisibleDonne400AvecLeMessageHabituel() throws Exception {
        incident(IncidentStatus.OPEN);
        mvc.perform(post("/api/incidents/5/comments").contentType(MediaType.APPLICATION_JSON).content("{pas du json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Requete invalide (corps JSON ou parametre incorrect)"));
    }

    @Test
    void commentaireSurIncidentInconnuDonne404() throws Exception {
        mvc.perform(post("/api/incidents/99/comments").contentType(MediaType.APPLICATION_JSON)
                        .content(commentaire("texte", "technicien-1")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").exists());
        verify(historique, never()).save(any());
    }

    @Test
    void commentaireSurIncidentClosDonne400() throws Exception {
        incident(IncidentStatus.CLOSED);
        mvc.perform(post("/api/incidents/5/comments").contentType(MediaType.APPLICATION_JSON)
                        .content(commentaire("trop tard", "technicien-1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("incident 5 clos : commentaire impossible"));
        verify(historique, never()).save(any());
    }

    // ---- GET /history ----

    @Test
    void historiqueRenduDansLOrdreDuDepot() throws Exception {
        // Preparer : trois lignes creees par le vrai recorder, a des heures croissantes
        incident(IncidentStatus.ASSIGNED);
        IncidentHistoryEntry confirme = recorder.recordStatusChange(5L, IncidentAction.CONFIRMED,
                IncidentStatus.OPEN, IncidentStatus.CONFIRMED, "superviseur-1", null);
        horloge.avancer(Duration.ofMinutes(1));
        IncidentHistoryEntry assigne = recorder.recordStatusChange(5L, IncidentAction.ASSIGNED,
                IncidentStatus.CONFIRMED, IncidentStatus.ASSIGNED, "superviseur-1", null);
        horloge.avancer(Duration.ofMinutes(1));
        IncidentHistoryEntry commente = recorder.recordComment(5L, "technicien-1", "sur place");
        // le depot (requete ORDER BY occurred_at, id, prouvee contre PostgreSQL) rend cet ordre
        when(historique.findByIncidentIdOrderByOccurredAtAscIdAsc(5L)).thenReturn(List.of(confirme, assigne, commente));

        // Agir et Verifier : ordre conserve, et seuls les champs du DTO sont exposes
        mvc.perform(get("/api/incidents/5/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].action").value("CONFIRMED"))
                .andExpect(jsonPath("$[0].fromStatus").value("OPEN"))
                .andExpect(jsonPath("$[0].toStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$[0].occurredAt").value("2026-11-04T10:00:00Z"))
                .andExpect(jsonPath("$[1].action").value("ASSIGNED"))
                .andExpect(jsonPath("$[1].occurredAt").value("2026-11-04T10:01:00Z"))
                .andExpect(jsonPath("$[2].action").value("COMMENTED"))
                .andExpect(jsonPath("$[2].author").value("technicien-1"))
                .andExpect(jsonPath("$[2].comment").value("sur place"))
                .andExpect(jsonPath("$[2].occurredAt").value("2026-11-04T10:02:00Z"))
                .andExpect(jsonPath("$[0].*", hasSize(8)));
    }

    @Test
    void historiqueVidePourUnIncidentSansAction() throws Exception {
        incident(IncidentStatus.OPEN);
        when(historique.findByIncidentIdOrderByOccurredAtAscIdAsc(5L)).thenReturn(List.of());
        mvc.perform(get("/api/incidents/5/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void historiqueDUnIncidentInconnuDonne404() throws Exception {
        mvc.perform(get("/api/incidents/99/history"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").exists());
        verify(historique, never()).findByIncidentIdOrderByOccurredAtAscIdAsc(any());
    }

    @Test
    void lesEndpointsExistantsNeChangentPas() throws Exception {
        incident(IncidentStatus.OPEN);
        mvc.perform(get("/api/incidents/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));
        mvc.perform(get("/api/incidents/99"))
                .andExpect(status().isNotFound());
    }

    /** Horloge fixe que le test peut avancer. */
    private static final class HorlogeDeTest extends Clock {
        private Instant maintenant;

        HorlogeDeTest(Instant depart) {
            this.maintenant = depart;
        }

        void avancer(Duration duree) {
            maintenant = maintenant.plus(duree);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return maintenant;
        }
    }
}
