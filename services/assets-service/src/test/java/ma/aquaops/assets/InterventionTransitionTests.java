package ma.aquaops.assets;

import static ma.aquaops.assets.model.InterventionStatus.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Test pur (sans Spring, sans base) des transitions de statut. */
class InterventionTransitionTests {

    @Test
    void transitionsValides() {
        assertTrue(PLANNED.canMoveTo(IN_PROGRESS));
        assertTrue(IN_PROGRESS.canMoveTo(DONE));
    }

    @Test
    void toutesLesAutresTransitionsSontRefusees() {
        assertFalse(PLANNED.canMoveTo(PLANNED));
        assertFalse(PLANNED.canMoveTo(DONE));        // pas de saut
        assertFalse(IN_PROGRESS.canMoveTo(PLANNED)); // pas de retour
        assertFalse(IN_PROGRESS.canMoveTo(IN_PROGRESS));
        assertFalse(DONE.canMoveTo(PLANNED));
        assertFalse(DONE.canMoveTo(IN_PROGRESS));
        assertFalse(DONE.canMoveTo(DONE));
    }
}
