package ma.aquaops.simulator.scenario;

import java.time.Duration;

/**
 * Un scenario de panne : une fonction pure du temps ecoule depuis son demarrage.
 * Aucune dependance a Spring, Kafka ou au reseau.
 */
public interface ScenarioProfile {

    String name();

    /** Duree du scenario ; au-dela, le simulateur revient au normal. */
    Duration duration();

    Sample sampleAt(ReadingType type, Duration elapsed);
}
