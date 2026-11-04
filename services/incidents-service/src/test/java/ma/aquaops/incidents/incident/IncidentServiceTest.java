package ma.aquaops.incidents.incident;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;

import ma.aquaops.incidents.history.IncidentAction;
import ma.aquaops.incidents.history.IncidentHistoryRecorder;
import ma.aquaops.incidents.kafka.AnomalyDetectedEvent;
import ma.aquaops.incidents.kafka.IncidentEventPublisher;
import ma.aquaops.incidents.state.IncidentStateMachine;
import ma.aquaops.incidents.state.IncidentStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;

class IncidentServiceTest {

    private IncidentRepository repository;
    private IncidentEventPublisher publisher;
    private IncidentHistoryRecorder history;
    private IncidentService service;

    private final AnomalyDetectedEvent leak = new AnomalyDetectedEvent("a-1", "LEAK_SUSPECTED", 3L, "s-1",
            Instant.parse("2026-11-03T09:00:01Z"), Instant.parse("2026-11-03T09:00:00Z"), null);

    @BeforeEach
    void setUp() {
        repository = mock(IncidentRepository.class);
        publisher = mock(IncidentEventPublisher.class);
        history = mock(IncidentHistoryRecorder.class);
        service = new IncidentService(repository, publisher, history);
        when(repository.save(any(Incident.class))).thenAnswer(call -> {
            Incident i = call.getArgument(0);
            if (i.getId() == null) {
                i.setId(7L);
            }
            return i;
        });
    }

    @Test
    void anomalyCreatesOpenCriticalIncidentAndPublishes() {
        when(repository.existsByAnomalyId("a-1")).thenReturn(false);
        Incident created = service.createFromAnomaly(leak).orElseThrow();
        assertEquals(IncidentStatus.OPEN, created.getStatus());
        assertEquals(Priority.CRITICAL, created.getPriority());
        assertEquals(3L, created.getZoneId());
        assertEquals(leak.measuredAt(), created.getMeasuredAt());
        verify(publisher).publish(created, IncidentEventPublisher.CREATED, null);
    }

    @Test
    void sameAnomalyTwiceCreatesNothing() {
        when(repository.existsByAnomalyId("a-1")).thenReturn(true);
        assertTrue(service.createFromAnomaly(leak).isEmpty());
        verify(repository, never()).save(any(Incident.class));
        verify(publisher, never()).publish(any(), any(), any());
    }

    private Incident existing(IncidentStatus status) {
        Incident i = new Incident();
        i.setId(5L);
        i.setZoneId(1L);
        i.setType("PUMP_FAULT");
        i.setStatus(status);
        i.setPriority(Priority.HIGH);
        when(repository.findById(5L)).thenReturn(Optional.of(i));
        return i;
    }

    @Test
    void validTransitionIsSavedAndPublished() {
        existing(IncidentStatus.OPEN);
        Incident updated = service.update(5L, IncidentStatus.CONFIRMED, null, "alice");
        assertEquals(IncidentStatus.CONFIRMED, updated.getStatus());
        verify(publisher).publish(updated, "INCIDENT_CONFIRMED", "alice");
    }

    @Test
    void invalidTransitionIsRefusedAndNothingIsSaved() {
        existing(IncidentStatus.OPEN);
        assertThrows(InvalidTransitionException.class, () -> service.update(5L, IncidentStatus.RESOLVED, null, null));
        verify(repository, never()).save(any(Incident.class));
        verify(publisher, never()).publish(any(), any(), any());
        verifyNoInteractions(history);
    }

    @Test
    void assignedNeedsAnAssignee() {
        existing(IncidentStatus.CONFIRMED);
        assertThrows(IllegalArgumentException.class,
                () -> service.update(5L, IncidentStatus.ASSIGNED, null, "superviseur-1"));
        verifyNoInteractions(history);
        Incident ok = service.update(5L, IncidentStatus.ASSIGNED, "technicien-1", "superviseur-1");
        assertEquals("technicien-1", ok.getAssignee());
    }

    // ---- SCRUM-38 / FR-16 : historique des changements de statut ----

    /** Toutes les transitions autorisees par IncidentStateMachine, avec l'action attendue. */
    static Stream<Arguments> validTransitions() {
        List<Arguments> all = new ArrayList<>();
        for (IncidentStatus from : IncidentStatus.values()) {
            for (IncidentStatus to : IncidentStatus.values()) {
                if (IncidentStateMachine.isValidTransition(from, to)) {
                    all.add(Arguments.of(from, to, expectedAction(to)));
                }
            }
        }
        return all.stream();
    }

