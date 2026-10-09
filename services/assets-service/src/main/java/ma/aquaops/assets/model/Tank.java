package ma.aquaops.assets.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;

@Entity
@Table(name = "tanks")
public class Tank {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @NotBlank
    public String name;
    @NotNull
    public Long zoneId;
    @Positive
    public Double capacityM3;
}
