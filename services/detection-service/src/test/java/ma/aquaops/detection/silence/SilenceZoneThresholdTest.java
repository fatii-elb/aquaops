package ma.aquaops.detection.silence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import ma.aquaops.detection.kafka.AnomalyPublisher;
import ma.aquaops.detection.model.Anomaly;
import ma.aquaops.detection.model.SensorReading;
import ma.aquaops.detection.threshold.ZoneThresholds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** FR-13 : le delai de silence est celui de la zone du capteur. */
class SilenceZoneThresholdTest {

    private static final Instant T0 = Instant.parse("2026-12-07T10:00:00Z");

    private MutableClock clock;
    private AnomalyPublisher publisher;
    private SilenceDetector detector;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(T0);
        publisher = mock(AnomalyPublisher.class);
        detector = new SilenceDetector(clock, 10, publisher);
        detector.setThresholds(zone -> new ZoneThresholds(2.0, 40.0, 10, zone == 1 ? 5 : 30, 2.0));
    }

    private void reading(int sensor, int zone) {
        detector.onReading(new SensorReading("1", sensor, zone, "PRESSURE", 3.0, "bar", null, clock.instant()));
    }

    @Test
    void chaqueZoneADeSonPropreDelai() {
        reading(11, 1);
        reading(22, 2);
        clock.advance(Duration.ofSeconds(5));

        detector.check();

        ArgumentCaptor<Anomaly> captor = ArgumentCaptor.forClass(Anomaly.class);
        verify(publisher, atLeastOnce()).publish(captor.capture());
        List<Anomaly> published = captor.getAllValues();
        assertEquals(1, published.size());
        assertEquals(1, published.get(0).zoneId());
        assertEquals(11, published.get(0).sensorId());
    }

    @Test
    void laZoneLenteFinitParEtreSilencieuse() {
        reading(22, 2);
        clock.advance(Duration.ofSeconds(29));
        detector.check();
        verify(publisher, never()).publish(any());

        clock.advance(Duration.ofSeconds(1));
        detector.check();
        verify(publisher).publish(any());
    }

    @Test
    void sansSeuilsParZoneLeSeuilGlobalSAppliqueToujours() {
        SilenceDetector plain = new SilenceDetector(clock, 10, publisher);
        plain.onReading(new SensorReading("1", 33, 9, "PRESSURE", 3.0, "bar", null, clock.instant()));
        clock.advance(Duration.ofSeconds(10));
        plain.check();
        verify(publisher).publish(any());
    }
}
