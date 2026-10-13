package ma.aquaops.assets.web;

import jakarta.validation.Valid;
import java.util.List;
import ma.aquaops.assets.model.Sensor;
import ma.aquaops.assets.repository.SensorRepository;
import ma.aquaops.assets.repository.ZoneRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/assets/sensors")
public class SensorController {
    private final SensorRepository repo;
    private final ZoneRepository zones;

    public SensorController(SensorRepository repo, ZoneRepository zones) {
        this.repo = repo; this.zones = zones;
    }

    private void checkZone(Long zoneId) {
        if (!zones.existsById(zoneId))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Zone inexistante");
    }

    @GetMapping
    public List<Sensor> all() { return repo.findAll(); }

    @GetMapping("/{id}")
    public Sensor one(@PathVariable Long id) {
        return repo.findById(id).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Capteur introuvable"));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Sensor create(@Valid @RequestBody Sensor x) {
        checkZone(x.zoneId); x.id = null; return repo.save(x);
    }

    @PutMapping("/{id}")
    public Sensor update(@PathVariable Long id, @Valid @RequestBody Sensor x) {
        one(id); checkZone(x.zoneId); x.id = id; return repo.save(x);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { one(id); repo.deleteById(id); }
}