    /** Toutes les paires refusees par IncidentStateMachine (dont un statut vers lui-meme). */
    static Stream<Arguments> invalidTransitions() {
        List<Arguments> all = new ArrayList<>();
        for (IncidentStatus from : IncidentStatus.values()) {
            for (IncidentStatus to : IncidentStatus.values()) {
                if (!IncidentStateMachine.isValidTransition(from, to)) {
                    all.add(Arguments.of(from, to));
                }
            }
        }
        return all.stream();
    }

    private static IncidentAction expectedAction(IncidentStatus to) {
        return switch (to) {
            case CONFIRMED -> IncidentAction.CONFIRMED;
            case ASSIGNED -> IncidentAction.ASSIGNED;
            case CLOSED -> IncidentAction.CLOSED;
            case IN_PROGRESS, RESOLVED -> IncidentAction.STATUS_CHANGED;
            case OPEN -> throw new AssertionError("OPEN n'est jamais une destination");
        };
    }

    @ParameterizedTest(name = "{0} -> {1} enregistre {2}")
    @MethodSource("validTransitions")
    void everyValidTransitionRecordsExactlyOneHistoryEntry(IncidentStatus from, IncidentStatus to,
                                                           IncidentAction action) {
        // Preparer
        existing(from);
        String assignee = to == IncidentStatus.ASSIGNED ? "technicien-1" : null;

        // Agir
        service.update(5L, to, assignee, "superviseur-1");

        // Verifier : une seule ligne, bonne action, anciens et nouveaux statuts, auteur conserve
        verify(history).recordStatusChange(5L, action, from, to, "superviseur-1", null);
        verify(history, never()).recordComment(anyLong(), any(), any());
        verify(publisher).publish(any(Incident.class), eq(IncidentEventPublisher.eventTypeFor(to)), eq("superviseur-1"));
    }

    @Test
    void validTransitionsCoverEveryAction() {
        List<Object> actions = validTransitions().map(a -> a.get()[2]).distinct().toList();
        assertEquals(5, validTransitions().map(a -> a.get()[1]).distinct().count(), "5 statuts atteignables");
        assertTrue(actions.containsAll(List.of(IncidentAction.CONFIRMED, IncidentAction.ASSIGNED,
                IncidentAction.CLOSED, IncidentAction.STATUS_CHANGED)));
    }

    @Test
    void actionForMapsEveryReachableStatus() {
        assertEquals(IncidentAction.CONFIRMED, IncidentService.actionFor(IncidentStatus.CONFIRMED));
        assertEquals(IncidentAction.ASSIGNED, IncidentService.actionFor(IncidentStatus.ASSIGNED));
        assertEquals(IncidentAction.CLOSED, IncidentService.actionFor(IncidentStatus.CLOSED));
        assertEquals(IncidentAction.STATUS_CHANGED, IncidentService.actionFor(IncidentStatus.IN_PROGRESS));
        assertEquals(IncidentAction.STATUS_CHANGED, IncidentService.actionFor(IncidentStatus.RESOLVED));
        assertThrows(IllegalStateException.class, () -> IncidentService.actionFor(IncidentStatus.OPEN));
    }

