
package ma.aquaops.detection.engine;

import ma.aquaops.detection.model.Anomaly;
import ma.aquaops.detection.model.SensorReading;
import ma.aquaops.detection.rules.LeakRule;
import ma.aquaops.detection.rules.LowLevelRule;
import ma.aquaops.detection.rules.Rule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class DetectionEngine {

    private static final Logger log =
            LoggerFactory.getLogger(DetectionEngine.class);

    private final List<Rule> rules;
    private final AnomalyDeduplicator deduplicator;

    // Derniere mesure de pompe acceptee par zone
    private final Map<Integer, Instant> lastPumpReading =
            new HashMap<>();

    // Facultatif : prevenu quand une anomalie ouverte se termine (BE-1)
    private AnomalyRecoveryListener recoveryListener;

    public DetectionEngine(List<Rule> rules) {
        this(rules, new AnomalyDeduplicator());
    }

    public DetectionEngine(
            List<Rule> rules,
            AnomalyDeduplicator deduplicator) {

        this.rules = List.copyOf(rules);
        this.deduplicator =
                Objects.requireNonNull(deduplicator);
    }

    public synchronized List<Anomaly> process(
            SensorReading reading) {

        Objects.requireNonNull(reading);

        // ETAPE 1 : Verifier l'ordre des mesures de pompe
        if ("PUMP_STATE".equals(reading.type())) {

            Integer zone = reading.zoneId();
            Instant time = reading.measuredAt();
            String state = reading.pumpState();

            if (zone == null || time == null
                    || (!"FAULT".equals(state)
                    && !"NORMAL".equals(state))) {
                return List.of();
            }

            Instant previous = lastPumpReading.get(zone);

            // Ignorer les mesures anciennes ou dupliquees
            if (previous != null
                    && !time.isAfter(previous)) {
                return List.of();
            }

            lastPumpReading.put(zone, time);

            // Recuperation uniquement pour une mesure recente
            if ("NORMAL".equals(state)) {
                notifyRecovered(zone, "PUMP_FAULT",
                        deduplicator.recover(zone, "PUMP_FAULT"));
            }
        }

        List<Anomaly> published = new ArrayList<>();

        // ETAPE 2 : Appliquer les regles de detection
        for (Rule rule : rules) {

            List<Anomaly> anomalies =
                    rule.evaluate(reading);

            // ETAPE 3 : Recuperation d'une fuite
            if (rule instanceof LeakRule leakRule
                    && leakRule.confirmsRecovery(reading)) {

                notifyRecovered(reading.zoneId(), "LEAK_SUSPECTED",
                        deduplicator.recover(
                                reading.zoneId(),
                                "LEAK_SUSPECTED"));
            }

            // ETAPE 3 bis : Retour a la normale d'un niveau bas (FR-12)
            if (rule instanceof LowLevelRule lowLevelRule
                    && lowLevelRule.confirmsRecovery(reading)) {

                notifyRecovered(reading.zoneId(), LowLevelRule.TYPE,
                        deduplicator.recover(
                                reading.zoneId(),
                                LowLevelRule.TYPE));
            }

            // ETAPE 4 : Supprimer les anomalies repetees
            for (Anomaly anomaly : anomalies) {

                if (deduplicator.tryOpen(
                        anomaly.zoneId(),
                        anomaly.type())) {

                    published.add(anomaly);
                }
            }
        }

        return published;
    }

    public synchronized void setRecoveryListener(
            AnomalyRecoveryListener listener) {

        this.recoveryListener = listener;
    }

    // Un echec de l'ecouteur ne doit jamais arreter la detection.
    private void notifyRecovered(
            int zoneId, String type, boolean wasOpen) {

        if (!wasOpen || recoveryListener == null) {
            return;
        }
        try {
            recoveryListener.onRecovered(zoneId, type);
        } catch (RuntimeException e) {
            log.warn("Resolution de l'anomalie {} (zone {}) non enregistree : {}",
                    type, zoneId, e.toString());
        }
    }
}
