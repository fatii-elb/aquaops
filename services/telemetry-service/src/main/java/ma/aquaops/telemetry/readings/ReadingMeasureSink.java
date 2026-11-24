package ma.aquaops.telemetry.readings;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Implementation reelle du sink : enregistre chaque mesure valide en base.
 * Si le meme message Kafka arrive deux fois (reprise apres l'arret d'un pod), la base le refuse
 * (contrainte uk_readings_source) et on l'ignore : une mesure, une ligne.
 * Si l'emetteur renvoie la meme mesure (meme identifiant, reponse HTTP perdue), Kafka recoit
 * un second message mais la base le refuse (contrainte uk_readings_reading_id) : on l'ignore aussi.
 *
 * RC-04 : un lot lu sur Kafka est ecrit en UNE transaction (un seul commit, inserts groupes par
 * hibernate.jdbc.batch_size) au lieu d'un commit par mesure. Si une mesure du lot est un doublon,
 * la transaction est annulee en entier et le lot est repris mesure par mesure (record) : meme
 * resultat qu'avant, une mesure = une ligne. Toute autre erreur remonte : Spring Kafka rejoue le
 * lot entier (KafkaRecoveryConfig), les offsets ne sont valides qu'apres l'enregistrement.
 */
@Component
public class ReadingMeasureSink implements MeasureSink {

    private static final Logger LOG = LoggerFactory.getLogger(ReadingMeasureSink.class);

    private final ReadingRepository repository;
    private final TransactionTemplate transactions;

    public ReadingMeasureSink(ReadingRepository repository, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public void recordAll(List<Reading> readings) {
        if (readings.isEmpty()) {
            return;
        }
        try {
            transactions.executeWithoutResult(status -> repository.saveAllAndFlush(readings));
        } catch (DataIntegrityViolationException e) {
            LOG.info("Lot de {} mesures : au moins un doublon, reprise mesure par mesure", readings.size());
            for (Reading reading : readings) {
                reading.id = null; // identifiant pris dans la transaction annulee : la mesure n'est pas en base
                record(reading);
            }
        }
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
            if (reading.readingId != null && repository.existsByReadingId(reading.readingId)) {
                LOG.info("Mesure deja enregistree (renvoyee par l'emetteur), ignoree : readingId={}", reading.readingId);
                return;
            }
            throw e;
        }
    }
}
