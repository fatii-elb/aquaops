package ma.aquaops.telemetry.validation;

import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
        return process(mesure, null);
    }

    /** Idem, en notant l'origine Kafka de la mesure (pour ignorer un message livre deux fois). */
    public boolean process(Map<String, Object> mesure, KafkaSource source) {
        ValidationResult resultat = validator.validate(mesure);
        if (!resultat.isValid()) {
            rejeter(resultat, mesure);
            return false;
        }
        sink.record(versReading(mesure, source));
        accepted.incrementAndGet();
        return true;
    }

    /** Une mesure lue sur Kafka avec son origine (lot de SensorReadingsConsumer). */
    public record Incoming(Map<String, Object> mesure, KafkaSource source) { }

    /**
     * RC-04 : un lot Kafka. Chaque mesure est validee comme dans process() ; les valides partent au
     * sink en une fois (une transaction). Une mesure invalide ou inconvertible est rejetee seule :
     * elle ne bloque pas le lot. Retourne le nombre de mesures acceptees.
     */
    public int processAll(List<Incoming> lot) {
        List<Reading> valides = new ArrayList<>(lot.size());
        for (Incoming entree : lot) {
            ValidationResult resultat = validator.validate(entree.mesure());
            if (!resultat.isValid()) {
                rejeter(resultat, entree.mesure());
                continue;
            }
            try {
                valides.add(versReading(entree.mesure(), entree.source()));
            } catch (IllegalArgumentException | ClassCastException | NullPointerException | DateTimeException e) {
                reject("mesure inconvertible (" + e.getClass().getSimpleName() + ")");
            }
        }
        sink.recordAll(valides);
        accepted.addAndGet(valides.size());
        return valides.size();
    }

    /** Rejette un message illisible (JSON invalide) : journal et compteur. */
    public void reject(String cause) {
        rejected.incrementAndGet();
        LOG.warn("Message rejeté : cause={}", cause);
    }

    private void rejeter(ValidationResult resultat, Map<String, Object> mesure) {
        rejected.incrementAndGet();
        LOG.warn("Mesure rejetée : causes={}, sensorId={}, measuredAt={}",
                resultat.errors(),
                mesure == null ? null : mesure.get("sensorId"),
                mesure == null ? null : mesure.get("measuredAt"));
    }

    public long acceptedCount() {
        return accepted.get();
    }

    public long rejectedCount() {
        return rejected.get();
    }

    private Reading versReading(Map<String, Object> m, KafkaSource source) {
        Long sensorId = ((Number) m.get("sensorId")).longValue();
        Long zoneId = ((Number) m.get("zoneId")).longValue();
        Double valeur = m.get("value") instanceof Number n ? n.doubleValue() : null;
        String unite = m.get("unit") instanceof String s ? s : null;
        String etatPompe = m.get("pumpState") instanceof String p ? p : null;
        Instant date = Instant.parse((String) m.get("measuredAt"));
        Reading reading = new Reading(sensorId, zoneId, (String) m.get("type"), valeur, unite, etatPompe, date);
        if (source == null) {
            return reading;
        }
        return reading.withSource(source.partition(), source.offset(), source.timestamp())
                .withReadingId(source.readingId());
    }
}