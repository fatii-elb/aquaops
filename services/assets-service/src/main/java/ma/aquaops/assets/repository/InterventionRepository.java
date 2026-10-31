package ma.aquaops.assets.repository;

import java.util.List;
import ma.aquaops.assets.model.Intervention;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterventionRepository extends JpaRepository<Intervention, Long> {
    List<Intervention> findByIncidentId(Long incidentId);
}
