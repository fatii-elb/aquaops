package ma.aquaops.detection.threshold;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Seuils de detection par zone (FR-13). Lecture : les quatre roles. Modification : administrateur uniquement
 * (voir SecurityConfig). Le service de detection ne connait pas la liste des zones (elle est dans assets-service) :
 * toute zone d'identifiant positif est acceptee.
 */
@RestController
@RequestMapping("/api/detection/thresholds")
public class ThresholdController {

    /** Seuils d'une zone : {@code custom} est faux quand ce sont les valeurs par defaut. */
    public record ZoneThresholdView(int zoneId, boolean custom, ThresholdBody thresholds,
                                    Instant updatedAt, String updatedBy) {
    }

    public record ThresholdsView(ThresholdBody defaults, List<ZoneThresholdView> zones) {
    }

    private final ThresholdService service;

    public ThresholdController(ThresholdService service) {
        this.service = service;
    }

    /** Seuils par defaut et zones personnalisees. Les zones absentes de la liste utilisent les valeurs par defaut. */
    @GetMapping
    public ThresholdsView list() {
        List<ZoneThresholdView> zones = service.customZones().stream().map(ThresholdController::view).toList();
        return new ThresholdsView(ThresholdBody.of(service.defaults()), zones);
    }

    @GetMapping("/{zoneId}")
    public ResponseEntity<?> get(@PathVariable int zoneId) {
        if (zoneId < 1) {
            return invalidZone();
        }
        ZoneThresholdEntity entity = service.find(zoneId);
        if (entity == null) {
            return ResponseEntity.ok(new ZoneThresholdView(zoneId, false,
                    ThresholdBody.of(service.defaults()), null, null));
        }
        return ResponseEntity.ok(view(entity));
    }

    @PutMapping("/{zoneId}")
    public ResponseEntity<?> put(@PathVariable int zoneId, @RequestBody(required = false) ThresholdBody body,
                                 Authentication authentication) {
        if (zoneId < 1) {
            return invalidZone();
        }
        List<FieldError> errors = ThresholdValidator.validate(body);
        if (!errors.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("errors", errors));
        }
        String user = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(view(service.save(zoneId, body, user)));
    }

    private static ResponseEntity<?> invalidZone() {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("errors", List.of(new FieldError("zoneId", "L'identifiant de zone doit etre positif."))));
    }

    private static ZoneThresholdView view(ZoneThresholdEntity e) {
        return new ZoneThresholdView(e.zoneId, true, ThresholdBody.of(e.toThresholds()), e.updatedAt, e.updatedBy);
    }
}
