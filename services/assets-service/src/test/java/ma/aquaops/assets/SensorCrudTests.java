package ma.aquaops.assets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Transactional
class SensorCrudTests {

    @Autowired WebApplicationContext wac;
    MockMvc mvc;

    @BeforeEach
    void setup() { mvc = MockMvcBuilders.webAppContextSetup(wac).build(); }

    private long create(String url, String json) throws Exception {
        String body = mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        Matcher m = Pattern.compile("\"id\":(\\d+)").matcher(body);
        m.find();
        return Long.parseLong(m.group(1));
    }

    @Test
    void capteurCrudComplet() throws Exception {
        long zone = create("/api/assets/zones", "{\"name\":\"Z\"}");
        long id = create("/api/assets/sensors",
            "{\"name\":\"P1\",\"type\":\"PRESSURE\",\"zoneId\":" + zone + "}");
        mvc.perform(get("/api/assets/sensors/" + id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.type").value("PRESSURE"));
        mvc.perform(put("/api/assets/sensors/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"P1b\",\"type\":\"FLOW\",\"zoneId\":" + zone + "}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("P1b"))
            .andExpect(jsonPath("$.type").value("FLOW"));
        mvc.perform(get("/api/assets/sensors")).andExpect(status().isOk());
        mvc.perform(delete("/api/assets/sensors/" + id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/assets/sensors/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void capteurZoneInexistanteRefuse() throws Exception {
        mvc.perform(post("/api/assets/sensors").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"P1\",\"type\":\"PRESSURE\",\"zoneId\":999999}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void capteurSansZoneRefuse() throws Exception {
        mvc.perform(post("/api/assets/sensors").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"P1\",\"type\":\"PRESSURE\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void capteurSansNomRefuse() throws Exception {
        long zone = create("/api/assets/zones", "{\"name\":\"Z\"}");
        mvc.perform(post("/api/assets/sensors").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"type\":\"PRESSURE\",\"zoneId\":" + zone + "}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void capteurTypeInvalideRefuse() throws Exception {
        long zone = create("/api/assets/zones", "{\"name\":\"Z\"}");
        mvc.perform(post("/api/assets/sensors").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"P1\",\"type\":\"TEMPERATURE\",\"zoneId\":" + zone + "}"))
            .andExpect(status().isBadRequest());
    }
}
