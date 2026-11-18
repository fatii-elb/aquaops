package ma.aquaops.detection.anomaly;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface StoredAnomalyRepository
        extends JpaRepository<StoredAnomaly, Long>, JpaSpecificationExecutor<StoredAnomaly> {

    boolean existsByAnomalyId(String anomalyId);

    boolean existsByDedupKeyAndStatus(String dedupKey, AnomalyStatus status);

    List<StoredAnomaly> findByDedupKeyAndStatus(String dedupKey, AnomalyStatus status);
}
