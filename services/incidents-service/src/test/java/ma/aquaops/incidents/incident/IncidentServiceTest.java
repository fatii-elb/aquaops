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

import ma.aquaops.incidents.kafka.AnomalyDetectedEvent;
import ma.aquaops.incidents.kafka.IncidentEventPublisher;
import ma.aquaops.incidents.state.IncidentStatus;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IncidentServiceTest {

    private IncidentRepository repository;
    private IncidentEventPublisher publisher;
    private IncidentService service;

    private final AnomalyDetectedEvent leak = new AnomalyDetectedEvent("a-1", "LEAK_SUSPECTED", 3L, "s-1",
            Instant.parse("2026-11-03T09:00:01Z"), Instant.parse("2026-11-03T09:00:00Z"), null);

    @BeforeEach
    void setUp() {
        repository = mock(IncidentRepository.class);
        publisher = mock(IncidentEventPublisher.class);
        service = new IncidentService(repository, publisher);
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
    }

    @Test
    void assignedNeedsAnAssignee() {
        existing(IncidentStatus.CONFIRMED);
        assertThrows(IllegalArgumentException.class,
                () -> service.update(5L, IncidentStatus.ASSIGNED, null, null));
        Incident ok = service.update(5L, IncidentStatus.ASSIGNED, "technicien-1", null);
        assertEquals("technicien-1", ok.getAssignee());
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
