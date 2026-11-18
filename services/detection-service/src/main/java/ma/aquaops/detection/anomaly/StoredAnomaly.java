package ma.aquaops.detection.anomaly;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Anomalie detectee, conservee pour la consultation (BE-1). Ecrite par le service de detection
 * au moment de la publication sur anomaly-detected ; jamais modifiee par l'API.
 */
@Entity
@Table(name = "anomalies",
        uniqueConstraints = @UniqueConstraint(name = "uk_anomalies_anomaly_id", columnNames = "anomaly_id"),
        indexes = {
            @Index(name = "idx_anomalies_detected_at", columnList = "detected_at"),
            @Index(name = "idx_anomalies_zone", columnList = "zone_id"),
            @Index(name = "idx_anomalies_dedup_status", columnList = "dedup_key, status")
        })
public class StoredAnomaly {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** Identifiant de l'evenement Kafka ; unique : un meme evenement n'est enregistre qu'une fois. */
    @Column(name = "anomaly_id", nullable = false)
    public String anomalyId;

    @Column(name = "type", nullable = false)
    public String type;

    @Column(name = "zone_id", nullable = false)
    public Integer zoneId;

    @Column(name = "sensor_id")
    public Integer sensorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    public AnomalyStatus status;

    /** Cle de deduplication : une seule anomalie ouverte par cle (voir AnomalyStore.dedupKey). */
    @Column(name = "dedup_key", nullable = false)
    public String dedupKey;

    @Column(name = "measured_at")
    public Instant measuredAt;

    @Column(name = "detected_at", nullable = false)
    public Instant detectedAt;

    @Column(name = "resolved_at")
    public Instant resolvedAt;
}
