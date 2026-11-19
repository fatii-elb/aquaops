package ma.aquaops.detection.threshold;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.function.DoubleSupplier;
import ma.aquaops.detection.engine.DetectionEngine;
import ma.aquaops.detection.model.SensorReading;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * FR-13 : rechargement des seuils sans redemarrage. Cycle ramene a 200 ms pour le test (15 s en production,
 * exigence : 30 s au plus). Pas de @Transactional : les seuils doivent etre valides en base pour que la tache
 * planifiee, qui tourne dans un autre thread, les voie.
 */
@SpringBootTest(properties = "detection.thresholds.refresh-ms=200")
class ThresholdReloadTests {

    private static final Instant T0 = Instant.parse("2026-12-10T10:00:00Z");

    @Autowired ZoneThresholdRepository repository;
    @Autowired ThresholdService service;
    @Autowired ThresholdCache cache;
    @Autowired DetectionEngine engine;

    @AfterEach
    void clean() {
        repository.deleteAllById(List.of(8201, 8202));
    }

    private static void await(DoubleSupplier value, double expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            if (value.getAsDouble() == expected) {
                return;
            }
            Thread.sleep(50);
        }
        fail("Le seuil n'a pas atteint " + expected + " (valeur : " + value.getAsDouble() + ")");
    }

    private ZoneThresholdEntity row(int zone, double minLevel) {
        ZoneThresholdEntity e = new ZoneThresholdEntity();
        e.zoneId = zone;
        e.leakMinPressure = 2.0;
        e.leakMaxFlow = 40.0;
        e.leakDurationSeconds = 10;
        e.silenceThresholdSeconds = 10;
        e.tankMinLevel = minLevel;
        e.updatedAt = Instant.now();
        e.updatedBy = "test";
        return e;
    }

    @Test
    void unSeuilModifieDirectementEnBaseEstRechargeSansRedemarrage() throws Exception {
        assertEquals(2.0, cache.forZone(8201).tankMinLevel());

        repository.saveAndFlush(row(8201, 6.0)); // modification faite par une autre instance du service

        await(() -> cache.forZone(8201).tankMinLevel(), 6.0);
        assertTrue(cache.isCustom(8201));

        repository.saveAndFlush(row(8201, 1.0));
        await(() -> cache.forZone(8201).tankMinLevel(), 1.0);
    }

    @Test
    void leMoteurDeDetectionUtiliseLeSeuilModifie() throws Exception {
        SensorReading before = new SensorReading("1", 3, 8202, "LEVEL", 3.0, "m", null, T0);
        SensorReading after = new SensorReading("1", 3, 8202, "LEVEL", 3.0, "m", null, T0.plusSeconds(1));

        // Seuil par defaut 2.0 : un niveau de 3.0 est normal
        assertTrue(engine.process(before).isEmpty());

        service.save(8202, new ThresholdBody(2.0, 40.0, 10L, 10L, 5.0), "test");
        await(() -> cache.forZone(8202).tankMinLevel(), 5.0);

        // Seuil porte a 5.0 : le meme niveau de 3.0 devient une anomalie
        List<?> anomalies = engine.process(after);
        assertEquals(1, anomalies.size());
    }

    @Test
    void ledelaiDeRechargementParDefautRespecteLExigenceDe30Secondes() {
        var yaml = new org.springframework.beans.factory.config.YamlPropertiesFactoryBean();
        yaml.setResources(new org.springframework.core.io.ClassPathResource("application.yml"));
        String raw = yaml.getObject().getProperty("detection.thresholds.refresh-ms");
        assertEquals("${THRESHOLDS_REFRESH_MS:15000}", raw);
        long refreshMs = Long.parseLong(raw.substring(raw.indexOf(':') + 1, raw.length() - 1));
        assertTrue(refreshMs <= 15_000, "rafraichissement : 15 s au plus");
        assertTrue(2 * refreshMs <= 30_000, "prise en compte : 30 s au plus");
    }
}