    @ParameterizedTest(name = "{0} -> {1} refuse")
    @MethodSource("invalidTransitions")
    void invalidTransitionsRecordNothing(IncidentStatus from, IncidentStatus to) {
        // Preparer
        existing(from);

        // Agir et Verifier
        assertThrows(InvalidTransitionException.class,
                () -> service.update(5L, to, "technicien-1", "superviseur-1"));
        verify(repository, never()).save(any(Incident.class));
        verifyNoInteractions(history);
        verify(publisher, never()).publish(any(), any(), any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void statusChangeWithoutActorIsRejected(String actor) {
        // Preparer
        Incident incident = existing(IncidentStatus.OPEN);

        // Agir et Verifier
        assertThrows(IllegalArgumentException.class,
                () -> service.update(5L, IncidentStatus.CONFIRMED, null, actor));
        assertEquals(IncidentStatus.OPEN, incident.getStatus(), "le statut ne doit pas changer");
        verify(repository, never()).save(any(Incident.class));
        verifyNoInteractions(history);
        verify(publisher, never()).publish(any(), any(), any());
    }

    @Test
    void assigneeOnlyChangeStillNeedsNoActorAndRecordsNoStatusHistory() {
        // Preparer : comportement existant conserve (pas de statut : ni evenement, ni historique)
        existing(IncidentStatus.ASSIGNED);

        // Agir
        Incident updated = service.update(5L, null, "technicien-2", null);

        // Verifier
        assertEquals("technicien-2", updated.getAssignee());
        assertEquals(IncidentStatus.ASSIGNED, updated.getStatus());
        verify(repository).save(updated);
        verifyNoInteractions(history);
        verify(publisher, never()).publish(any(), any(), any());
    }

    @Test
    void saveThenFlushThenHistoryThenPublishWithUnchangedArguments() {
        // Preparer
        existing(IncidentStatus.OPEN);

        // Agir
        Incident updated = service.update(5L, IncidentStatus.CONFIRMED, null, "superviseur-1");

        // Verifier : publication identique a avant (incident, type, actor), et faite en dernier
        InOrder ordre = inOrder(repository, history, publisher);
        ordre.verify(repository).save(updated);
        ordre.verify(repository).flush();
        ordre.verify(history).recordStatusChange(5L, IncidentAction.CONFIRMED, IncidentStatus.OPEN,
                IncidentStatus.CONFIRMED, "superviseur-1", null);
        ordre.verify(publisher).publish(updated, "INCIDENT_CONFIRMED", "superviseur-1");
    }

    // ---- SCRUM-38 / FR-16 : commentaires et lecture de l'historique ----

    @ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = IncidentStatus.class, names = "CLOSED",
            mode = org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE)
    void commentIsRecordedWithoutChangingTheIncidentOrPublishing(IncidentStatus status) {
        // Preparer
        Incident incident = existing(status);

        // Agir
        service.addComment(5L, "technicien-1", "vanne fermee");

        // Verifier
        verify(history).recordComment(5L, "technicien-1", "vanne fermee");
        assertEquals(status, incident.getStatus());
        verify(repository, never()).save(any(Incident.class));
        verify(publisher, never()).publish(any(), any(), any());
    }

    @Test
    void commentOnClosedIncidentIsRefused() {
        existing(IncidentStatus.CLOSED);
        assertThrows(IllegalArgumentException.class, () -> service.addComment(5L, "technicien-1", "trop tard"));
        verifyNoInteractions(history);
        verify(publisher, never()).publish(any(), any(), any());
    }

    @Test
    void commentOnUnknownIncidentIsNotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(IncidentNotFoundException.class, () -> service.addComment(99L, "technicien-1", "texte"));
        verifyNoInteractions(history);
    }

    @Test
    void invalidCommentFromRecorderPropagates() {
        existing(IncidentStatus.OPEN);
        when(history.recordComment(5L, "technicien-1", " ")).thenThrow(new IllegalArgumentException("vide"));
        assertThrows(IllegalArgumentException.class, () -> service.addComment(5L, "technicien-1", " "));
        verify(publisher, never()).publish(any(), any(), any());
    }

    @Test
    void historyOfAnExistingIncidentComesFromTheRecorder() {
        existing(IncidentStatus.OPEN);
        List<ma.aquaops.incidents.history.IncidentHistoryEntry> attendu = List.of();
        when(history.historyOf(5L)).thenReturn(attendu);
        assertTrue(attendu == service.history(5L));
    }

    @Test
    void historyOfUnknownIncidentIsNotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(IncidentNotFoundException.class, () -> service.history(99L));
        verifyNoInteractions(history);
    }

    @Test
    void historyFailurePropagatesAndNothingIsPublished() {
        // Preparer : l'ecriture de l'historique echoue (la transaction sera annulee par Spring)
        existing(IncidentStatus.OPEN);
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("base indisponible"))
                .when(history).recordStatusChange(anyLong(), any(), any(), any(), any(), isNull());

        // Agir et Verifier
        assertThrows(org.springframework.dao.DataAccessResourceFailureException.class,
                () -> service.update(5L, IncidentStatus.CONFIRMED, null, "superviseur-1"));
        verify(publisher, never()).publish(any(), any(), any());
    }

    @Test
    void unknownIncidentIsNotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(IncidentNotFoundException.class, () -> service.get(99L));
    }

    @Test
    void manualCreationNeedsZoneAndType() {
        assertThrows(IllegalArgumentException.class, () -> service.createManual(null, "X", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> service.createManual(1L, " ", null, null, null));
        Incident i = service.createManual(1L, "PUMP_FAULT", null, "test", "bob");
        assertEquals(Priority.HIGH, i.getPriority());
        verify(publisher).publish(eq(i), eq(IncidentEventPublisher.CREATED), eq("bob"));
    }
}
