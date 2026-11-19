package ma.aquaops.detection.threshold;

import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lecture et enregistrement des seuils par zone (FR-13). */
@Service
public class ThresholdService {

    private final ZoneThresholdRepository repository;
    private final ThresholdCache cache;
    private final Clock clock;

    public ThresholdService(ZoneThresholdRepository repository, ThresholdCache cache, Clock clock) {
        this.repository = repository;
        this.cache = cache;
        this.clock = clock;
    }

    public ZoneThresholds defaults() {
        return cache.defaults();
    }

    @Transactional(readOnly = true)
    public List<ZoneThresholdEntity> customZones() {
        return repository.findAll().stream().sorted((a, b) -> Integer.compare(a.zoneId, b.zoneId)).toList();
    }

    /** Seuils de la zone et indication « personnalises » (faux : valeurs par defaut). */
    @Transactional(readOnly = true)
    public ZoneThresholdEntity find(int zoneId) {
        return repository.findById(zoneId).orElse(null);
    }

    /**
     * Cree ou remplace les seuils d'une zone. Les valeurs doivent avoir ete validees (ThresholdValidator).
     * Le cache de cette instance est rafraichi tout de suite ; les autres instances suivent a leur prochain cycle.
     */
    @Transactional
    public ZoneThresholdEntity save(int zoneId, ThresholdBody body, String user) {
        ZoneThresholdEntity entity = repository.findById(zoneId).orElseGet(ZoneThresholdEntity::new);
        entity.zoneId = zoneId;
        entity.leakMinPressure = body.leakMinPressure();
        entity.leakMaxFlow = body.leakMaxFlow();
        entity.leakDurationSeconds = body.leakDurationSeconds();
        entity.silenceThresholdSeconds = body.silenceThresholdSeconds();
        entity.tankMinLevel = body.tankMinLevel();
        entity.updatedAt = clock.instant();
        entity.updatedBy = user;
        ZoneThresholdEntity saved = repository.saveAndFlush(entity);
        cache.refresh();
        return saved;
    }
}
