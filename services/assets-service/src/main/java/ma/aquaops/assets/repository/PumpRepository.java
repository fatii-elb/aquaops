package ma.aquaops.assets.repository;

import ma.aquaops.assets.model.Pump;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PumpRepository extends JpaRepository<Pump, Long> {}
