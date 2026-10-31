package ma.aquaops.telemetry.validation;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import ma.aquaops.telemetry.readings.MeasureSink;
import ma.aquaops.telemetry.readings.Reading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Traite une mesure (SCRUM-26) : validation, puis envoi au MeasureSink si elle
 * est valide, sinon rejet journalisé (cause, capteur, horodatage) et compté.
 * Classe sans Spring ni Kafka : le sink est injecté.
 */
public class MeasureProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(MeasureProcessor.class);

    private final MeasureValidator validator;
    private final MeasureSink sink;
    private final AtomicLong accepted = new AtomicLong();
    private final AtomicLong rejected = new AtomicLong();

    public MeasureProcessor(MeasureValidator validator, MeasureSink sink) {
        this.validator = validator;
        this.sink = sink;
    }

    /** Retourne true si la mesure a été acceptée et envoyée au sink. */
    public boolean process(Map<String, Object> mesure) {
        ValidationResult resultat = validator.validate(mesure);
        if (!resultat.isValid()) {
            rejected.incrementAndGet();
            LOG.warn("Mesure rejetée : causes={}, sensorId={}, measuredAt={}",
                    resultat.errors(),
                    mesure == null ? null : mesure.get("sensorId"),
                    mesure == null ? null : mesure.get("measuredAt"));
            return false;
        }
        sink.record(versReading(mesure));
        accepted.incrementAndGet();
        return true;
    }

    /** Rejette un message illisible (JSON invalide) : journal et compteur. */
    public void reject(String cause) {
        rejected.incrementAndGet();
        LOG.warn("Message rejeté : cause={}", cause);
    }

    public long acceptedCount() {
        return accepted.get();
    }

    public long rejectedCount() {
        return rejected.get();
    }

    private Reading versReading(Map<String, Object> m) {
        Long sensorId = ((Number) m.get("sensorId")).longValue();
        Long zoneId = ((Number) m.get("zoneId")).longValue();
        Double valeur = m.get("value") instanceof Number n ? n.doubleValue() : null;
        String unite = m.get("unit") instanceof String s ? s : null;
        String etatPompe = m.get("pumpState") instanceof String p ? p : null;
        Instant date = Instant.parse((String) m.get("measuredAt"));
        return new Reading(sensorId, zoneId, (String) m.get("type"), valeur, unite, etatPompe, date);
    }
}