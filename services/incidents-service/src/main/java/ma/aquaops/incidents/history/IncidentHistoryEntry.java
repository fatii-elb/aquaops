package ma.aquaops.incidents.history;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import ma.aquaops.incidents.state.IncidentStatus;

/**
 * Une ligne de l'historique d'un incident (SCRUM-38 / FR-16) : qui a fait quoi, et quand.
 * Ecrite une seule fois, jamais modifiee : pas de setter. Creee par {@link IncidentHistoryRecorder}.
 */
@Entity
@Table(name = "incident_history",
        indexes = @Index(name = "idx_incident_history_incident_time", columnList = "incident_id, occurred_at"))
public class IncidentHistoryEntry {

    /** Taille maximale d'un commentaire, en caracteres. */
    public static final int COMMENT_MAX_LENGTH = 2000;

    /** Taille maximale de l'auteur : longueur JPA par defaut de la colonne author (varchar(255)). */
    public static final int AUTHOR_MAX_LENGTH = 255;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "incident_id", nullable = false, updatable = false)
    private Long incidentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, updatable = false)
    private IncidentAction action;

    /** Statut avant l'action ; nul pour un commentaire. */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", updatable = false)
    private IncidentStatus fromStatus;

    /** Statut apres l'action ; nul pour un commentaire. */
    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", updatable = false)
    private IncidentStatus toStatus;

    /** Nom d'utilisateur de l'auteur, jamais vide. */
    @Column(name = "author", nullable = false, updatable = false)
    private String author;

    @Column(name = "comment", length = COMMENT_MAX_LENGTH, updatable = false)
    private String comment;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    /** Pour JPA uniquement. */
    protected IncidentHistoryEntry() {
    }

    IncidentHistoryEntry(Long incidentId, IncidentAction action, IncidentStatus fromStatus,
                         IncidentStatus toStatus, String author, String comment, Instant occurredAt) {
        this.incidentId = incidentId;
        this.action = action;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.author = author;
        this.comment = comment;
        this.occurredAt = occurredAt;
    }

    public Long getId() { return id; }
    public Long getIncidentId() { return incidentId; }
    public IncidentAction getAction() { return action; }
    public IncidentStatus getFromStatus() { return fromStatus; }
    public IncidentStatus getToStatus() { return toStatus; }
    public String getAuthor() { return author; }
    public String getComment() { return comment; }
    public Instant getOccurredAt() { return occurredAt; }
}
