package ma.aquaops.incidents.history;

import java.time.Instant;
import ma.aquaops.incidents.state.IncidentStatus;

/**
 * Ligne d'historique renvoyee par l'API (SCRUM-38 / FR-16), sans exposer l'entite JPA.
 * author est le nom fourni par le client : ce n'est PAS une identite authentifiee
 * tant que l'authentification par jeton n'est pas en place dans incidents-service.
 */
public record IncidentHistoryResponse(
        Long id,
        Long incidentId,
        IncidentAction action,
        IncidentStatus fromStatus,
        IncidentStatus toStatus,
        String author,
        String comment,
        Instant occurredAt) {

    public static IncidentHistoryResponse from(IncidentHistoryEntry e) {
        return new IncidentHistoryResponse(e.getId(), e.getIncidentId(), e.getAction(), e.getFromStatus(),
                e.getToStatus(), e.getAuthor(), e.getComment(), e.getOccurredAt());
    }
}
