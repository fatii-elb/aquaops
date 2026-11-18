package ma.aquaops.simulator;

import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import ma.aquaops.simulator.scenario.ReadingType;
import ma.aquaops.simulator.scenario.Sample;
import ma.aquaops.simulator.scenario.ScenarioRegistry;
import tools.jackson.databind.json.JsonMapper;

@Component
public class SimulatorPublisher {

    private static final String EVENT_VERSION = "1";
    private static final Logger LOG = LoggerFactory.getLogger(SimulatorPublisher.class);
    private static final Map<ReadingType, String> UNITES = Map.of(
            ReadingType.PRESSURE, "bar",
            ReadingType.FLOW, "m3/h",
            ReadingType.LEVEL, "m");

    private final TelemetryIngestClient telemetry;
    private final ScenarioRegistry scenarios;
    private final Clock clock;
    private final JsonMapper json = JsonMapper.builder().build();
    private final List<Sensor> capteurs;
    private volatile boolean pause;

    public SimulatorPublisher(TelemetryIngestClient telemetry,
                              ScenarioRegistry scenarios,
                              Clock clock,
                              @Value("${simulator.sensors-file}") Resource fichier) throws Exception {
        this.telemetry = telemetry;
        this.scenarios = scenarios;
        this.clock = clock;
        try (InputStream in = fichier.getInputStream()) {
            this.capteurs = List.of(json.readValue(in, Sensor[].class));
        }
    }

    @Scheduled(fixedRateString = "${simulator.interval-ms}")
    public void publier() {
        if (pause) {
            return; // test de charge en cours : le générateur de charge publie à ma place
        }
        for (Sensor s : capteurs) {
            Sample mesure = scenarios.mesure(s.type());
            if (!mesure.emit()) {
                continue; // capteur silencieux : on n'envoie rien
            }
            envoyer(s, mesure).whenComplete((ignored, error) -> {
                if (error != null) {
                    LOG.warn("Echec d’envoi de mesure pour sensorId={}", s.sensorId(), error);
                }
            });
        }
    }

    java.util.concurrent.CompletableFuture<Void> envoyer(Sensor sensor, Sample measure) {
        return telemetry.publish(enJson(sensor, measure));
    }

    void setPause(boolean pause) {
        this.pause = pause;
    }

    List<Sensor> capteurs() {
        return capteurs;
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