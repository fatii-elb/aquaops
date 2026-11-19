package ma.aquaops.detection.kafka;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import ma.aquaops.detection.anomaly.AnomalyStatus;
import ma.aquaops.detection.anomaly.StoredAnomaly;
import ma.aquaops.detection.anomaly.StoredAnomalyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * FR-12 de bout en bout : une mesure LEVEL sous le seuil devient une anomalie LOW_LEVEL enregistree
 * (donc visible dans FE-9 via l'API), puis resolue quand le niveau remonte.
 */
@SpringBootTest(properties = {
    "detection.kafka.sensor-topic=test-lowlevel-sensor-readings",
    "detection.kafka.anomaly-topic=test-lowlevel-anomaly-detected",
    "detection.kafka.consumer-group=test-lowlevel-detection-group"
})
@EmbeddedKafka(
    partitions = 1,
    topics = {"test-lowlevel-sensor-readings", "test-lowlevel-anomaly-detected"},
    bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@DirtiesContext
class LowLevelKafkaIntegrationTest {

    @Autowired KafkaTemplate<String, String> template;
    @Autowired StoredAnomalyRepository repository;
    @Autowired WebApplicationContext wac;

    private static String level(double value, String measuredAt) {
        return """
            {"eventVersion":"1","sensorId":3,"zoneId":7101,"type":"LEVEL","value":%s,"unit":"m",
             "measuredAt":"%s"}
            """.formatted(value, measuredAt);
    }

    private StoredAnomaly awaitStatus(AnomalyStatus wanted) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            Optional<StoredAnomaly> found = repository.findAll().stream()
                .filter(a -> a.zoneId == 7101 && a.type.equals("LOW_LEVEL") && a.status == wanted).findFirst();
            if (found.isPresent()) {
                return found.get();
            }
            Thread.sleep(200);
        }
        fail("Aucune anomalie LOW_LEVEL de la zone 7101 au statut " + wanted);
        return null;
    }

    @Test
    void unNiveauBasCreeUneAnomalieVisibleDansLApiPuisElleSeResout() throws Exception {
        // Niveau normal (5 m, seuil par defaut 2 m) : aucune anomalie
        template.send("test-lowlevel-sensor-readings", "7101", level(5.0, "2026-12-16T10:00:00Z")).get(10, TimeUnit.SECONDS);
        // Niveau bas (0,5 m) puis plusieurs mesures basses : une seule anomalie
        for (int i = 1; i <= 5; i++) {
            template.send("test-lowlevel-sensor-readings", "7101", level(0.5, "2026-12-16T10:00:0" + i + "Z"))
                .get(10, TimeUnit.SECONDS);
        }

        StoredAnomaly open = awaitStatus(AnomalyStatus.OPEN);
        assertEquals(3, open.sensorId);
        assertNotNull(open.anomalyId);
        assertEquals(1, repository.findAll().stream().filter(a -> a.zoneId == 7101 && a.type.equals("LOW_LEVEL")).count());

        // Visible pour FE-9 : gravite faible, statut ouvert
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(wac).apply(springSecurity()).build();
        mvc.perform(get("/api/detection/anomalies").param("zoneId", "7101").param("type", "LOW_LEVEL")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_operateur"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].severity").value("LOW"))
            .andExpect(jsonPath("$.content[0].status").value("OPEN"));

        // Le niveau remonte : l'anomalie est resolue
        template.send("test-lowlevel-sensor-readings", "7101", level(5.0, "2026-12-16T10:01:00Z")).get(10, TimeUnit.SECONDS);
        StoredAnomaly resolved = awaitStatus(AnomalyStatus.RESOLVED);
        assertEquals(open.anomalyId, resolved.anomalyId);
        assertNotNull(resolved.resolvedAt);
    }
}
