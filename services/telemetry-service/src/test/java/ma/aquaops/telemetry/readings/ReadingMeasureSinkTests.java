package ma.aquaops.telemetry.readings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import ma.aquaops.telemetry.TelemetryServiceApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
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

    // RC-04 / RC-05 : l'emetteur renvoie une mesure dont il n'a pas recu la reponse. Kafka recoit deux
    // messages DIFFERENTS (offsets differents) ; l'identifiant de la mesure est le meme : une seule ligne.
    private static final String ID_A = "6c8e1d10-0000-4000-8000-000000000001";
    private static final String ID_B = "6c8e1d10-0000-4000-8000-000000000002";

    @Test
    void laMemeMesureEnvoyeeDeuxFoisNEstStockeeQuUneFois() {
        sink.record(mesure().withSource(0, 50L, 1_700_000_000_000L).withReadingId(ID_A));
        sink.record(mesure().withSource(0, 51L, 1_700_000_000_005L).withReadingId(ID_A));
        assertEquals(1, lignes());
    }

    @Test
    void deuxMesuresDifferentesSontToutesLesDeuxStockees() {
        sink.record(mesure().withSource(0, 50L, 1_700_000_000_000L).withReadingId(ID_A));
        sink.record(mesure().withSource(0, 51L, 1_700_000_000_005L).withReadingId(ID_B));
        assertEquals(2, lignes());
    }

    @Test
    void sansIdentifiantDeMesureLeComportementDOrigineEstGarde() {
        sink.record(mesure().withSource(0, 50L, 1_700_000_000_000L));
        sink.record(mesure().withSource(0, 51L, 1_700_000_000_005L));
        assertEquals(2, lignes());
    }

    // RC-04 : un lot Kafka est ecrit en une transaction ; un doublon dans le lot ne perd ni ne double rien.

    @Test
    void unLotEstStockeEnEntier() {
        sink.recordAll(List.of(
                mesure().withSource(1, 10L, 1_700_000_000_010L).withReadingId(ID_A),
                mesure().withSource(1, 11L, 1_700_000_000_011L).withReadingId(ID_B),
                mesure().withSource(1, 12L, 1_700_000_000_012L)));
        assertEquals(3, lignes());
    }

    @Test
    void unLotRejoueDejaEnPartieStockeNeDoublePas() {
        sink.record(mesure().withSource(1, 10L, 1_700_000_000_010L));
        sink.recordAll(List.of(
                mesure().withSource(1, 10L, 1_700_000_000_010L),
                mesure().withSource(1, 11L, 1_700_000_000_011L),
                mesure().withSource(1, 12L, 1_700_000_000_012L)));
        assertEquals(3, lignes());
    }

    @Test
    void laMemeMesureDeuxFoisDansUnLotNEstStockeeQuUneFois() {
        sink.recordAll(List.of(
                mesure().withSource(1, 20L, 1_700_000_000_020L).withReadingId(ID_A),
                mesure().withSource(1, 21L, 1_700_000_000_021L).withReadingId(ID_A),
                mesure().withSource(1, 22L, 1_700_000_000_022L).withReadingId(ID_B)));
        assertEquals(2, lignes());
    }

    @Test
    void unLotVideNeFaitRien() {
        sink.recordAll(List.of());
        assertEquals(0, lignes());
    }

    @Test
    void uneErreurQuiNEstPasUnDoublonRemontePourQueKafkaRejoueLeLot() {
        Reading sansType = new Reading(SENSOR, 3L, null, 2.4, "bar", null, T).withSource(1, 31L, 1_700_000_000_031L);
        assertThrows(DataIntegrityViolationException.class, () -> sink.recordAll(List.of(
                mesure().withSource(1, 30L, 1_700_000_000_030L), sansType)));
        // Lot rejoue par Kafka (sans la mesure fautive ici) : la mesure deja ecrite n'est pas doublee.
        sink.recordAll(List.of(mesure().withSource(1, 30L, 1_700_000_000_030L)));
        assertEquals(1, lignes());
    }
}
