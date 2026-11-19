
package ma.aquaops.detection.config;

import ma.aquaops.detection.engine.AnomalyRecoveryListener;
import ma.aquaops.detection.engine.DetectionEngine;
import ma.aquaops.detection.rules.LeakRule;
import ma.aquaops.detection.rules.LowLevelRule;
import ma.aquaops.detection.rules.PumpFaultRule;
import ma.aquaops.detection.rules.Rule;
import ma.aquaops.detection.threshold.ThresholdProvider;

import org.springframework.beans.factory.ObjectProvider;
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
    public LeakRule leakRule(
            Clock clock,
            ThresholdProvider thresholds) {

        // Seuils par zone (FR-13), recharges sans redemarrage
        return new LeakRule(thresholds, clock);
    }

    @Bean
    public LowLevelRule lowLevelRule(
            Clock clock,
            ThresholdProvider thresholds) {

        // Niveau de reservoir bas (FR-12)
        return new LowLevelRule(thresholds, clock);
    }

    @Bean
    public DetectionEngine detectionEngine(
            List<Rule> rules,
            ObjectProvider<AnomalyRecoveryListener> recoveryListener) {

        DetectionEngine engine = new DetectionEngine(rules);
        recoveryListener.ifAvailable(engine::setRecoveryListener);
        return engine;
    }
}
