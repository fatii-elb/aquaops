package ma.aquaops.incidents.history;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Horloge UTC du service. Les tests unitaires passent leur propre horloge fixe au constructeur. */
@Configuration
public class HistoryClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
