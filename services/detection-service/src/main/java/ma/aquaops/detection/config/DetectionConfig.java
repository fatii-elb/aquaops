
package ma.aquaops.detection.config;

import ma.aquaops.detection.engine.DetectionEngine;
import ma.aquaops.detection.rules.LeakRule;
import ma.aquaops.detection.rules.PumpFaultRule;
import ma.aquaops.detection.rules.Rule;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

@Configuration
public class DetectionConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public PumpFaultRule pumpFaultRule(Clock clock) {
        return new PumpFaultRule(clock);
    }

    @Bean
    public LeakRule leakRule(
            Clock clock,
            @Value("${detection.leak.min-pressure}")
            double minPressure,
            @Value("${detection.leak.max-flow}")
            double maxFlow,
            @Value("${detection.leak.duration-seconds}")
            long durationSeconds) {

        return new LeakRule(
                minPressure,
                maxFlow,
                Duration.ofSeconds(durationSeconds),
                clock
        );
    }

    @Bean
    public DetectionEngine detectionEngine(
            List<Rule> rules) {

        return new DetectionEngine(rules);
    }
}
