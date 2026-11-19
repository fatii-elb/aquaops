package ma.aquaops.detection.threshold;

import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Seuils en vigueur, recharges depuis la base sans redemarrage (FR-13).
 * Rechargement toutes les {@code detection.thresholds.refresh-ms} ms (15 s par defaut) : une modification faite
 * sur n'importe quelle instance est donc prise en compte en 15 s, bien en dessous des 30 s exigees.
 * Si la base est indisponible, les derniers seuils connus restent utilises.
 */
@Component
public class ThresholdCache implements ThresholdProvider {

    private static final Logger log = LoggerFactory.getLogger(ThresholdCache.class);

    private final ZoneThresholdRepository repository;
    private final ZoneThresholds defaults;
    private volatile Map<Integer, ZoneThresholds> overrides = Map.of();

    public ThresholdCache(ZoneThresholdRepository repository, ZoneThresholds defaultThresholds) {
        this.repository = repository;
        this.defaults = defaultThresholds;
    }

    @Override
    public ZoneThresholds forZone(int zoneId) {
        return overrides.getOrDefault(zoneId, defaults);
    }

    public ZoneThresholds defaults() {
        return defaults;
    }

    public boolean isCustom(int zoneId) {
        return overrides.containsKey(zoneId);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadAtStartup() {
        refresh();
    }

    @Scheduled(fixedDelayString = "${detection.thresholds.refresh-ms:15000}",
            initialDelayString = "${detection.thresholds.refresh-ms:15000}")
    public void refresh() {
        try {
            Map<Integer, ZoneThresholds> loaded = new HashMap<>();
            for (ZoneThresholdEntity e : repository.findAll()) {
                loaded.put(e.zoneId, e.toThresholds());
            }
            overrides = Map.copyOf(loaded);
        } catch (RuntimeException e) {
            log.warn("Rechargement des seuils impossible, seuils precedents conserves : {}", e.toString());
        }
    }
}
