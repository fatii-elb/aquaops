package ma.aquaops.detection.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** NFR-07 : sondes de vivacite et de disponibilite ouvertes sans jeton (Kubernetes), arret gracieux actif. */
@SpringBootTest
class HealthProbesTests {

    @Autowired WebApplicationContext wac;
    @Autowired Environment env;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(wac).apply(springSecurity()).build();
    }

    @Test
    void laSondeDeVivaciteRepondUPSansJeton() throws Exception {
        mvc().perform(get("/actuator/health/liveness"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void laSondeDeDisponibiliteRepondUPSansJeton() throws Exception {
        mvc().perform(get("/actuator/health/readiness"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void lArretGracieuxEstConfigure() {
        assertEquals("graceful", env.getProperty("server.shutdown"));
        assertEquals("30s", env.getProperty("spring.lifecycle.timeout-per-shutdown-phase"));
    }
}
