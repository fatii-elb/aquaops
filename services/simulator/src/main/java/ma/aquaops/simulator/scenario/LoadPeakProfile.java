package ma.aquaops.simulator.scenario;

import java.time.Duration;

/**
 * Pic de consommation : apres 10 s, le debit monte MAIS la pression reste normale.
 *
 * VOULU : ce scenario ne doit declencher AUCUNE anomalie. Il prouve que la regle
 * de fuite exige les deux conditions (pression basse ET debit haut).
 * Ne pas "corriger" la pression.
 */
public class LoadPeakProfile implements ScenarioProfile {

    public static final Duration DEBUT = Duration.ofSeconds(10);
    public static final double DEBIT_PIC = 70.0;

    @Override
    public String name() {
        return "pic";
    }

    @Override
    public Duration duration() {
        return Duration.ofMinutes(2);
    }

    @Override
    public Sample sampleAt(ReadingType type, Duration elapsed) {
        if (type == ReadingType.FLOW && elapsed.compareTo(DEBUT) >= 0) {
            return Sample.value(DEBIT_PIC);
        }
        return NormalProfile.normal(type);
    }
}
