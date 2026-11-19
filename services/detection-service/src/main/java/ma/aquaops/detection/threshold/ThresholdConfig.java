package ma.aquaops.detection.threshold;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Seuils par defaut, utilises par les zones sans seuils personnalises. Ils reprennent les reglages existants
 * (detection.leak.*, silence.threshold-seconds / SILENCE_THRESHOLD_SECONDS) pour ne rien changer au comportement
 * avant la premiere modification.
 */
@Configuration
public class ThresholdConfig {

    @Bean
    public ZoneThresholds defaultThresholds(
            @Value("${detection.leak.min-pressure:2.0}") double leakMinPressure,
            @Value("${detection.leak.max-flow:40.0}") double leakMaxFlow,
            @Value("${detection.leak.duration-seconds:10}") long leakDurationSeconds,
            @Value("${detection.thresholds.defaults.silence-seconds:${silence.threshold-seconds:10}}") long silenceSeconds,
            @Value("${detection.thresholds.defaults.tank-min-level:2.0}") double tankMinLevel) {
        return new ZoneThresholds(leakMinPressure, leakMaxFlow, leakDurationSeconds, silenceSeconds, tankMinLevel);
    }
}
