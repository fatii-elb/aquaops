package ma.aquaops.simulator;

import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import ma.aquaops.simulator.scenario.ReadingType;
import ma.aquaops.simulator.scenario.Sample;
import ma.aquaops.simulator.scenario.ScenarioRegistry;
import tools.jackson.databind.json.JsonMapper;

@Component
public class SimulatorPublisher {

    private static final String TOPIC = "sensor-readings";
    private static final String EVENT_VERSION = "1";
    private static final Map<ReadingType, String> UNITES = Map.of(
            ReadingType.PRESSURE, "bar",
            ReadingType.FLOW, "m3/h",
            ReadingType.LEVEL, "m");

    private final KafkaTemplate<String, String> kafka;
    private final ScenarioRegistry scenarios;
    private final Clock clock;
    private final JsonMapper json = JsonMapper.builder().build();
    private final List<Sensor> capteurs;

    public SimulatorPublisher(KafkaTemplate<String, String> kafka,
                              ScenarioRegistry scenarios,
                              Clock clock,
                              @Value("${simulator.sensors-file}") Resource fichier) throws Exception {
        this.kafka = kafka;
        this.scenarios = scenarios;
        this.clock = clock;
        try (InputStream in = fichier.getInputStream()) {
            this.capteurs = List.of(json.readValue(in, Sensor[].class));
        }
    }

    @Scheduled(fixedRateString = "${simulator.interval-ms}")
    public void publier() {
        for (Sensor s : capteurs) {
            Sample mesure = scenarios.mesure(s.type());
            if (!mesure.emit()) {
                continue; // capteur silencieux : on n'envoie rien
            }
            kafka.send(TOPIC, String.valueOf(s.sensorId()), enJson(s, mesure));
        }
    }

    String enJson(Sensor s, Sample mesure) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("eventVersion", EVENT_VERSION);
        m.put("sensorId", s.sensorId());
        m.put("zoneId", s.zoneId());
        m.put("type", s.type().name());
        if (mesure.value() != null) {
            m.put("value", mesure.value());
            m.put("unit", UNITES.get(s.type()));
        }
        if (mesure.pumpState() != null) {
            m.put("pumpState", mesure.pumpState());
        }
        m.put("measuredAt", Instant.now(clock).toString());
        return json.writeValueAsString(m);
    }
}