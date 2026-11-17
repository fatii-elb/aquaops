package ma.aquaops.telemetry;

import java.time.DateTimeException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * NFR-05 : que se passe-t-il quand l'enregistrement d'une mesure echoue ?
 *
 * Par defaut Spring Kafka reessaie 9 fois sans attendre, puis ABANDONNE le message : une panne de la base
 * de quelques secondes ferait perdre des mesures. Ici on reessaie sans limite, toutes les 2 s : le consommateur
 * attend le retour de la base, l'offset n'avance pas, aucune mesure n'est perdue.
 *
 * Les erreurs qui viennent d'une mesure impossible a lire (bug ou donnee mal formee) ne sont PAS reessayees,
 * sinon une seule mauvaise mesure bloquerait la partition pour toujours : elle est journalisee puis passee.
 * (Les mesures invalides normales sont deja rejetees avant, par MeasureProcessor.)
 */
@Configuration
class KafkaRecoveryConfig {

    @Bean
    DefaultErrorHandler kafkaErrorHandler(@Value("${aquaops.kafka.retry-interval-ms:2000}") long intervalMs) {
        DefaultErrorHandler handler = new DefaultErrorHandler(new FixedBackOff(intervalMs, FixedBackOff.UNLIMITED_ATTEMPTS));
        handler.addNotRetryableExceptions(
                IllegalArgumentException.class,
                ClassCastException.class,
                NullPointerException.class,
                DateTimeException.class);
        return handler;
    }
}
