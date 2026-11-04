package ma.aquaops.incidents.history;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentHistoryRepository extends JpaRepository<IncidentHistoryEntry, Long> {

    /**
     * Historique d'un incident, du plus ancien au plus recent.
     * A heure egale, l'ordre d'insertion (id) departage.
     */
    List<IncidentHistoryEntry> findByIncidentIdOrderByOccurredAtAscIdAsc(Long incidentId);
}
