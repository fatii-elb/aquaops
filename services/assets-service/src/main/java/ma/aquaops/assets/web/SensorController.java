package ma.aquaops.assets.web;

import jakarta.validation.Valid;
import java.util.List;
import ma.aquaops.assets.model.AssetType;
import ma.aquaops.assets.model.Sensor;
import ma.aquaops.assets.repository.PipeRepository;
import ma.aquaops.assets.repository.PumpRepository;
import ma.aquaops.assets.repository.SensorRepository;
import ma.aquaops.assets.repository.TankRepository;
import ma.aquaops.assets.repository.ZoneRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/assets/sensors")
public class SensorController {
    private final SensorRepository repo;
    private final ZoneRepository zones;
    private final PipeRepository pipes;
    private final TankRepository tanks;
    private final PumpRepository pumps;

    public SensorController(SensorRepository repo, ZoneRepository zones,
                            PipeRepository pipes, TankRepository tanks, PumpRepository pumps) {
        this.repo = repo; this.zones = zones;
        this.pipes = pipes; this.tanks = tanks; this.pumps = pumps;
    }

    private void checkZone(Long zoneId) {
        if (!zones.existsById(zoneId))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Zone inexistante");
    }

    // FR-02 : le capteur doit etre rattache a un actif qui existe (table selon assetType)
    private void checkAsset(AssetType type, Long assetId) {
        boolean exists = switch (type) {
            case PIPE -> pipes.existsById(assetId);
            case TANK -> tanks.existsById(assetId);
            case PUMP -> pumps.existsById(assetId);
        };
        if (!exists)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Actif inexistant");
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
        checkZone(x.zoneId); checkAsset(x.assetType, x.assetId);
        x.id = null; return repo.save(x);
    }

    @PutMapping("/{id}")
    public Sensor update(@PathVariable Long id, @Valid @RequestBody Sensor x) {
        one(id); checkZone(x.zoneId); checkAsset(x.assetType, x.assetId);
        x.id = id; return repo.save(x);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { one(id); repo.deleteById(id); }
}
