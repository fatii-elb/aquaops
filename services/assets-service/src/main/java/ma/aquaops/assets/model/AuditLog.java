package ma.aquaops.assets.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Une ligne du journal d'audit (SCRUM-46, FR-24) : qui, quand, quoi.
 * Les lignes ne sont jamais modifiees ni supprimees par l'API.
 */
@Entity
@Table(name = "audit_log",
        indexes = {
            @Index(name = "idx_audit_occurred_at", columnList = "occurred_at"),
            @Index(name = "idx_audit_actor", columnList = "actor"),
            @Index(name = "idx_audit_action", columnList = "action")
        })
public class AuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** Quand : horodatage de l'action, UTC. */
    @Column(name = "occurred_at", nullable = false)
    public Instant occurredAt;

    /** Qui : nom d'utilisateur Keycloak (preferred_username), ou "system" si inconnu. */
    @Column(nullable = false)
    public String actor;

    /** Role principal de l'auteur au moment de l'action (peut etre vide). */
    @Column(name = "actor_role")
    public String actorRole;

    /** Quoi (1) : le type d'action. CREATE, UPDATE, DELETE, ASSIGN, STATUS_CHANGE, ou un eventType d'incident. */
    @Column(nullable = false)
    public String action;

    /** Quoi (2) : le type d'objet touche (ZONE, PIPE, TANK, PUMP, SENSOR, INTERVENTION, INCIDENT...). */
    @Column(name = "entity_type", nullable = false)
    public String entityType;

    /** Identifiant de l'objet touche, en texte. */
    @Column(name = "entity_id")
    public String entityId;

    /** Quoi (3) : detail lisible. */
    @Column(length = 1000)
    public String details;

    /** Origine : "assets-service" ou "incident-events" (consommateur Kafka). */
    @Column(nullable = false)
    public String source;
}
