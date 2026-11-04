package ma.aquaops.incidents.incident;

import ma.aquaops.incidents.state.IncidentStatus;

public class InvalidTransitionException extends RuntimeException {
    public InvalidTransitionException(IncidentStatus from, IncidentStatus to) {
        super("Transition interdite : " + from + " -> " + to);
    }
}
