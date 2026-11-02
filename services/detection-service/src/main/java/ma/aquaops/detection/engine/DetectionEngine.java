package ma.aquaops.detection.engine;

import ma.aquaops.detection.model.Anomaly;
import ma.aquaops.detection.model.SensorReading;
import ma.aquaops.detection.rules.Rule;

import java.util.List;

public class DetectionEngine {

    private final List<Rule> rules;

    public DetectionEngine(List<Rule> rules) {
        this.rules = List.copyOf(rules);
    }

    public List<Anomaly> process(SensorReading reading) {
        return rules.stream()
                .flatMap(rule -> rule.evaluate(reading).stream())
                .toList();
    }
}
