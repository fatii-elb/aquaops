package ma.aquaops.detection.anomaly;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.UUID;
import ma.aquaops.detection.model.Anomaly;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class AnomalyStoreTests {

    private static final Instant T0 = Instant.parse("2026-11-09T10:00:00Z");

    @Autowired AnomalyStore store;
    @Autowired StoredAnomalyRepository repository;

    private Anomaly event(String id, String type, int zone, Integer sensor) {
        return new Anomaly("1", id, type, zone, sensor, T0, T0.plusSeconds(2));
    }

    private static String id() {
        return UUID.randomUUID().toString();
    }

    @Test
    void enregistreUneAnomalieOuverte() {
        String id = id();

        assertTrue(store.record(event(id, "LEAK_SUSPECTED", 8001, 12)));

        StoredAnomaly stored = repository.findAll().stream()
            .filter(a -> a.anomalyId.equals(id)).findFirst().orElseThrow();
        assertEquals(AnomalyStatus.OPEN, stored.status);
        assertEquals("LEAK_SUSPECTED:8001", stored.dedupKey);
        assertEquals(T0.plusSeconds(2), stored.detectedAt);
        assertNull(stored.resolvedAt);
    }

    @Test
    void ignoreUnEvenementDejaVu() {
        String id = id();

        assertTrue(store.record(event(id, "PUMP_FAULT", 8002, 5)));
        assertFalse(store.record(event(id, "PUMP_FAULT", 8002, 5)));

        assertEquals(1, repository.findAll().stream().filter(a -> a.anomalyId.equals(id)).count());
    }

    @Test
    void neCreePasUneSecondeAnomalieOuvertePourLaMemeCle() {
        assertTrue(store.record(event(id(), "LEAK_SUSPECTED", 8003, 1)));
        assertFalse(store.record(event(id(), "LEAK_SUSPECTED", 8003, 2)));

        assertEquals(1, repository.findAll().stream().filter(a -> a.zoneId == 8003).count());
    }

    @Test
    void deuxCapteursSilencieuxDeLaMemeZoneSontDeuxAnomalies() {
        assertTrue(store.record(event(id(), "SENSOR_SILENT", 8004, 1)));
        assertTrue(store.record(event(id(), "SENSOR_SILENT", 8004, 2)));

        assertEquals(2, repository.findAll().stream().filter(a -> a.zoneId == 8004).count());
    }

    @Test
    void ignoreUnEvenementIncomplet() {
        assertFalse(store.record(null));
        assertFalse(store.record(new Anomaly("1", id(), "LEAK_SUSPECTED", null, 1, T0, T0)));
        assertFalse(store.record(new Anomaly("1", id(), "LEAK_SUSPECTED", 8005, 1, T0, null)));
    }

    @Test
    void leRetourALaNormaleResoutPuisAutoriseUneNouvelleAnomalie() {
        String first = id();
        store.record(event(first, "PUMP_FAULT", 8006, 5));

        store.onRecovered(8006, "PUMP_FAULT");

        StoredAnomaly resolved = repository.findAll().stream()
            .filter(a -> a.anomalyId.equals(first)).findFirst().orElseThrow();
        assertEquals(AnomalyStatus.RESOLVED, resolved.status);
        assertNotNull(resolved.resolvedAt);
        assertTrue(store.record(event(id(), "PUMP_FAULT", 8006, 5)));
    }

    @Test
    void laRepriseDuCapteurResoutSeulementSonAnomalieDeSilence() {
        String silentOne = id();
        String silentTwo = id();
        store.record(event(silentOne, "SENSOR_SILENT", 8007, 1));
        store.record(event(silentTwo, "SENSOR_SILENT", 8007, 2));

        store.onSensorRecovered(8007, 1);

        assertEquals(AnomalyStatus.RESOLVED, status(silentOne));
        assertEquals(AnomalyStatus.OPEN, status(silentTwo));
    }

    @Test
    void resoudreUneAnomalieInconnueNeFaitRien() {
        assertDoesNotThrow(() -> store.onRecovered(8008, "LEAK_SUSPECTED"));
        assertDoesNotThrow(() -> store.onSensorRecovered(8008, 99));
    }

    private AnomalyStatus status(String anomalyId) {
        return repository.findAll().stream()
            .filter(a -> a.anomalyId.equals(anomalyId)).findFirst().orElseThrow().status;
    }
}
