package ma.aquaops.incidents.history;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import ma.aquaops.incidents.state.IncidentStatus;
import org.springframework.stereotype.Component;

/**
 * Cree et enregistre les lignes d'historique d'un incident (SCRUM-38 / FR-16).
 * L'heure vient de l'horloge injectee, jamais de l'appelant : les tests utilisent une heure fixe.
 * Ne verifie pas les transitions : c'est le role de IncidentStateMachine, appelee avant.
 * L'auteur doit venir d'une source sure (le jeton, plus tard), pas du corps de la requete.
 */
@Component
public class IncidentHistoryRecorder {

    private final IncidentHistoryRepository repository;
    private final Clock clock;

    public IncidentHistoryRecorder(IncidentHistoryRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Changement de statut : CONFIRMED, ASSIGNED, CLOSED ou STATUS_CHANGED. */
    public IncidentHistoryEntry recordStatusChange(Long incidentId, IncidentAction action,
                                                   IncidentStatus fromStatus, IncidentStatus toStatus,
                                                   String author, String comment) {
        Objects.requireNonNull(action, "action est obligatoire");
        if (action == IncidentAction.COMMENTED) {
            throw new IllegalArgumentException("un commentaire s'enregistre avec recordComment");
        }
        if (fromStatus == null || toStatus == null) {
            throw new IllegalArgumentException("fromStatus et toStatus sont obligatoires pour " + action);
        }
        IncidentStatus expected = expectedTarget(action);
        if (expected != null && toStatus != expected) {
            throw new IllegalArgumentException(action + " doit mener au statut " + expected + ", pas " + toStatus);
        }
        return save(incidentId, action, fromStatus, toStatus, author, optional(comment));
    }

    /** Commentaire seul : le statut ne change pas, le texte est obligatoire. */
    public IncidentHistoryEntry recordComment(Long incidentId, String author, String comment) {
        String text = optional(comment);
        if (text == null) {
            throw new IllegalArgumentException("le commentaire ne peut pas etre vide");
        }
        return save(incidentId, IncidentAction.COMMENTED, null, null, author, text);
    }

    /** Historique d'un incident, du plus ancien au plus recent. */
    public List<IncidentHistoryEntry> historyOf(Long incidentId) {
        return repository.findByIncidentIdOrderByOccurredAtAscIdAsc(
                Objects.requireNonNull(incidentId, "incidentId est obligatoire"));
    }

    private IncidentHistoryEntry save(Long incidentId, IncidentAction action, IncidentStatus fromStatus,
                                      IncidentStatus toStatus, String author, String comment) {
        Objects.requireNonNull(incidentId, "incidentId est obligatoire");
        if (author == null || author.isBlank()) {
            throw new IllegalArgumentException("l'auteur est obligatoire");
        }
        String auteur = author.strip();
        if (auteur.length() > IncidentHistoryEntry.AUTHOR_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "auteur trop long (" + IncidentHistoryEntry.AUTHOR_MAX_LENGTH + " caracteres maximum)");
        }
        return repository.save(new IncidentHistoryEntry(incidentId, action, fromStatus, toStatus,
                auteur, comment, clock.instant()));
    }

    private static IncidentStatus expectedTarget(IncidentAction action) {
        return switch (action) {
            case CONFIRMED -> IncidentStatus.CONFIRMED;
            case ASSIGNED -> IncidentStatus.ASSIGNED;
            case CLOSED -> IncidentStatus.CLOSED;
            case STATUS_CHANGED, COMMENTED -> null;
        };
    }

    /** Texte facultatif : vide devient nul ; trop long est refuse. */
    private static String optional(String comment) {
        if (comment == null || comment.isBlank()) {
            return null;
        }
        String text = comment.strip();
        if (text.length() > IncidentHistoryEntry.COMMENT_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "commentaire trop long (" + IncidentHistoryEntry.COMMENT_MAX_LENGTH + " caracteres maximum)");
        }
        return text;
    }
}
