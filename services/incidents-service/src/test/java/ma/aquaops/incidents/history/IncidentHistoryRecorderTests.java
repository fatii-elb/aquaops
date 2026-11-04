package ma.aquaops.incidents.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import ma.aquaops.incidents.state.IncidentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class IncidentHistoryRecorderTests {

    private static final Instant T0 = Instant.parse("2026-11-04T10:00:00Z");

    private IncidentHistoryRepository repository;
    private HorlogeDeTest horloge;
    private IncidentHistoryRecorder recorder;

    @BeforeEach
    void setUp() {
        repository = mock(IncidentHistoryRepository.class);
        when(repository.save(any(IncidentHistoryEntry.class))).thenAnswer(call -> call.getArgument(0));
        horloge = new HorlogeDeTest(T0);
        recorder = new IncidentHistoryRecorder(repository, horloge);
    }

    @Test
    void enregistreUnChangementDeStatutAvecAuteurEtHeureDeLHorloge() {
        // Agir
        IncidentHistoryEntry entry = recorder.recordStatusChange(
                5L, IncidentAction.CONFIRMED, IncidentStatus.OPEN, IncidentStatus.CONFIRMED, "superviseur1", null);

        // Verifier
        assertEquals(5L, entry.getIncidentId());
        assertEquals(IncidentAction.CONFIRMED, entry.getAction());
        assertEquals(IncidentStatus.OPEN, entry.getFromStatus());
        assertEquals(IncidentStatus.CONFIRMED, entry.getToStatus());
        assertEquals("superviseur1", entry.getAuthor());
        assertNull(entry.getComment());
        assertEquals(T0, entry.getOccurredAt());
        verify(repository).save(entry);
    }

    @Test
    void chaqueLigneRecoitLHeureDuMomentOuElleEstEnregistree() {
        // Preparer
        IncidentHistoryEntry premiere = recorder.recordStatusChange(
                5L, IncidentAction.CONFIRMED, IncidentStatus.OPEN, IncidentStatus.CONFIRMED, "superviseur1", null);
        horloge.avancer(Duration.ofMinutes(3));

        // Agir
        IncidentHistoryEntry seconde = recorder.recordComment(5L, "technicien1", "vanne fermee");

        // Verifier
        assertEquals(T0, premiere.getOccurredAt());
        assertEquals(T0.plus(Duration.ofMinutes(3)), seconde.getOccurredAt());
    }

    @Test
    void unCommentaireNaPasDeStatutEtGardeSonTexteNettoye() {
        // Agir
        IncidentHistoryEntry entry = recorder.recordComment(5L, "technicien1", "  vanne fermee  ");

        // Verifier
        assertEquals(IncidentAction.COMMENTED, entry.getAction());
        assertNull(entry.getFromStatus());
        assertNull(entry.getToStatus());
        assertEquals("vanne fermee", entry.getComment());
        assertEquals(T0, entry.getOccurredAt());
    }

    @Test
    void lAuteurEstNettoyeDesEspaces() {
        IncidentHistoryEntry entry = recorder.recordComment(5L, "  technicien1 ", "ok");
        assertEquals("technicien1", entry.getAuthor());
    }

    @Test
    void unCommentaireFacultatifVideDevientNul() {
        IncidentHistoryEntry entry = recorder.recordStatusChange(
                5L, IncidentAction.CLOSED, IncidentStatus.RESOLVED, IncidentStatus.CLOSED, "superviseur1", "   ");
        assertNull(entry.getComment());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void refuseUnAuteurVide(String author) {
        assertThrows(IllegalArgumentException.class, () -> recorder.recordStatusChange(
                5L, IncidentAction.CONFIRMED, IncidentStatus.OPEN, IncidentStatus.CONFIRMED, author, null));
        assertThrows(IllegalArgumentException.class, () -> recorder.recordComment(5L, author, "texte"));
        verify(repository, never()).save(any());
    }

    @Test
    void acceptUnAuteurDe255Caracteres() {
        String max = "a".repeat(IncidentHistoryEntry.AUTHOR_MAX_LENGTH);
        assertEquals(255, max.length());
        assertEquals(max, recorder.recordComment(5L, max, "ok").getAuthor());
    }

    @Test
    void refuseUnAuteurDe256Caracteres() {
        String tropLong = "a".repeat(IncidentHistoryEntry.AUTHOR_MAX_LENGTH + 1);
        assertThrows(IllegalArgumentException.class, () -> recorder.recordComment(5L, tropLong, "ok"));
        assertThrows(IllegalArgumentException.class, () -> recorder.recordStatusChange(
                5L, IncidentAction.CONFIRMED, IncidentStatus.OPEN, IncidentStatus.CONFIRMED, tropLong, null));
        verify(repository, never()).save(any());
    }

    @Test
    void lesEspacesAutourNeComptentPasDansLaLongueur() {
        // 255 caracteres utiles entoures d'espaces : accepte, et enregistre sans les espaces
        String max = "a".repeat(IncidentHistoryEntry.AUTHOR_MAX_LENGTH);
        assertEquals(max, recorder.recordComment(5L, "  " + max + "\t ", "ok").getAuthor());
        // 256 caracteres utiles : refuse, meme entoures d'espaces
        String tropLong = " " + "a".repeat(IncidentHistoryEntry.AUTHOR_MAX_LENGTH + 1) + " ";
        assertThrows(IllegalArgumentException.class, () -> recorder.recordComment(5L, tropLong, "ok"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void refuseUnCommentaireSansTexte(String comment) {
        assertThrows(IllegalArgumentException.class, () -> recorder.recordComment(5L, "technicien1", comment));
        verify(repository, never()).save(any());
    }

    @Test
    void refuseUnCommentaireTropLong() {
        String tropLong = "x".repeat(IncidentHistoryEntry.COMMENT_MAX_LENGTH + 1);
        assertThrows(IllegalArgumentException.class, () -> recorder.recordComment(5L, "technicien1", tropLong));
        verify(repository, never()).save(any());
    }

    @Test
    void acceptUnCommentaireDeTailleMaximale() {
        String max = "x".repeat(IncidentHistoryEntry.COMMENT_MAX_LENGTH);
        assertEquals(max, recorder.recordComment(5L, "technicien1", max).getComment());
    }

    @Test
    void refuseUnIncidentOuUneActionManquants() {
        assertThrows(NullPointerException.class, () -> recorder.recordComment(null, "technicien1", "texte"));
        assertThrows(NullPointerException.class, () -> recorder.recordStatusChange(
                5L, null, IncidentStatus.OPEN, IncidentStatus.CONFIRMED, "superviseur1", null));
        verify(repository, never()).save(any());
    }

    @Test
    void unChangementDeStatutDemandeLesDeuxStatuts() {
        assertThrows(IllegalArgumentException.class, () -> recorder.recordStatusChange(
                5L, IncidentAction.STATUS_CHANGED, null, IncidentStatus.IN_PROGRESS, "technicien1", null));
        assertThrows(IllegalArgumentException.class, () -> recorder.recordStatusChange(
                5L, IncidentAction.STATUS_CHANGED, IncidentStatus.ASSIGNED, null, "technicien1", null));
        verify(repository, never()).save(any());
    }

    @Test
    void lActionDoitCorrespondreAuStatutObtenu() {
        assertThrows(IllegalArgumentException.class, () -> recorder.recordStatusChange(
                5L, IncidentAction.CONFIRMED, IncidentStatus.OPEN, IncidentStatus.CLOSED, "superviseur1", null));
        assertThrows(IllegalArgumentException.class, () -> recorder.recordStatusChange(
                5L, IncidentAction.CLOSED, IncidentStatus.OPEN, IncidentStatus.CONFIRMED, "superviseur1", null));
        assertThrows(IllegalArgumentException.class, () -> recorder.recordStatusChange(
                5L, IncidentAction.ASSIGNED, IncidentStatus.CONFIRMED, IncidentStatus.IN_PROGRESS, "superviseur1", null));
        verify(repository, never()).save(any());
    }

    @Test
    void statusChangedAccepteLesAutresStatuts() {
        IncidentHistoryEntry entry = recorder.recordStatusChange(
                5L, IncidentAction.STATUS_CHANGED, IncidentStatus.ASSIGNED, IncidentStatus.IN_PROGRESS, "technicien1", null);
        assertEquals(IncidentStatus.IN_PROGRESS, entry.getToStatus());
    }

    @Test
    void unCommentaireNePassePasParRecordStatusChange() {
        assertThrows(IllegalArgumentException.class, () -> recorder.recordStatusChange(
                5L, IncidentAction.COMMENTED, IncidentStatus.OPEN, IncidentStatus.OPEN, "technicien1", "texte"));
        verify(repository, never()).save(any());
    }

    @Test
    void lHistoriqueEstLuDansLOrdreChronologiqueParIncident() {
        // Preparer
        List<IncidentHistoryEntry> attendu = List.of();
        when(repository.findByIncidentIdOrderByOccurredAtAscIdAsc(5L)).thenReturn(attendu);

        // Agir et Verifier
        assertSame(attendu, recorder.historyOf(5L));
        verify(repository).findByIncidentIdOrderByOccurredAtAscIdAsc(5L);
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
