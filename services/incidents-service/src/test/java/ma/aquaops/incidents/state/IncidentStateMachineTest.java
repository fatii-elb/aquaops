package ma.aquaops.incidents.state;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IncidentStateMachineTest {

    @Test
    void testValidTransitions() {
        assertTrue(IncidentStateMachine.isValidTransition(IncidentStatus.OPEN, IncidentStatus.CONFIRMED));
        assertTrue(IncidentStateMachine.isValidTransition(IncidentStatus.CONFIRMED, IncidentStatus.ASSIGNED));
        assertTrue(IncidentStateMachine.isValidTransition(IncidentStatus.ASSIGNED, IncidentStatus.IN_PROGRESS));
        assertTrue(IncidentStateMachine.isValidTransition(IncidentStatus.IN_PROGRESS, IncidentStatus.RESOLVED));
        assertTrue(IncidentStateMachine.isValidTransition(IncidentStatus.RESOLVED, IncidentStatus.CLOSED));
    }

    @Test
    void testInvalidTransitions() {
        assertFalse(IncidentStateMachine.isValidTransition(IncidentStatus.OPEN, IncidentStatus.RESOLVED));
        assertFalse(IncidentStateMachine.isValidTransition(IncidentStatus.CONFIRMED, IncidentStatus.OPEN));
        assertFalse(IncidentStateMachine.isValidTransition(IncidentStatus.CLOSED, IncidentStatus.OPEN));
        assertFalse(IncidentStateMachine.isValidTransition(IncidentStatus.RESOLVED, IncidentStatus.ASSIGNED));
    }

    @Test
    void testNullTransitions() {
        assertFalse(IncidentStateMachine.isValidTransition(null, IncidentStatus.OPEN));
        assertFalse(IncidentStateMachine.isValidTransition(IncidentStatus.OPEN, null));
    }

    @Test
    void testNextThrowsOnInvalidTransition() {
        assertThrows(IllegalStateException.class, () -> {
            IncidentStateMachine.next(IncidentStatus.OPEN, IncidentStatus.RESOLVED);
        });
    }

    @Test
    void testNextReturnsTargetOnValidTransition() {
        IncidentStatus result = IncidentStateMachine.next(IncidentStatus.OPEN, IncidentStatus.CONFIRMED);
        assertEquals(IncidentStatus.CONFIRMED, result);
    }
}