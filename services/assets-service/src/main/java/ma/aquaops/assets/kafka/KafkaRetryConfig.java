package ma.aquaops.assets.kafka;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Que faire quand le traitement d'un message echoue (par exemple PostgreSQL indisponible) ?
 *
 * Sans reglage, Spring Kafka retente 9 fois de suite SANS attendre : une panne de base de
 * quelques secondes suffit pour epuiser les tentatives et perdre la ligne d'audit.
 * Ici : 30 nouvelles tentatives espacees de 2 secondes, soit environ une minute.
 * Si la base reste indisponible plus longtemps, le message est journalise puis abandonne.
 *
 * Spring Boot applique ce bean a tous les @KafkaListener du service.
 */
@Configuration
public class KafkaRetryConfig {

    @Bean
    DefaultErrorHandler kafkaErrorHandler() {
        return new DefaultErrorHandler(new FixedBackOff(2000L, 30L));
    }
}
