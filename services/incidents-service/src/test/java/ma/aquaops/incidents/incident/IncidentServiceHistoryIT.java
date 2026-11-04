package ma.aquaops.incidents.incident;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import ma.aquaops.incidents.history.IncidentHistoryRecorder;
import ma.aquaops.incidents.kafka.IncidentEventPublisher;
import ma.aquaops.incidents.state.IncidentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * SCRUM-38 : IncidentService.update contre un vrai PostgreSQL, a travers le proxy Spring.
 * PAS de @Transactional sur la classe : chaque appel au service valide (commit) ou annule
 * (rollback) pour de vrai, et les verifications relisent la base en SQL apres coup.
 * Ne tourne que si AQUAOPS_IT=1, avec INCIDENTS_DB_URL, DB_USER et DB_PASSWORD pointant vers une
 * base JETABLE :  AQUAOPS_IT=1 INCIDENTS_DB_URL=... ./mvnw test -Dtest=IncidentServiceHistoryIT
 * Kafka : consommateurs arretes, et le publieur est remplace par un mock (aucun envoi reel).
 */
@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.bootstrap-servers=127.0.0.1:1"
})
@EnabledIfEnvironmentVariable(named = "AQUAOPS_IT", matches = "1")
class IncidentServiceHistoryIT {

    @Autowired
    IncidentService service;

    @Autowired
    IncidentRepository incidents;

    @Autowired
    IncidentHistoryRecorder recorder;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    IncidentEventPublisher publisher;

    @BeforeEach
    void resetPublisher() {
        reset(publisher);
    }

    /** Incident OPEN enregistre (et valide) avant le test, dans une zone propre au test. */
    private Incident incidentOuvert() {
        Incident i = new Incident();
        i.setZoneId(ThreadLocalRandom.current().nextLong(1_000_000L, 9_000_000_000L));
        i.setType("PUMP_FAULT");
        i.setStatus(IncidentStatus.OPEN);
        i.setPriority(Priority.HIGH);
        i.setDescription("SCRUM-38 IT");
        return incidents.save(i);
    }

    private String statutEnBase(Long id) {
        return jdbc.queryForObject("select status from incidents where id = ?", String.class, id);
    }

    private List<Map<String, Object>> historiqueEnBase(Long id) {
        return jdbc.queryForList("select action, from_status, to_status, author, comment, occurred_at "
                + "from incident_history where incident_id = ? order by occurred_at, id", id);
    }

    @Test
    void contexteDemarreAvecLeServiceTransactionnelEtLeRecorder() {
        assertNotNull(recorder);
        assertTrue(AopUtils.isAopProxy(service), "IncidentService doit etre un proxy Spring (@Transactional)");
    }

    @Test
    void transitionValideEnregistreLIncidentEtUneLigneDHistorique() {
        // Preparer
        Incident incident = incidentOuvert();
        Instant avant = Instant.now().truncatedTo(ChronoUnit.MICROS);

        // Agir : a travers le proxy, la transaction est validee a la sortie
        service.update(incident.getId(), IncidentStatus.CONFIRMED, null, "superviseur-it");
        Instant apres = Instant.now();

        // Verifier, en relisant la base
        assertEquals("CONFIRMED", statutEnBase(incident.getId()));
        List<Map<String, Object>> lignes = historiqueEnBase(incident.getId());
        assertEquals(1, lignes.size(), "exactement une ligne d'historique");
        Map<String, Object> l = lignes.get(0);
        assertEquals("CONFIRMED", l.get("action"));
        assertEquals("OPEN", l.get("from_status"));
        assertEquals("CONFIRMED", l.get("to_status"));
        assertEquals("superviseur-it", l.get("author"));
        assertEquals(null, l.get("comment"));
        Instant quand = ((Timestamp) l.get("occurred_at")).toInstant();
        assertFalse(quand.isBefore(avant) || quand.isAfter(apres),
                "occurred_at " + quand + " hors de [" + avant + ", " + apres + "]");
        verify(publisher).publish(any(Incident.class), eq("INCIDENT_CONFIRMED"), eq("superviseur-it"));
    }

    /** Auteur qui declenche le trigger de test ; aucun vrai utilisateur ne s'appelle ainsi. */
    private static final String AUTEUR_REFUSE = "it-scrum38-reject";

