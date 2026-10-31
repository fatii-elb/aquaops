package ma.aquaops.assets.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.Instant;

/**
 * Intervention de maintenance liee a un incident (SCRUM-41, FR-19).
 * Le lien est l'identifiant de l'incident (simple Long, comme zoneId : pas de relation JPA
 * car les incidents sont dans un autre service).
 */
@Entity
@Table(name = "interventions",
        indexes = @Index(name = "idx_interventions_incident", columnList = "incident_id"))
public class Intervention {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @NotNull
    @Column(name = "incident_id", nullable = false)
    public Long incidentId;

    /** Equipe affectee. Vide tant que l'intervention n'est pas affectee. */
    public String team;

    /** Technicien assigne : son nom d'utilisateur Keycloak (preferred_username). Peut rester vide. */
    @Column(name = "assigned_to")
    public String assignedTo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public InterventionStatus status;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;
}
