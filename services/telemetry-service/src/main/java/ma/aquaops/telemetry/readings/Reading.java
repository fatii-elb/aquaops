package ma.aquaops.telemetry.readings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/** Une mesure historisee (contrat sensor-readings v1). */
@Entity
@Table(name = "readings",
        indexes = @Index(name = "idx_readings_sensor_time", columnList = "sensor_id, measured_at"),
        // NFR-05 : un message Kafka relu apres une reprise ne cree pas une deuxieme ligne (colonnes vides = pas de contrainte).
        uniqueConstraints = @UniqueConstraint(name = "uk_readings_source",
                columnNames = {"source_partition", "source_offset", "source_timestamp"}))
public class Reading {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    public Long id;

    @Column(name = "sensor_id", nullable = false)
    public Long sensorId;

    @Column(name = "zone_id", nullable = false)
    public Long zoneId;

    /** PRESSURE, FLOW, LEVEL ou PUMP_STATE. */
    @Column(nullable = false)
    public String type;

    /** Absent pour PUMP_STATE. */
    @Column(name = "measure_value")
    public Double value;

    public String unit;

    /** NORMAL ou FAULT, seulement pour PUMP_STATE. */
    @Column(name = "pump_state")
    public String pumpState;

    @Column(name = "measured_at", nullable = false)
    public Instant measuredAt;

    /** Origine Kafka (partition, offset, horodatage du message). Vide si la mesure ne vient pas de Kafka. */
    @Column(name = "source_partition")
    public Integer sourcePartition;

    @Column(name = "source_offset")
    public Long sourceOffset;

    @Column(name = "source_timestamp")
    public Long sourceTimestamp;

    public Reading() {
    }

    public Reading(Long sensorId, Long zoneId, String type, Double value, String unit,
                   String pumpState, Instant measuredAt) {
        this.sensorId = sensorId;
        this.zoneId = zoneId;
        this.type = type;
        this.value = value;
        this.unit = unit;
        this.pumpState = pumpState;
        this.measuredAt = measuredAt;
    }

    /** Note l'origine Kafka de la mesure (voir la contrainte uk_readings_source). */
    public Reading withSource(int partition, long offset, long timestamp) {
        this.sourcePartition = partition;
        this.sourceOffset = offset;
        this.sourceTimestamp = timestamp;
        return this;
    }
}
