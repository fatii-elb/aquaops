package ma.aquaops.incidents.incident;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
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
 * SCRUM-38 : reponse HTTP de PUT /api/incidents/{id} selon l'actor (sans Spring, sans base).
 * Vrais controleur, ApiExceptionHandler, IncidentService et IncidentHistoryRecorder ;
 * seuls les depots, le publieur Kafka et l'horloge sont remplaces.
 */
class IncidentControllerActorTests {

    private IncidentRepository incidents;
    private IncidentHistoryRepository historique;
    private IncidentEventPublisher publisher;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        incidents = mock(IncidentRepository.class);
        historique = mock(IncidentHistoryRepository.class);
        publisher = mock(IncidentEventPublisher.class);
        when(incidents.save(any(Incident.class))).thenAnswer(call -> call.getArgument(0));
        when(historique.save(any(IncidentHistoryEntry.class))).thenAnswer(call -> call.getArgument(0));
        IncidentHistoryRecorder recorder = new IncidentHistoryRecorder(historique,
                Clock.fixed(Instant.parse("2026-11-04T10:00:00Z"), ZoneOffset.UTC));
        IncidentService service = new IncidentService(incidents, publisher, recorder);
        mvc = MockMvcBuilders.standaloneSetup(new IncidentController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

        Incident ouvert = new Incident();
        ouvert.setId(5L);
        ouvert.setZoneId(1L);
        ouvert.setType("PUMP_FAULT");
        ouvert.setStatus(IncidentStatus.OPEN);
        ouvert.setPriority(Priority.HIGH);
        when(incidents.findById(5L)).thenReturn(Optional.of(ouvert));
    }

    private static String corps(String actor) {
        return "{\"status\":\"CONFIRMED\",\"actor\":\"" + actor + "\"}";
    }

    @Test
    void actorDe256CaracteresDonne400EtPas500() throws Exception {
        // Agir et Verifier
        mvc.perform(put("/api/incidents/5").contentType(MediaType.APPLICATION_JSON).content(corps("a".repeat(256))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("auteur trop long (255 caracteres maximum)"));
        verify(historique, never()).save(any());
        verify(publisher, never()).publish(any(), any(), any());
    }

    @Test
    void actorDe255CaracteresEstAccepte() throws Exception {
        mvc.perform(put("/api/incidents/5").contentType(MediaType.APPLICATION_JSON).content(corps("a".repeat(255))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        verify(historique).save(any(IncidentHistoryEntry.class));
    }

    @Test
    void actorVideDonne400AvecLeFormatDErreurHabituel() throws Exception {
        mvc.perform(put("/api/incidents/5").contentType(MediaType.APPLICATION_JSON).content(corps("   ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("actor est obligatoire pour changer le statut"));
        verify(incidents, never()).save(any());
        verify(publisher, never()).publish(any(), any(), any());
    }
}
