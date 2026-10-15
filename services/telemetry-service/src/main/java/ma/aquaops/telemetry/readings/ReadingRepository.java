package ma.aquaops.telemetry.readings;

import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReadingRepository extends JpaRepository<Reading, Long> {

    Page<Reading> findBySensorIdAndMeasuredAtBetweenOrderByMeasuredAtAsc(
            Long sensorId, Instant from, Instant to, Pageable pageable);
}
