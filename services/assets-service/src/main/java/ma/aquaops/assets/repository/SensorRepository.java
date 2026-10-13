package ma.aquaops.assets.repository;

import ma.aquaops.assets.model.Sensor;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SensorRepository extends JpaRepository<Sensor, Long> {}
