package ma.aquaops.assets.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;

@Entity
@Table(name = "sensors")
public class Sensor {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @NotBlank
    public String name;
    @NotNull
    @Enumerated(EnumType.STRING)
    public SensorType type;
    @NotNull
    public Long zoneId;
}
