package ma.aquaops.telemetry.readings;

/**
 * Interface commune (SCRUM-27 / E2) : destination d'une mesure valide.
 * E6 (validation) appelle record() pour chaque mesure validee.
 * Implementation reelle : ReadingMeasureSink (ecrit en base).
 */
public interface MeasureSink {

    void record(Reading reading);
}
