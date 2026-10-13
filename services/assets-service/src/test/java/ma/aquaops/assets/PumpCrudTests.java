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
class PumpCrudTests {

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
    void pompeCrudComplet() throws Exception {
        long zone = create("/api/assets/zones", "{\"name\":\"Z\"}");
        long id = create("/api/assets/pumps", "{\"name\":\"PO1\",\"zoneId\":" + zone + "}");
        mvc.perform(get("/api/assets/pumps/" + id)).andExpect(status().isOk());
        mvc.perform(put("/api/assets/pumps/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"PO1b\",\"zoneId\":" + zone + "}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("PO1b"));
        mvc.perform(get("/api/assets/pumps")).andExpect(status().isOk());
        mvc.perform(delete("/api/assets/pumps/" + id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/assets/pumps/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void pompeZoneInexistanteRefusee() throws Exception {
        mvc.perform(post("/api/assets/pumps").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"PO1\",\"zoneId\":999999}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void pompeSansNomRefusee() throws Exception {
        long zone = create("/api/assets/zones", "{\"name\":\"Z\"}");
        mvc.perform(post("/api/assets/pumps").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"zoneId\":" + zone + "}"))
            .andExpect(status().isBadRequest());
    }
}
