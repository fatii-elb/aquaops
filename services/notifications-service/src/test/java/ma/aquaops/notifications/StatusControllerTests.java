package ma.aquaops.notifications;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class StatusControllerTests {

    @Test
    void statusIndiqueQueLeServiceFonctionne() {
        // Préparer
        StatusController controleur = new StatusController();
        // Agir
        var reponse = controleur.status();
        // Vérifier
        assertEquals("UP", reponse.get("status"));
        assertEquals("notifications-service", reponse.get("service"));
    }
}