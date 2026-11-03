package ma.aquaops.simulator.scenario;

import java.time.Duration;

/** Fuite : apres 10 s, la pression chute ET le debit monte, ensemble. */
public class LeakProfile implements ScenarioProfile {

    public static final Duration DEBUT = Duration.ofSeconds(10);
    public static final double PRESSION_FUITE = 1.2;
    public static final double DEBIT_FUITE = 60.0;

    @Override
    public String name() {
        return "fuite";
    }

    @Override
    public Duration duration() {
        return Duration.ofMinutes(2);
    }

    @Override
    public Sample sampleAt(ReadingType type, Duration elapsed) {
        if (elapsed.compareTo(DEBUT) >= 0) {
            if (type == ReadingType.PRESSURE) {
                return Sample.value(PRESSION_FUITE);
            }
            if (type == ReadingType.FLOW) {
                return Sample.value(DEBIT_FUITE);
            }
        }
        return NormalProfile.normal(type);
    }
}
