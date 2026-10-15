package ma.aquaops.telemetry.readings;

import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/telemetry/readings")
public class ReadingController {

    private static final int MAX_SIZE = 1000;

    private final ReadingRepository repository;

    public ReadingController(ReadingRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public PageResult<Reading> list(
            @RequestParam("sensor") Long sensor,
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "100") int size) {
        if (from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from doit etre avant to");
        }
        if (page < 0 || size < 1 || size > MAX_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "page >= 0 et size entre 1 et " + MAX_SIZE);
        }
        Page<Reading> result = repository.findBySensorIdAndMeasuredAtBetweenOrderByMeasuredAtAsc(
                sensor, from, to, PageRequest.of(page, size));
        return new PageResult<>(result.getContent(), page, size,
                result.getTotalElements(), result.getTotalPages());
    }
}