    @Test
    void echecSqlDeLHistoriqueAnnuleAussiLeChangementDeStatut() {
        // Securite : le trigger de test n'est installe que dans une base jetable (nom en _it).
        String base = jdbc.queryForObject("select current_database()", String.class);
        assumeTrue(base != null && base.endsWith("_it"), "base non jetable (" + base + ") : test ignore");

        // Preparer : trigger de TEST, dans la base temporaire seulement. Avant l'INSERT d'historique,
        // il lit le statut de l'incident DANS la transaction en cours, puis leve une vraie erreur SQL.
        jdbc.execute("""
                create or replace function it_scrum38_reject_history() returns trigger as $$
                declare vu text;
                begin
                  if new.author = '%s' then
                    select status into vu from incidents where id = new.incident_id;
                    raise exception 'it_scrum38: insert refuse, statut vu dans la transaction = %%', vu
                      using errcode = '23514';
                  end if;
                  return new;
                end $$ language plpgsql""".formatted(AUTEUR_REFUSE));
        jdbc.execute("create trigger it_scrum38_reject_history before insert on incident_history "
                + "for each row execute function it_scrum38_reject_history()");
        try {
            Incident incident = incidentOuvert();

            // Agir : a travers le proxy Spring
            DataIntegrityViolationException e = assertThrows(DataIntegrityViolationException.class,
                    () -> service.update(incident.getId(), IncidentStatus.CONFIRMED, null, AUTEUR_REFUSE));

            // Verifier : l'UPDATE etait arrive dans PostgreSQL avant l'echec de l'INSERT...
            String cause = e.getMostSpecificCause().getMessage();
            assertTrue(cause.contains("statut vu dans la transaction = CONFIRMED"), cause);
            // ... et la transaction a tout annule : statut d'origine, aucune ligne, aucun evenement.
            assertEquals("OPEN", statutEnBase(incident.getId()), "le statut doit etre annule");
            assertEquals(0, historiqueEnBase(incident.getId()).size(), "aucune ligne d'historique validee");
            verify(publisher, never()).publish(any(), any(), any());
        } finally {
            jdbc.execute("drop trigger if exists it_scrum38_reject_history on incident_history");
            jdbc.execute("drop function if exists it_scrum38_reject_history()");
        }
        assertEquals(0, jdbc.queryForObject(
                "select count(*) from pg_trigger where tgname = 'it_scrum38_reject_history'", Integer.class));
    }

    @Test
    void auteurDe256CaracteresEstRefuseAvantLaBaseEtNeChangeRien() {
        // Preparer
        Incident incident = incidentOuvert();
        String tropLong = "a".repeat(256);

        // Agir et Verifier : refus Java (400 cote API), la transaction annule l'UPDATE deja envoye
        assertThrows(IllegalArgumentException.class,
                () -> service.update(incident.getId(), IncidentStatus.CONFIRMED, null, tropLong));
        assertEquals("OPEN", statutEnBase(incident.getId()));
        assertEquals(0, historiqueEnBase(incident.getId()).size());
        verify(publisher, never()).publish(any(), any(), any());
    }

    @Test
    void transitionRefuseeNeChangeRienEtNEcritPasDHistorique() {
        // Preparer
        Incident incident = incidentOuvert();

        // Agir et Verifier : OPEN -> RESOLVED est refuse par IncidentStateMachine
        assertThrows(InvalidTransitionException.class,
                () -> service.update(incident.getId(), IncidentStatus.RESOLVED, null, "superviseur-it"));
        assertEquals("OPEN", statutEnBase(incident.getId()));
        assertEquals(0, historiqueEnBase(incident.getId()).size());
        verify(publisher, never()).publish(any(), any(), any());
    }

    @Test
    void changementDeStatutSansAuteurNeChangeRien() {
        // Preparer
        Incident incident = incidentOuvert();

        // Agir et Verifier
        assertThrows(IllegalArgumentException.class,
                () -> service.update(incident.getId(), IncidentStatus.CONFIRMED, null, "  "));
        assertEquals("OPEN", statutEnBase(incident.getId()));
        assertEquals(0, historiqueEnBase(incident.getId()).size());
        verify(publisher, never()).publish(any(), any(), any());
    }

    @Test
    void deuxTransitionsDonnentDeuxLignesDansLOrdre() {
        // Preparer
        Incident incident = incidentOuvert();

        // Agir : deux transactions validees l'une apres l'autre
        service.update(incident.getId(), IncidentStatus.CONFIRMED, null, "superviseur-it");
        service.update(incident.getId(), IncidentStatus.ASSIGNED, "technicien-it", "superviseur-it");

        // Verifier
        assertEquals("ASSIGNED", statutEnBase(incident.getId()));
        assertEquals("technicien-it", jdbc.queryForObject(
                "select assignee from incidents where id = ?", String.class, incident.getId()));
        List<Map<String, Object>> lignes = historiqueEnBase(incident.getId());
        assertEquals(List.of("CONFIRMED", "ASSIGNED"), lignes.stream().map(l -> l.get("action")).toList());
        assertEquals(List.of("OPEN", "CONFIRMED"), lignes.stream().map(l -> l.get("from_status")).toList());
    }
}
