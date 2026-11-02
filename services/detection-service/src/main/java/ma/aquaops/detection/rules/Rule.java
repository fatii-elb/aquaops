package ma.aquaops.detection.rules;

import ma.aquaops.detection.model.Anomaly;
import ma.aquaops.detection.model.SensorReading;

import java.util.List;

public interface Rule {
    List<Anomaly> evaluate(SensorReading reading);
}
