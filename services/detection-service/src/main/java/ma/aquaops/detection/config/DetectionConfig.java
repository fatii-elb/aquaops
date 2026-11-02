package ma.aquaops.detection.config;

import ma.aquaops.detection.engine.DetectionEngine;
import ma.aquaops.detection.rules.PumpFaultRule;
import ma.aquaops.detection.rules.Rule;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
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
    public DetectionEngine detectionEngine(List<Rule> rules) {
        return new DetectionEngine(rules);
    }
}
