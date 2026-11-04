package ma.aquaops.incidents.kafka;

import ma.aquaops.incidents.incident.IncidentService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class AnomalyConsumer {

    private final AnomalyParser parser;
    private final IncidentService service;

    public AnomalyConsumer(AnomalyParser parser, IncidentService service) {
        this.parser = parser;
        this.service = service;
    }

    @KafkaListener(topics = "anomaly-detected", groupId = "incidents-service",
            properties = {"auto.offset.reset=earliest"})
    public void onAnomaly(String payload) {
        parser.parse(payload).ifPresent(service::createFromAnomaly);
    }
}
