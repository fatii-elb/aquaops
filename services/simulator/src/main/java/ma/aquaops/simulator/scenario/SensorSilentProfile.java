package ma.aquaops.simulator.scenario;

import java.time.Duration;

/** Capteur muet : apres 5 s, le capteur de pression cesse d'emettre. Les autres continuent. */
public class SensorSilentProfile implements ScenarioProfile {

    public static final Duration DEBUT = Duration.ofSeconds(5);

    @Override
    public String name() {
        return "silence";
    }

    @Override
    public Duration duration() {
        return Duration.ofMinutes(2);
    }

    @Override
    public Sample sampleAt(ReadingType type, Duration elapsed) {
        if (type == ReadingType.PRESSURE && elapsed.compareTo(DEBUT) >= 0) {
            return Sample.silence();
        }
        return NormalProfile.normal(type);
    }
}
