
package ma.aquaops.detection.rules;

import ma.aquaops.detection.model.SensorReading;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class PressureWindowTracker {

    private final double minPressure;
    private final Duration window;

    private final Map<Integer, ZoneState> states =
            new HashMap<>();

    private record ZoneState(
            Instant lastMeasuredAt,
            Instant lowSince
    ) {}

    public PressureWindowTracker(
            double minPressure,
            Duration window) {

        if (!Double.isFinite(minPressure)
                || window.isZero()
                || window.isNegative()) {
            throw new IllegalArgumentException(
                    "Invalid pressure configuration");
        }

        this.minPressure = minPressure;
        this.window = window;
    }

    public synchronized boolean update(
            SensorReading reading) {

        if (!"PRESSURE".equals(reading.type())) {
            return false;
        }

        Integer zone = Objects.requireNonNull(
                reading.zoneId());
        Instant time = Objects.requireNonNull(
                reading.measuredAt());
        Double value = Objects.requireNonNull(
                reading.value());

        ZoneState previous = states.get(zone);

        if (previous != null &&
                !time.isAfter(previous.lastMeasuredAt())) {
            return false;
        }

        Instant lowSince = null;

        if (value < minPressure) {
            lowSince = previous != null
                    && previous.lowSince() != null
                    ? previous.lowSince()
                    : time;
        }

        states.put(zone, new ZoneState(time, lowSince));

        return lowSince != null &&
                Duration.between(lowSince, time)
                        .compareTo(window) >= 0;
    }
}
