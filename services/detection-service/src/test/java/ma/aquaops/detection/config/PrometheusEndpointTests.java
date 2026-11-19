package ma.aquaops.detection.config;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics
@SpringBootTest
class PrometheusEndpointTests {

    @Autowired
    WebApplicationContext wac;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(wac)
                .apply(springSecurity())
                .build();
    }

    @Test
    void prometheusEstAccessibleSansJeton() throws Exception {
        mvc().perform(get("/actuator/prometheus"))
                .andExpect(status().isOk());
    }

    @Test
    void prometheusExposeLesMetriquesEtLeLabelApplication() throws Exception {
        String body = mvc().perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(body.contains("jvm_memory_used_bytes"),
                "Metrique memoire JVM absente");
        assertTrue(body.contains("process_cpu_usage"),
                "Metrique CPU absente");
        assertTrue(body.contains("application=\"detection-service\""),
                "Label application absent");
    }

    @Test
    void lesAutresEndpointsNeSontPasOuverts() throws Exception {
        mvc().perform(get("/actuator/env"))
                .andExpect(status().isUnauthorized());
    }
}
