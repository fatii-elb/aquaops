package ma.aquaops.incidents.incident;

import ma.aquaops.incidents.history.IncidentAction;
import ma.aquaops.incidents.history.IncidentHistoryEntry;
import ma.aquaops.incidents.history.IncidentHistoryRecorder;
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
import org.springframework.transaction.annotation.Transactional;

@Service
public class IncidentService {

    private static final Logger log = LoggerFactory.getLogger(IncidentService.class);

    private final IncidentRepository repository;
    private final IncidentEventPublisher publisher;
    private final IncidentHistoryRecorder history;

    public IncidentService(IncidentRepository repository, IncidentEventPublisher publisher,
                           IncidentHistoryRecorder history) {
        this.repository = repository;
        this.publisher = publisher;
        this.history = history;
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

    /**
     * Changement de statut selon la machine a etats (FR-17) ; affectation d'un technicien (FR-18).
     * SCRUM-38 / FR-16 : chaque changement de statut accepte ecrit une ligne d'historique, dans la
     * meme transaction que l'incident. actor vient du corps de la requete : il est obligatoire pour
     * un changement de statut, mais NON VERIFIE tant que l'authentification n'est pas en place.
     */
    @Transactional
    public Incident update(Long id, IncidentStatus newStatus, String assignee, String actor) {
        Incident incident = get(id);
        IncidentStatus from = incident.getStatus();
        String eventType = null;
        if (newStatus != null) {
            if (!IncidentStateMachine.isValidTransition(from, newStatus)) {
                throw new InvalidTransitionException(from, newStatus);
            }
            if (newStatus == IncidentStatus.ASSIGNED
                    && (assignee == null || assignee.isBlank()) && incident.getAssignee() == null) {
                throw new IllegalArgumentException("assignee est obligatoire pour passer en ASSIGNED");
            }
            if (actor == null || actor.isBlank()) {
                throw new IllegalArgumentException("actor est obligatoire pour changer le statut");
            }
            incident.setStatus(newStatus);
            eventType = IncidentEventPublisher.eventTypeFor(newStatus);
        }
        if (assignee != null && !assignee.isBlank()) {
            incident.setAssignee(assignee);
        }
        Incident saved = repository.save(incident);
        if (eventType != null) {
            // Envoie l'UPDATE maintenant : une erreur base arrive ici, avant l'historique et avant Kafka.
            repository.flush();
            history.recordStatusChange(saved.getId(), actionFor(newStatus), from, newStatus, actor, null);
            // En dernier : si l'historique echoue, rien n'est publie et la transaction est annulee.
            publisher.publish(saved, eventType, actor);
        }
        return saved;
    }

    /**
     * SCRUM-38 / FR-16 : ajoute un commentaire (ligne COMMENTED) sans changer le statut.
     * Inconnu : 404. Incident CLOSED : refuse, car CLOSED est l'etat final de IncidentStateMachine.
     * Texte (2000 max) et auteur (255 max) sont verifies par IncidentHistoryRecorder.
     * author vient du client : NON AUTHENTIFIE tant que le jeton n'est pas en place.
     * Aucun evenement Kafka : le contrat incident-events ne prevoit pas les commentaires.
     */
    @Transactional
    public IncidentHistoryEntry addComment(Long id, String author, String text) {
        Incident incident = get(id);
        if (incident.getStatus() == IncidentStatus.CLOSED) {
            throw new IllegalArgumentException("incident " + id + " clos : commentaire impossible");
        }
        return history.recordComment(id, author, text);
    }

    /** SCRUM-38 / FR-16 : historique d'un incident, du plus ancien au plus recent. Inconnu : 404. */
    @Transactional(readOnly = true)
    public List<IncidentHistoryEntry> history(Long id) {
        get(id);
        return history.historyOf(id);
    }

    /** Statut obtenu vers action d'historique (SCRUM-38). OPEN n'est jamais une destination. */
    static IncidentAction actionFor(IncidentStatus newStatus) {
        return switch (newStatus) {
            case CONFIRMED -> IncidentAction.CONFIRMED;
            case ASSIGNED -> IncidentAction.ASSIGNED;
            case CLOSED -> IncidentAction.CLOSED;
            case IN_PROGRESS, RESOLVED -> IncidentAction.STATUS_CHANGED;
            case OPEN -> throw new IllegalStateException("OPEN n'est pas une destination de transition");
        };
    }
}
