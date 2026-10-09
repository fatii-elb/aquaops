package ma.aquaops.assets.repository;

import ma.aquaops.assets.model.Tank;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TankRepository extends JpaRepository<Tank, Long> {}
