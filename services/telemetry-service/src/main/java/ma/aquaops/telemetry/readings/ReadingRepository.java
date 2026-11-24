package ma.aquaops.telemetry.readings;

import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReadingRepository extends JpaRepository<Reading, Long> {

    Page<Reading> findBySensorIdAndMeasuredAtBetweenOrderByMeasuredAtAsc(
            Long sensorId, Instant from, Instant to, Pageable pageable);

    /** Vrai si ce message Kafka est deja enregistre (reprise apres une panne). */
    boolean existsBySourcePartitionAndSourceOffsetAndSourceTimestamp(
            Integer sourcePartition, Long sourceOffset, Long sourceTimestamp);

    /** Vrai si cette mesure (identifiant donne par l'emetteur) est deja enregistree : envoi repete. */
    boolean existsByReadingId(String readingId);
}
