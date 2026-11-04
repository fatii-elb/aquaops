package ma.aquaops.incidents.incident;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentRepository extends JpaRepository<Incident, Long> {

    boolean existsByAnomalyId(String anomalyId);

    Optional<Incident> findByAnomalyId(String anomalyId);
}
