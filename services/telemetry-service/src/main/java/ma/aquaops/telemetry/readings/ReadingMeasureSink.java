package ma.aquaops.telemetry.readings;

import org.springframework.stereotype.Component;

/** Implementation reelle du sink : enregistre chaque mesure valide en base. */
@Component
public class ReadingMeasureSink implements MeasureSink {

    private final ReadingRepository repository;

    public ReadingMeasureSink(ReadingRepository repository) {
        this.repository = repository;
    }

    @Override
    public void record(Reading reading) {
        repository.save(reading);
    }
}
