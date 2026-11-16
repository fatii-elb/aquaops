package ma.aquaops.telemetry.readings;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import ma.aquaops.telemetry.TelemetryServiceApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** NFR-05 : un message Kafka relu apres une reprise ne cree qu'une seule ligne. Comme ReadingApiTests : base PostgreSQL. */
@SpringBootTest(classes = TelemetryServiceApplication.class)
class ReadingMeasureSinkTests {

    private static final long SENSOR = 9_000_003L;
    private static final Instant T = Instant.parse("2026-10-20T10:00:00Z");

    @Autowired MeasureSink sink;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.update("delete from readings where sensor_id = ?", SENSOR);
    }

    private Reading mesure() {
        return new Reading(SENSOR, 3L, "PRESSURE", 2.4, "bar", null, T);
    }

    private int lignes() {
        Integer n = jdbc.queryForObject("select count(*) from readings where sensor_id = ?", Integer.class, SENSOR);
        return n == null ? 0 : n;
    }

    @Test
    void leMemeMessageLivreDeuxFoisNEstStockeQuUneFois() {
        sink.record(mesure().withSource(0, 42L, 1_700_000_000_000L));
        sink.record(mesure().withSource(0, 42L, 1_700_000_000_000L));
        assertEquals(1, lignes());
    }

    @Test
    void deuxMessagesDifferentsSontTousLesDeuxStockes() {
        sink.record(mesure().withSource(0, 42L, 1_700_000_000_000L));
        sink.record(mesure().withSource(0, 43L, 1_700_000_000_001L));
        assertEquals(2, lignes());
    }

    @Test
    void unTopicRecreeNeFaitPasPerdreDeMesure() {
        // Memes partition et offset qu'avant, mais horodatage different : ce n'est pas le meme message.
        sink.record(mesure().withSource(0, 42L, 1_700_000_000_000L));
        sink.record(mesure().withSource(0, 42L, 1_800_000_000_000L));
        assertEquals(2, lignes());
    }

    @Test
    void sansOrigineKafkaAucunDedoublonnage() {
        sink.record(mesure());
        sink.record(mesure());
        assertEquals(2, lignes());
    }
}
