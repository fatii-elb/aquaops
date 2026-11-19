
package ma.aquaops.detection.rules;

import ma.aquaops.detection.model.SensorReading;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntFunction;
import java.util.function.IntToDoubleFunction;

public class PressureWindowTracker {

    // Seuil et duree propres a chaque zone (FR-13) : relus a chaque mesure
    private final IntToDoubleFunction minPressureByZone;
    private final IntFunction<Duration> windowByZone;

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

        this.minPressureByZone = zone -> minPressure;
        this.windowByZone = zone -> window;
    }

    // Seuils differents selon la zone, modifiables sans redemarrage (FR-13)
    public PressureWindowTracker(
            IntToDoubleFunction minPressureByZone,
            IntFunction<Duration> windowByZone) {

        this.minPressureByZone = Objects.requireNonNull(minPressureByZone);
        this.windowByZone = Objects.requireNonNull(windowByZone);
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

        if (value < minPressureByZone.applyAsDouble(zone)) {
            lowSince = previous != null
                    && previous.lowSince() != null
                    ? previous.lowSince()
                    : time;
        }

        states.put(zone, new ZoneState(time, lowSince));

        return lowSince != null &&
                Duration.between(lowSince, time)
                        .compareTo(windowByZone.apply(zone)) >= 0;
    }
}
