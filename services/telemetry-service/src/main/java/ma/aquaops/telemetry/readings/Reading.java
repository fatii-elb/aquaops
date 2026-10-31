package ma.aquaops.telemetry.readings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/** Une mesure historisee (contrat sensor-readings v1). */
@Entity
@Table(name = "readings",
        indexes = @Index(name = "idx_readings_sensor_time", columnList = "sensor_id, measured_at"))
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
}
