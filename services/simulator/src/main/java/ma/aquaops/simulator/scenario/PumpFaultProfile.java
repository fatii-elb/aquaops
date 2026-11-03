package ma.aquaops.simulator.scenario;

import java.time.Duration;

/** Panne de pompe : apres 5 s, PUMP_STATE passe a FAULT. Le reste est normal. */
public class PumpFaultProfile implements ScenarioProfile {

    public static final Duration DEBUT = Duration.ofSeconds(5);
    public static final String POMPE_EN_PANNE = "FAULT";

    @Override
    public String name() {
        return "pompe";
    }

    @Override
    public Duration duration() {
        return Duration.ofMinutes(2);
    }

    @Override
    public Sample sampleAt(ReadingType type, Duration elapsed) {
        if (type == ReadingType.PUMP_STATE && elapsed.compareTo(DEBUT) >= 0) {
            return Sample.pump(POMPE_EN_PANNE);
        }
        return NormalProfile.normal(type);
    }
}
