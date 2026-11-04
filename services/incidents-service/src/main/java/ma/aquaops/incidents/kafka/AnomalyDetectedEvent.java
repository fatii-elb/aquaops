package ma.aquaops.incidents.kafka;

import java.time.Instant;

/** Evenement anomaly-detected.v1 (champs utiles a FR-15). */
public record AnomalyDetectedEvent(String anomalyId, String type, Long zoneId, String sensorId,
                                   Instant detectedAt, Instant measuredAt, String severity) {
}
