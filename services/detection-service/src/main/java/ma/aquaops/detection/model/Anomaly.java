package ma.aquaops.detection.model;

import java.time.Instant;

public record Anomaly(
        String eventVersion,
        String anomalyId,
        String type,
        Integer zoneId,
        Integer sensorId,
        Instant measuredAt,
        Instant detectedAt
) {}
