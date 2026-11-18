package ma.aquaops.detection.silence;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.Instant;
import ma.aquaops.detection.engine.AnomalyRecoveryListener;
import ma.aquaops.detection.kafka.AnomalyPublisher;
import ma.aquaops.detection.model.SensorReading;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** BE-1 : la reprise d'un capteur silencieux est signalee a l'ecouteur. */
class SilenceRecoveryTest {

    private static final Instant T0 = Instant.parse("2026-11-09T10:00:00Z");

    private MutableClock clock;
    private AnomalyRecoveryListener listener;
    private SilenceDetector detector;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(T0);
        listener = mock(AnomalyRecoveryListener.class);
        detector = new SilenceDetector(clock, 10, mock(AnomalyPublisher.class));
        detector.setRecoveryListener(listener);
    }

    private SensorReading reading(int sensorId, int zoneId) {
        return new SensorReading("1", sensorId, zoneId, "PRESSURE", 3.0, "bar", null, clock.instant());
    }

    @Test
    void previentQuandUnCapteurSilencieuxReprend() {
        detector.onReading(reading(12, 3));
        clock.advance(Duration.ofSeconds(10));
        detector.check();

        detector.onReading(reading(12, 3));

        verify(listener).onSensorRecovered(3, 12);
    }

    @Test
    void neFaitRienPourUnCapteurQuiNEtaitPasSilencieux() {
        detector.onReading(reading(12, 3));
        clock.advance(Duration.ofSeconds(5));
        detector.onReading(reading(12, 3));

        verify(listener, never()).onSensorRecovered(3, 12);
    }
}
