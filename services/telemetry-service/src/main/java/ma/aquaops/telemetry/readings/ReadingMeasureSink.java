package ma.aquaops.telemetry.readings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * Implementation reelle du sink : enregistre chaque mesure valide en base.
 * Si le meme message Kafka arrive deux fois (reprise apres l'arret d'un pod), la base le refuse
 * (contrainte uk_readings_source) et on l'ignore : une mesure, une ligne.
 */
@Component
public class ReadingMeasureSink implements MeasureSink {

    private static final Logger LOG = LoggerFactory.getLogger(ReadingMeasureSink.class);

    private final ReadingRepository repository;

    public ReadingMeasureSink(ReadingRepository repository) {
        this.repository = repository;
    }

    @Override
    public void record(Reading reading) {
        try {
            repository.save(reading);
        } catch (DataIntegrityViolationException e) {
            if (reading.sourceOffset != null && repository.existsBySourcePartitionAndSourceOffsetAndSourceTimestamp(
                    reading.sourcePartition, reading.sourceOffset, reading.sourceTimestamp)) {
                LOG.info("Mesure deja enregistree (message relu apres une reprise), ignoree : partition={}, offset={}",
                        reading.sourcePartition, reading.sourceOffset);
                return;
            }
            throw e;
        }
    }
}
