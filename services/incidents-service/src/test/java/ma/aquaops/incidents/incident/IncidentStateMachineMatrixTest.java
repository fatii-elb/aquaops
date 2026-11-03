package ma.aquaops.incidents.incident;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ma.aquaops.incidents.state.IncidentStateMachine;
import ma.aquaops.incidents.state.IncidentStatus;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** SCRUM-39 : les 36 couples (de, vers) ; seules les transitions du tableau FR-17 sont acceptees. */
class IncidentStateMachineMatrixTest {

    private static final Map<IncidentStatus, Set<IncidentStatus>> EXPECTED = new EnumMap<>(IncidentStatus.class);

    static {
        for (IncidentStatus s : IncidentStatus.values()) {
            EXPECTED.put(s, EnumSet.noneOf(IncidentStatus.class));
        }
        EXPECTED.get(IncidentStatus.OPEN).addAll(Set.of(IncidentStatus.CONFIRMED, IncidentStatus.CLOSED));
        EXPECTED.get(IncidentStatus.CONFIRMED).addAll(Set.of(IncidentStatus.ASSIGNED, IncidentStatus.CLOSED));
        EXPECTED.get(IncidentStatus.ASSIGNED).addAll(Set.of(IncidentStatus.IN_PROGRESS, IncidentStatus.CLOSED));
        EXPECTED.get(IncidentStatus.IN_PROGRESS).addAll(Set.of(IncidentStatus.RESOLVED, IncidentStatus.CLOSED));
        EXPECTED.get(IncidentStatus.RESOLVED).add(IncidentStatus.CLOSED);
    }

    static Stream<Arguments> allPairs() {
        return Stream.of(IncidentStatus.values()).flatMap(from ->
                Stream.of(IncidentStatus.values()).map(to ->
                        Arguments.of(from, to, EXPECTED.get(from).contains(to))));
    }

    @ParameterizedTest(name = "{0} -> {1} : autorise = {2}")
    @MethodSource("allPairs")
    void everyPair(IncidentStatus from, IncidentStatus to, boolean allowed) {
        assertEquals(allowed, IncidentStateMachine.isValidTransition(from, to));
    }

    @org.junit.jupiter.api.Test
    void matrixHasSixStatuses() {
        assertEquals(6, IncidentStatus.values().length);
    }
}
