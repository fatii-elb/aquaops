package ma.aquaops.incidents.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import ma.aquaops.incidents.state.IncidentStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Test d'integration JPA de l'historique (SCRUM-38) contre un vrai PostgreSQL.
 * Ne tourne que si AQUAOPS_IT=1, avec INCIDENTS_DB_URL, DB_USER et DB_PASSWORD pointant
 * vers une base JETABLE (jamais la base de dev) :
 *     AQUAOPS_IT=1 INCIDENTS_DB_URL=... DB_USER=... DB_PASSWORD=... ./mvnw test -Dtest=IncidentHistoryRepositoryIT
 * Kafka n'est pas utilise : les consommateurs ne demarrent pas et l'adresse ne mene nulle part.
 * Chaque test est annule a la fin (@Transactional).
 */
@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.bootstrap-servers=127.0.0.1:1"
})
@EnabledIfEnvironmentVariable(named = "AQUAOPS_IT", matches = "1")
@Transactional
class IncidentHistoryRepositoryIT {

    private static final Instant T1 = Instant.parse("2026-11-04T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-11-04T10:05:00Z");
    private static final Instant T3 = Instant.parse("2026-11-04T10:10:00Z");

    @Autowired
    IncidentHistoryRepository repository;

    @Autowired
    IncidentHistoryRecorder recorderDuContexte;

    @Autowired
    Clock clock;

    @Autowired
    JdbcTemplate jdbc;

    /** Identifiant d'incident propre au test : aucune donnee supposee deja en base. */
    private static long unIncident() {
        return ThreadLocalRandom.current().nextLong(1_000_000_000L, Long.MAX_VALUE);
    }

    private IncidentHistoryRecorder recorderA(Instant heure) {
        return new IncidentHistoryRecorder(repository, Clock.fixed(heure, ZoneOffset.UTC));
    }

    @Test
    void leContexteDemarreAvecLHorlogeUtcEtLeRecorder() {
        assertNotNull(recorderDuContexte);
        assertEquals(ZoneOffset.UTC, clock.getZone());
    }

    @Test
    void laTableIncidentHistoryExisteAvecSesColonnes() {
        // Agir
        List<Map<String, Object>> colonnes = jdbc.queryForList(
                "select column_name, is_nullable from information_schema.columns "
                        + "where table_schema = 'public' and table_name = 'incident_history' order by column_name");

        // Verifier
        Map<String, String> nullable = new java.util.TreeMap<>();
        colonnes.forEach(c -> nullable.put((String) c.get("column_name"), (String) c.get("is_nullable")));
        assertEquals(Map.of(
                "action", "NO", "author", "NO", "comment", "YES", "from_status", "YES",
                "id", "NO", "incident_id", "NO", "occurred_at", "NO", "to_status", "YES"), nullable);
        Integer index = jdbc.queryForObject(
                "select count(*) from pg_indexes where tablename = 'incident_history' "
                        + "and indexname = 'idx_incident_history_incident_time'", Integer.class);
        assertEquals(1, index);
    }

    @Test
    void uneLigneValideEstEnregistreeEtRelue() {
        // Preparer
        long incident = unIncident();

        // Agir
        IncidentHistoryEntry saved = recorderA(T1).recordStatusChange(
                incident, IncidentAction.CONFIRMED, IncidentStatus.OPEN, IncidentStatus.CONFIRMED,
                "superviseur1", "fuite confirmee sur place");
        repository.flush();
        List<IncidentHistoryEntry> lu = repository.findByIncidentIdOrderByOccurredAtAscIdAsc(incident);

        // Verifier
        assertNotNull(saved.getId());
        assertEquals(1, lu.size());
        IncidentHistoryEntry e = lu.get(0);
        assertEquals(saved.getId(), e.getId());
        assertEquals(incident, e.getIncidentId());
        assertEquals(IncidentAction.CONFIRMED, e.getAction());
        assertEquals(IncidentStatus.OPEN, e.getFromStatus());
        assertEquals(IncidentStatus.CONFIRMED, e.getToStatus());
        assertEquals("superviseur1", e.getAuthor());
        assertEquals("fuite confirmee sur place", e.getComment());
        assertEquals(T1, e.getOccurredAt());
        // Relu en SQL : les valeurs sont vraiment en base, pas seulement dans le cache JPA.
        Map<String, Object> ligne = jdbc.queryForMap(
                "select action, from_status, to_status, author from incident_history where id = ?", saved.getId());
        assertEquals(Map.of("action", "CONFIRMED", "from_status", "OPEN", "to_status", "CONFIRMED",
                "author", "superviseur1"), ligne);
    }

    @Test
    void lHistoriqueEstRenduDuPlusAncienAuPlusRecent() {
        // Preparer : enregistre volontairement dans le desordre
        long incident = unIncident();
        IncidentHistoryEntry troisieme = recorderA(T3).recordStatusChange(
                incident, IncidentAction.CLOSED, IncidentStatus.CONFIRMED, IncidentStatus.CLOSED, "superviseur1", null);
        IncidentHistoryEntry premiere = recorderA(T1).recordStatusChange(
                incident, IncidentAction.CONFIRMED, IncidentStatus.OPEN, IncidentStatus.CONFIRMED, "superviseur1", null);
        IncidentHistoryEntry deuxieme = recorderA(T2).recordComment(incident, "technicien1", "vanne fermee");
        repository.flush();

        // Agir
        List<IncidentHistoryEntry> lu = repository.findByIncidentIdOrderByOccurredAtAscIdAsc(incident);

        // Verifier
        assertEquals(List.of(premiere.getId(), deuxieme.getId(), troisieme.getId()),
                lu.stream().map(IncidentHistoryEntry::getId).toList());
    }

    @Test
    void aHeureEgaleLOrdreDInsertionDepartage() {
        // Preparer : trois lignes a la meme heure
        long incident = unIncident();
        IncidentHistoryRecorder memeHeure = recorderA(T2);
        IncidentHistoryEntry a = memeHeure.recordComment(incident, "technicien1", "a");
        IncidentHistoryEntry b = memeHeure.recordComment(incident, "technicien1", "b");
        IncidentHistoryEntry c = memeHeure.recordComment(incident, "technicien1", "c");
        repository.flush();
        assertTrue(a.getId() < b.getId() && b.getId() < c.getId(), "ids croissants attendus");

        // Agir
        List<IncidentHistoryEntry> lu = repository.findByIncidentIdOrderByOccurredAtAscIdAsc(incident);

        // Verifier
        assertEquals(List.of("a", "b", "c"), lu.stream().map(IncidentHistoryEntry::getComment).toList());
    }

    @Test
    void lesLignesDUnAutreIncidentSontExclues() {
        // Preparer
        long incident = unIncident();
        long autre = incident - 1;
        recorderA(T1).recordComment(incident, "technicien1", "le mien");
        recorderA(T1).recordComment(autre, "technicien1", "un autre incident");
        recorderA(T2).recordComment(autre, "technicien1", "encore un autre");
        repository.flush();

        // Agir
        List<IncidentHistoryEntry> lu = repository.findByIncidentIdOrderByOccurredAtAscIdAsc(incident);

        // Verifier
        assertEquals(List.of("le mien"), lu.stream().map(IncidentHistoryEntry::getComment).toList());
        assertEquals(2, repository.findByIncidentIdOrderByOccurredAtAscIdAsc(autre).size());
    }
}
