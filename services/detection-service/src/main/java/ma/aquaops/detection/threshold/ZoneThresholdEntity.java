package ma.aquaops.detection.threshold;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Seuils personnalises d'une zone (FR-13). Une zone sans ligne utilise les seuils par defaut
 * de la configuration (detection.thresholds.defaults.*).
 */
@Entity
@Table(name = "zone_thresholds")
public class ZoneThresholdEntity {

    @Id
    @Column(name = "zone_id")
    public Integer zoneId;

    @Column(name = "leak_min_pressure", nullable = false)
    public double leakMinPressure;

    @Column(name = "leak_max_flow", nullable = false)
    public double leakMaxFlow;

    @Column(name = "leak_duration_seconds", nullable = false)
    public long leakDurationSeconds;

    @Column(name = "silence_threshold_seconds", nullable = false)
    public long silenceThresholdSeconds;

    @Column(name = "tank_min_level", nullable = false)
    public double tankMinLevel;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @Column(name = "updated_by")
    public String updatedBy;

    public ZoneThresholds toThresholds() {
        return new ZoneThresholds(leakMinPressure, leakMaxFlow, leakDurationSeconds,
                silenceThresholdSeconds, tankMinLevel);
    }
}
