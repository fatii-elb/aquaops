package ma.aquaops.incidents.incident;

import ma.aquaops.incidents.kafka.AnomalyDetectedEvent;
import ma.aquaops.incidents.kafka.IncidentEventPublisher;
import ma.aquaops.incidents.state.IncidentStateMachine;
import ma.aquaops.incidents.state.IncidentStatus;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class IncidentService {

    private static final Logger log = LoggerFactory.getLogger(IncidentService.class);

    private final IncidentRepository repository;
    private final IncidentEventPublisher publisher;

    public IncidentService(IncidentRepository repository, IncidentEventPublisher publisher) {
        this.repository = repository;
        this.publisher = publisher;
    }

    /** FR-15 : cree un incident OPEN depuis une anomalie. Vide si l'anomalie a deja produit un incident. */
    public Optional<Incident> createFromAnomaly(AnomalyDetectedEvent event) {
        if (repository.existsByAnomalyId(event.anomalyId())) {
            log.info("Anomalie {} deja traitee, incident non recree", event.anomalyId());
            return Optional.empty();
        }
        Incident incident = new Incident();
        incident.setAnomalyId(event.anomalyId());
        incident.setZoneId(event.zoneId());
        incident.setSensorId(event.sensorId());
        incident.setType(event.type());
        incident.setStatus(IncidentStatus.OPEN);
        incident.setPriority(PriorityCalculator.forAnomalyType(event.type()));
        incident.setMeasuredAt(event.measuredAt());
        incident.setDescription("Anomalie " + event.type() + " detectee dans la zone " + event.zoneId());

        Incident saved;
        try {
            saved = repository.save(incident);
        } catch (DataIntegrityViolationException duplicate) {
            // deux messages identiques traites en meme temps : la contrainte d'unicite protege
            log.info("Anomalie {} deja traitee (contrainte d'unicite)", event.anomalyId());
            return Optional.empty();
        }
        publisher.publish(saved, IncidentEventPublisher.CREATED, null);
        log.info("Incident {} cree depuis l'anomalie {} (priorite {})",
                saved.getId(), event.anomalyId(), saved.getPriority());
        return Optional.of(saved);
    }

    /** Creation manuelle (POST /api/incidents). */
    public Incident createManual(Long zoneId, String type, Priority priority, String description, String actor) {
        if (zoneId == null) {
            throw new IllegalArgumentException("zoneId est obligatoire");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("type est obligatoire");
        }
        Incident incident = new Incident();
        incident.setZoneId(zoneId);
        incident.setType(type);
        incident.setStatus(IncidentStatus.OPEN);
        incident.setPriority(priority != null ? priority : PriorityCalculator.forAnomalyType(type));
        incident.setDescription(description);
        Incident saved = repository.save(incident);
        publisher.publish(saved, IncidentEventPublisher.CREATED, actor);
        return saved;
    }

    public Incident get(Long id) {
        return repository.findById(id).orElseThrow(() -> new IncidentNotFoundException(id));
    }

    public List<Incident> list(IncidentStatus status, Long zoneId) {
        return repository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .filter(i -> status == null || i.getStatus() == status)
                .filter(i -> zoneId == null || zoneId.equals(i.getZoneId()))
                .toList();
    }

    /** Changement de statut selon la machine a etats (FR-17) ; affectation d'un technicien (FR-18). */
    public Incident update(Long id, IncidentStatus newStatus, String assignee, String actor) {
        Incident incident = get(id);
        String eventType = null;
        if (newStatus != null) {
            IncidentStatus from = incident.getStatus();
            if (!IncidentStateMachine.isValidTransition(from, newStatus)) {
                throw new InvalidTransitionException(from, newStatus);
            }
            if (newStatus == IncidentStatus.ASSIGNED
                    && (assignee == null || assignee.isBlank()) && incident.getAssignee() == null) {
                throw new IllegalArgumentException("assignee est obligatoire pour passer en ASSIGNED");
            }
            incident.setStatus(newStatus);
            eventType = IncidentEventPublisher.eventTypeFor(newStatus);
        }
        if (assignee != null && !assignee.isBlank()) {
            incident.setAssignee(assignee);
        }
        Incident saved = repository.save(incident);
        if (eventType != null) {
            publisher.publish(saved, eventType, actor);
        }
        return saved;
    }
}
