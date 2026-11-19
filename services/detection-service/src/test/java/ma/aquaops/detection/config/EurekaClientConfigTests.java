package ma.aquaops.detection.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.netflix.eureka.EurekaClientConfigBean;
import org.springframework.cloud.netflix.eureka.EurekaInstanceConfigBean;
import org.springframework.test.annotation.DirtiesContext;

/**
 * [TECH] Client Eureka : la configuration est lue correctement. Le client est actif mais n'ouvre aucune
 * connexion (ni inscription ni lecture du registre) : le test ne depend pas d'un serveur Eureka.
 * Le statut UP dans le registre reel reste a verifier avec le serveur d'E5 (voir README_INTEGRATION.md).
 */
@SpringBootTest(properties = {
    "eureka.client.enabled=true",
    "eureka.client.register-with-eureka=false",
    "eureka.client.fetch-registry=false",
    "eureka.client.service-url.defaultZone=http://eureka.test:8761/eureka"
})
@DirtiesContext
class EurekaClientConfigTests {

    @Autowired EurekaInstanceConfigBean instance;
    @Autowired EurekaClientConfigBean client;

    @Test
    void leServiceSInscritSousSonNomDApplication() {
        assertEquals("detection-service", instance.getAppname().toLowerCase());
    }

    @Test
    void lInscriptionSeFaitParAdresseIp() {
        assertTrue(instance.isPreferIpAddress());
    }

    @Test
    void chaqueInstanceAUnIdentifiantUniqueContenantLeNomDeLHoteEtLePort() {
        String id = instance.getInstanceId();
        assertNotNull(id);
        assertTrue(id.startsWith("detection-service:"), id);
        assertTrue(id.endsWith(":8083"), id);
    }

    @Test
    void lesDelaisPermettentUneDisparitionEn45SecondesAuPlus() {
        assertEquals(5, instance.getLeaseRenewalIntervalInSeconds());
        assertEquals(15, instance.getLeaseExpirationDurationInSeconds());
        assertTrue(instance.getLeaseExpirationDurationInSeconds() <= 45);
    }

    @Test
    void lAdresseDuRegistreVientDeLaConfiguration() {
        assertEquals("http://eureka.test:8761/eureka", client.getServiceUrl().get("defaultZone"));
        assertFalse(client.isRegisterWithEureka());
    }
}
