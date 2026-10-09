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
class AssetsCrudTests {

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
    void zoneCrudComplet() throws Exception {
        long id = create("/api/assets/zones", "{\"name\":\"Zone Nord\",\"description\":\"test\"}");
        mvc.perform(get("/api/assets/zones/" + id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Zone Nord"));
        mvc.perform(put("/api/assets/zones/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Zone Sud\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Zone Sud"));
        mvc.perform(get("/api/assets/zones")).andExpect(status().isOk());
        mvc.perform(delete("/api/assets/zones/" + id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/assets/zones/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void zoneSansNomRefusee() throws Exception {
        mvc.perform(post("/api/assets/zones").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void conduiteCrudEtZoneInexistante() throws Exception {
        mvc.perform(post("/api/assets/pipes").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"C1\",\"zoneId\":999999}"))
            .andExpect(status().isBadRequest());

        long zone = create("/api/assets/zones", "{\"name\":\"Z\"}");
        long id = create("/api/assets/pipes",
            "{\"name\":\"C1\",\"zoneId\":" + zone + ",\"lengthM\":120.5,\"diameterMm\":200}");
        mvc.perform(get("/api/assets/pipes/" + id)).andExpect(status().isOk());
        mvc.perform(put("/api/assets/pipes/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"C1b\",\"zoneId\":" + zone + "}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("C1b"));
        mvc.perform(delete("/api/assets/pipes/" + id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/assets/pipes/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void reservoirCrudEtZoneInexistante() throws Exception {
        mvc.perform(post("/api/assets/tanks").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"R1\",\"zoneId\":999999}"))
            .andExpect(status().isBadRequest());

        long zone = create("/api/assets/zones", "{\"name\":\"Z\"}");
        long id = create("/api/assets/tanks",
            "{\"name\":\"R1\",\"zoneId\":" + zone + ",\"capacityM3\":500}");
        mvc.perform(get("/api/assets/tanks/" + id)).andExpect(status().isOk());
        mvc.perform(put("/api/assets/tanks/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"R1b\",\"zoneId\":" + zone + "}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("R1b"));
        mvc.perform(delete("/api/assets/tanks/" + id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/assets/tanks/" + id)).andExpect(status().isNotFound());
    }
}
