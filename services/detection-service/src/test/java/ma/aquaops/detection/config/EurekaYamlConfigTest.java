package ma.aquaops.detection.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

/**
 * Configuration livree dans application.yml : adresse du registre par variable d'environnement (aucune valeur
 * en dur), reglages Docker Compose et Kubernetes, arret gracieux et sondes. Ne demarre pas Spring.
 */
class EurekaYamlConfigTest {

    private static Properties yaml() {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource("application.yml"));
        return factory.getObject();
    }

    @Test
    void lAdresseDuRegistreVientDeEUREKA_URLAvecUneValeurLocaleParDefaut() {
        assertEquals("${EUREKA_URL:http://localhost:8761/eureka}",
            yaml().getProperty("eureka.client.service-url.defaultZone"));
    }

    @Test
    void lInscriptionPeutEtreDesactiveeParVariable() {
        assertEquals("${EUREKA_ENABLED:true}", yaml().getProperty("eureka.client.enabled"));
    }

    @Test
    void lIdentifiantDInstanceUtiliseLeNomDuPodOuDuConteneur() {
        String id = yaml().getProperty("eureka.instance.instance-id");
        assertTrue(id.contains("${spring.application.name}"));
        assertTrue(id.contains("${HOSTNAME:"), "HOSTNAME = nom du pod sous Kubernetes");
        assertTrue(id.contains("${server.port}"));
    }

    @Test
    void lesDelaisDeRenouvellementEtDExpirationSontCourts() {
        Properties p = yaml();
        assertEquals("true", p.getProperty("eureka.instance.prefer-ip-address"));
        assertEquals("5", p.getProperty("eureka.instance.lease-renewal-interval-in-seconds"));
        assertEquals("15", p.getProperty("eureka.instance.lease-expiration-duration-in-seconds"));
    }

    @Test
    void uneInterruptionDuRegistreNeRendPasLeServiceDOWN() {
        Properties p = yaml();
        assertEquals("false", p.getProperty("management.health.eureka.enabled"));
        assertEquals("false", p.getProperty("management.health.discoveryComposite.enabled"));
    }

    @Test
    void leNomDApplicationEstDefini() {
        assertEquals("detection-service", yaml().getProperty("spring.application.name"));
    }

    @Test
    void lesConfigurationsExistantesSontConservees() {
        Properties p = yaml();
        assertTrue(p.getProperty("spring.datasource.url").contains("DETECTION_DB_URL"));
        assertTrue(p.getProperty("spring.kafka.bootstrap-servers").contains("KAFKA_BOOTSTRAP_SERVERS"));
        assertTrue(p.getProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri").contains("KEYCLOAK_ISSUER_URI"));
    }

    @Test
    void lArretEstGracieuxEtLesSondesKubernetesSontActives() {
        Properties p = yaml();
        assertEquals("graceful", p.getProperty("server.shutdown"));
        assertEquals("${SHUTDOWN_TIMEOUT:30s}", p.getProperty("spring.lifecycle.timeout-per-shutdown-phase"));
        assertEquals("true", p.getProperty("management.endpoint.health.probes.enabled"));
        assertEquals("health,prometheus", p.getProperty("management.endpoints.web.exposure.include"));
    }
}
