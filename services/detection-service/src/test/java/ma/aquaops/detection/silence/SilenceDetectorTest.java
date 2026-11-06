package ma.aquaops.detection.silence;

import ma.aquaops.detection.kafka.AnomalyPublisher;
import ma.aquaops.detection.model.Anomaly;
import ma.aquaops.detection.model.SensorReading;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SilenceDetectorTest {

    private static final Instant T0 = Instant.parse("2026-11-09T10:00:00Z");

    private MutableClock clock;
    private AnomalyPublisher publisher;
    private SilenceDetector detector;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(T0);
        publisher = mock(AnomalyPublisher.class);
        detector = new SilenceDetector(clock, 10, publisher);
    }

    private SensorReading reading(int sensorId, int zoneId) {
        return new SensorReading("1", sensorId, zoneId, "PRESSURE",
                3.0, "bar", null, clock.instant());
    }

    @Test
    void noAnomalyBeforeThreshold() {
        detector.onReading(reading(1, 3));

        clock.advance(Duration.ofSeconds(9));
        detector.check();

        verifyNoInteractions(publisher);
    }

    @Test
    void anomalyPublishedAtThreshold() {
        detector.onReading(reading(1, 3));

        clock.advance(Duration.ofSeconds(10));
        detector.check();

        ArgumentCaptor<Anomaly> captor = ArgumentCaptor.forClass(Anomaly.class);
        verify(publisher).publish(captor.capture());
        Anomaly anomaly = captor.getValue();

        assertEquals("SENSOR_SILENT", anomaly.type());
        assertEquals("1", anomaly.eventVersion());
        assertEquals(3, anomaly.zoneId());
        assertEquals(1, anomaly.sensorId());
        assertEquals(T0, anomaly.measuredAt());
        assertEquals(T0.plusSeconds(10), anomaly.detectedAt());
        assertNotNull(anomaly.anomalyId());
    }

    @Test
    void noDuplicateWhileSensorStaysSilent() {
        detector.onReading(reading(1, 3));

        clock.advance(Duration.ofSeconds(10));
        detector.check();
        clock.advance(Duration.ofSeconds(5));
        detector.check();

        verify(publisher, times(1)).publish(any());
    }

    @Test
    void newReadingClearsSilence() {
        detector.onReading(reading(1, 3));
        clock.advance(Duration.ofSeconds(10));
        detector.check();

        detector.onReading(reading(1, 3));   // le capteur revient

        clock.advance(Duration.ofSeconds(9));
        detector.check();
        verify(publisher, times(1)).publish(any());   // pas encore

        clock.advance(Duration.ofSeconds(1));
        detector.check();
        verify(publisher, times(2)).publish(any());   // nouveau silence
    }

    @Test
    void sensorsAreIndependent() {
        detector.onReading(reading(1, 3));
        detector.onReading(reading(2, 3));

        clock.advance(Duration.ofSeconds(6));
        detector.onReading(reading(2, 3));   // le capteur 2 continue d'émettre

        clock.advance(Duration.ofSeconds(5));
        detector.check();

        ArgumentCaptor<Anomaly> captor = ArgumentCaptor.forClass(Anomaly.class);
        verify(publisher, times(1)).publish(captor.capture());
        assertEquals(1, captor.getValue().sensorId());
    }
}