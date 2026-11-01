package ma.aquaops.incidents.state;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public class IncidentStateMachine {

    private static final Map<IncidentStatus, Set<IncidentStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(IncidentStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(IncidentStatus.OPEN, EnumSet.of(IncidentStatus.CONFIRMED, IncidentStatus.CLOSED));
        ALLOWED_TRANSITIONS.put(IncidentStatus.CONFIRMED, EnumSet.of(IncidentStatus.ASSIGNED, IncidentStatus.CLOSED));
        ALLOWED_TRANSITIONS.put(IncidentStatus.ASSIGNED, EnumSet.of(IncidentStatus.IN_PROGRESS, IncidentStatus.CLOSED));
        ALLOWED_TRANSITIONS.put(IncidentStatus.IN_PROGRESS, EnumSet.of(IncidentStatus.RESOLVED, IncidentStatus.CLOSED));
        ALLOWED_TRANSITIONS.put(IncidentStatus.RESOLVED, EnumSet.of(IncidentStatus.CLOSED));
        ALLOWED_TRANSITIONS.put(IncidentStatus.CLOSED, EnumSet.noneOf(IncidentStatus.class));
    }

    public static boolean isValidTransition(IncidentStatus from, IncidentStatus to) {
        if (from == null || to == null) {
            return false;
        }
        return ALLOWED_TRANSITIONS.getOrDefault(from, EnumSet.noneOf(IncidentStatus.class)).contains(to);
    }

    public static IncidentStatus next(IncidentStatus current, IncidentStatus target) {
        if (!isValidTransition(current, target)) {
            throw new IllegalStateException("Transition invalide de " + current + " vers " + target);
        }
        return target;
    }
}