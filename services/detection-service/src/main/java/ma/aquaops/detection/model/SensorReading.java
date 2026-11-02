package ma.aquaops.detection.model;

import java.time.Instant;

public record SensorReading(
        String eventVersion,
        Integer sensorId,
        Integer zoneId,
        String type,
        Double value,
        String unit,
        String pumpState,
        Instant measuredAt
) {}
