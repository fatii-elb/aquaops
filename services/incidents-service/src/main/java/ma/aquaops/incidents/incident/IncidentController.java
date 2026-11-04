package ma.aquaops.incidents.incident;

import ma.aquaops.incidents.state.IncidentStatus;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/incidents")
public class IncidentController {

    public record CreateIncidentRequest(Long zoneId, String type, Priority priority, String description,
                                        String actor) {
    }

    public record UpdateIncidentRequest(IncidentStatus status, String assignee, String actor) {
    }

    private final IncidentService service;

    public IncidentController(IncidentService service) {
        this.service = service;
    }

    @GetMapping
    public List<Incident> list(@RequestParam(name = "status", required = false) IncidentStatus status,
                               @RequestParam(name = "zoneId", required = false) Long zoneId) {
        return service.list(status, zoneId);
    }

    @GetMapping("/{id}")
    public Incident get(@PathVariable("id") Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Incident create(@RequestBody CreateIncidentRequest request) {
        return service.createManual(request.zoneId(), request.type(), request.priority(),
                request.description(), request.actor());
    }

    @PutMapping("/{id}")
    public Incident update(@PathVariable("id") Long id, @RequestBody UpdateIncidentRequest request) {
        return service.update(id, request.status(), request.assignee(), request.actor());
    }
}
