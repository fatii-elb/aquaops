
package ma.aquaops.detection.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AnomalyDeduplicatorTest {

    @Test
    void shouldAllowOnlyOneAnomalyForHundredReadings() {

        AnomalyDeduplicator dedup =
                new AnomalyDeduplicator();

        int published = 0;

        for (int i = 0; i < 100; i++) {
            if (dedup.tryOpen(3, "PUMP_FAULT")) {
                published++;
            }
        }

        assertEquals(1, published);
    }

    @Test
    void shouldAllowNewAnomalyAfterRecovery() {

        AnomalyDeduplicator dedup =
                new AnomalyDeduplicator();

        assertTrue(dedup.tryOpen(3, "PUMP_FAULT"));

        assertFalse(dedup.tryOpen(3, "PUMP_FAULT"));

        dedup.recover(3, "PUMP_FAULT");

        assertTrue(dedup.tryOpen(3, "PUMP_FAULT"));
    }

    @Test
    void shouldKeepZonesIndependent() {

        AnomalyDeduplicator dedup =
                new AnomalyDeduplicator();

        assertTrue(dedup.tryOpen(3, "PUMP_FAULT"));

        assertTrue(dedup.tryOpen(4, "PUMP_FAULT"));
    }

    @Test
    void shouldKeepAnomalyTypesIndependent() {

        AnomalyDeduplicator dedup =
                new AnomalyDeduplicator();

        assertTrue(dedup.tryOpen(3, "PUMP_FAULT"));

        assertTrue(dedup.tryOpen(3, "LEAK_SUSPECTED"));
    }
}
