package ma.aquaops.simulator.scenario;

import java.time.Duration;

/** Fonctionnement normal du reseau. */
public class NormalProfile implements ScenarioProfile {

    public static final double PRESSION_NORMALE = 3.0;
    public static final double DEBIT_NORMAL = 20.0;
    public static final double NIVEAU_NORMAL = 5.0;
    public static final String POMPE_NORMALE = "NORMAL";

    @Override
    public String name() {
        return "normal";
    }

    /** Le regime normal n'expire jamais : le registre ne le remplace pas. */
    @Override
    public Duration duration() {
        return Duration.ZERO;
    }

    @Override
    public Sample sampleAt(ReadingType type, Duration elapsed) {
        return normal(type);
    }

    /** Valeur normale d'un capteur, reutilisee par les scenarios de panne. */
    public static Sample normal(ReadingType type) {
        return switch (type) {
            case PRESSURE -> Sample.value(PRESSION_NORMALE);
            case FLOW -> Sample.value(DEBIT_NORMAL);
            case LEVEL -> Sample.value(NIVEAU_NORMAL);
            case PUMP_STATE -> Sample.pump(POMPE_NORMALE);
        };
    }
}
