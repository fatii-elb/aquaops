package ma.aquaops.detection.anomaly;

import java.time.Clock;
import java.time.Instant;
import ma.aquaops.detection.engine.AnomalyRecoveryListener;
import ma.aquaops.detection.model.Anomaly;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enregistre les anomalies publiees et les marque resolues au retour a la normale (BE-1).
 * Deduplication a deux niveaux : un evenement deja vu (meme anomalyId) est ignore, et une anomalie
 * deja ouverte pour la meme cle n'est pas enregistree une seconde fois (protege d'un redemarrage du
 * service, dont la deduplication du moteur est en memoire).
 */
@Service
public class AnomalyStore implements AnomalyRecoveryListener {

    static final String SENSOR_SILENT = "SENSOR_SILENT";

    private final StoredAnomalyRepository repository;
    private final Clock clock;

    public AnomalyStore(StoredAnomalyRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Cle de deduplication : un capteur silencieux est propre a son capteur ("SENSOR_SILENT:zone:capteur"),
     * les autres anomalies sont propres a leur zone ("LEAK_SUSPECTED:zone"), comme dans le moteur de detection.
     */
    public static String dedupKey(String type, Integer zoneId, Integer sensorId) {
        if (SENSOR_SILENT.equals(type) && sensorId != null) {
            return type + ":" + zoneId + ":" + sensorId;
        }
        return type + ":" + zoneId;
    }

    /** Retourne vrai si l'anomalie a ete enregistree, faux si elle est incomplete ou deja connue. */
    @Transactional
    public boolean record(Anomaly event) {
        if (event == null || event.anomalyId() == null || event.type() == null
                || event.zoneId() == null || event.detectedAt() == null) {
            return false;
        }
        if (repository.existsByAnomalyId(event.anomalyId())) {
            return false;
        }
        String key = dedupKey(event.type(), event.zoneId(), event.sensorId());
        if (repository.existsByDedupKeyAndStatus(key, AnomalyStatus.OPEN)) {
            return false;
        }
        StoredAnomaly stored = new StoredAnomaly();
        stored.anomalyId = event.anomalyId();
        stored.type = event.type();
        stored.zoneId = event.zoneId();
        stored.sensorId = event.sensorId();
        stored.status = AnomalyStatus.OPEN;
        stored.dedupKey = key;
        stored.measuredAt = event.measuredAt();
        stored.detectedAt = event.detectedAt();
        repository.save(stored);
        return true;
    }

    @Override
    @Transactional
    public void onRecovered(int zoneId, String type) {
        resolve(dedupKey(type, zoneId, null));
    }

    @Override
    @Transactional
    public void onSensorRecovered(int zoneId, int sensorId) {
        resolve(dedupKey(SENSOR_SILENT, zoneId, sensorId));
    }

    private void resolve(String key) {
        Instant now = clock.instant();
        for (StoredAnomaly open : repository.findByDedupKeyAndStatus(key, AnomalyStatus.OPEN)) {
            open.status = AnomalyStatus.RESOLVED;
            open.resolvedAt = now;
        }
    }
}
