package ma.aquaops.telemetry.readings;

import java.util.List;

/**
 * Interface commune (SCRUM-27 / E2) : destination d'une mesure valide.
 * E6 (validation) appelle record() pour chaque mesure validee.
 * Implementation reelle : ReadingMeasureSink (ecrit en base).
 */
public interface MeasureSink {

    void record(Reading reading);

    /**
     * Un lot de mesures valides (un lot lu sur Kafka). Par defaut : une par une.
     * ReadingMeasureSink les ecrit en une seule transaction (RC-04).
     */
    default void recordAll(List<Reading> readings) {
        for (Reading reading : readings) {
            record(reading);
        }
    }
}
