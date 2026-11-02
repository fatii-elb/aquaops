
package ma.aquaops.detection.rules;

import ma.aquaops.detection.model.SensorReading;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class PressureWindowTrackerTest {

    private final Instant start =
            Instant.parse("2026-11-09T10:00:00Z");

    private final PressureWindowTracker tracker =
            new PressureWindowTracker(
                    2.0, Duration.ofSeconds(10));

    private SensorReading pressure(
            int zone, double value, long seconds) {

        return new SensorReading(
                "1", 12, zone, "PRESSURE",
                value, "bar", null,
                start.plusSeconds(seconds)
        );
    }

    @Test
    void shouldTriggerAfterTenSeconds() {
        assertFalse(tracker.update(pressure(3, 1.5, 0)));
        assertFalse(tracker.update(pressure(3, 1.4, 9)));
        assertTrue(tracker.update(pressure(3, 1.5, 10)));
    }

    @Test
    void shouldResetWhenPressureReturnsToNormal() {
        assertFalse(tracker.update(pressure(3, 1.5, 0)));
        assertFalse(tracker.update(pressure(3, 2.5, 5)));
        assertFalse(tracker.update(pressure(3, 1.4, 10)));
        assertTrue(tracker.update(pressure(3, 1.3, 20)));
    }

    @Test
    void shouldSeparateZones() {
        assertFalse(tracker.update(pressure(3, 1.5, 0)));
        assertFalse(tracker.update(pressure(4, 1.5, 5)));
        assertTrue(tracker.update(pressure(3, 1.4, 10)));
        assertFalse(tracker.update(pressure(4, 1.4, 10)));
    }

    @Test
    void shouldIgnoreOlderReading() {
        assertFalse(tracker.update(pressure(3, 1.5, 0)));
        assertFalse(tracker.update(pressure(3, 2.5, 5)));
        assertFalse(tracker.update(pressure(3, 1.2, 3)));
        assertFalse(tracker.update(pressure(3, 1.3, 10)));
    }
}
