package ma.aquaops.assets.repository;

import java.time.Instant;
import java.util.List;
import ma.aquaops.assets.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface AuditLogRepository
        extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    /** Sert au consommateur Kafka : un meme evenement rejoue ne cree pas une deuxieme ligne. */
    boolean existsBySourceAndActionAndEntityIdAndOccurredAt(
            String source, String action, String entityId, Instant occurredAt);

    @Query("select distinct a.action from AuditLog a order by a.action")
    List<String> findDistinctActions();
}
