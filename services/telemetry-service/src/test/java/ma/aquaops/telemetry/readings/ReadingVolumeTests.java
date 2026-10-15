package ma.aquaops.telemetry.readings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import ma.aquaops.telemetry.TelemetryServiceApplication;

/** Test de volume (SCRUM-27) : 100 000 mesures, requete sur 24 h en 2 s au plus. */
@SpringBootTest(classes = TelemetryServiceApplication.class)
class ReadingVolumeTests {

    private static final long SENSOR = 9_000_100L;
    private static final int TOTAL = 100_000;

    @Autowired ReadingRepository repository;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void clean() {
        jdbc.update("delete from readings where sensor_id = ?", SENSOR);
    }

    @Test
    void requeteSur24hEnMoinsDe2Secondes() {
        jdbc.update("delete from readings where sensor_id = ?", SENSOR);
        Instant start = Instant.parse("2026-10-20T00:00:00Z");
        long stepMs = Duration.ofHours(24).toMillis() / TOTAL;

        List<Reading> batch = new ArrayList<>();
        for (int i = 0; i < TOTAL; i++) {
            batch.add(new Reading(SENSOR, 3L, "PRESSURE", 2.0 + (i % 10) / 10.0, "bar", null,
                    start.plusMillis(i * stepMs)));
            if (batch.size() == 5000) {
                repository.saveAll(batch);
                batch.clear();
            }
        }

        Instant from = start;
        Instant to = start.plus(Duration.ofHours(24));
        long t0 = System.nanoTime();
        var page = repository.findBySensorIdAndMeasuredAtBetweenOrderByMeasuredAtAsc(
                SENSOR, from, to, PageRequest.of(0, 1000));
        long ms = (System.nanoTime() - t0) / 1_000_000;

        System.out.println("Requete 24 h sur " + TOTAL + " mesures : " + ms + " ms");
        assertEquals(TOTAL, page.getTotalElements());
        assertTrue(ms <= 2000, "Requete trop lente : " + ms + " ms");
    }
}
