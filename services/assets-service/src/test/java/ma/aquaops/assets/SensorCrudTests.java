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

    private long createZone() throws Exception {
        return create("/api/assets/zones", "{\"name\":\"Z\"}");
    }

    private long createPump(long zone) throws Exception {
        return create("/api/assets/pumps", "{\"name\":\"Pompe1\",\"zoneId\":" + zone + "}");
    }

    private void postSensorExpectBadRequest(String json) throws Exception {
        mvc.perform(post("/api/assets/sensors").contentType(MediaType.APPLICATION_JSON).content(json))
            .andExpect(status().isBadRequest());
    }

    @Test
    void capteurCrudComplet() throws Exception {
        long zone = createZone();
        long pump = createPump(zone);
        long id = create("/api/assets/sensors",
            "{\"name\":\"P1\",\"type\":\"PRESSURE\",\"zoneId\":" + zone
                + ",\"assetType\":\"PUMP\",\"assetId\":" + pump + "}");
        mvc.perform(get("/api/assets/sensors/" + id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.type").value("PRESSURE"))
            .andExpect(jsonPath("$.assetType").value("PUMP"));
        mvc.perform(put("/api/assets/sensors/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"P1b\",\"type\":\"FLOW\",\"zoneId\":" + zone
                    + ",\"assetType\":\"PUMP\",\"assetId\":" + pump + "}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("P1b"))
            .andExpect(jsonPath("$.type").value("FLOW"));
        mvc.perform(get("/api/assets/sensors")).andExpect(status().isOk());
        mvc.perform(delete("/api/assets/sensors/" + id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/assets/sensors/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void capteurSurConduiteEtReservoirAccepte() throws Exception {
        long zone = createZone();
        long pipe = create("/api/assets/pipes",
            "{\"name\":\"C1\",\"zoneId\":" + zone + ",\"lengthM\":100,\"diameterMm\":200}");
        long tank = create("/api/assets/tanks",
            "{\"name\":\"R1\",\"zoneId\":" + zone + ",\"capacityM3\":500}");
        create("/api/assets/sensors",
            "{\"name\":\"S1\",\"type\":\"FLOW\",\"zoneId\":" + zone
                + ",\"assetType\":\"PIPE\",\"assetId\":" + pipe + "}");
        create("/api/assets/sensors",
            "{\"name\":\"S2\",\"type\":\"LEVEL\",\"zoneId\":" + zone
                + ",\"assetType\":\"TANK\",\"assetId\":" + tank + "}");
    }

    @Test
    void capteurSansZoneRefuse() throws Exception {
        long zone = createZone();
        long pump = createPump(zone);
        postSensorExpectBadRequest(
            "{\"name\":\"P1\",\"type\":\"PRESSURE\",\"assetType\":\"PUMP\",\"assetId\":" + pump + "}");
    }

    @Test
    void capteurSansActifRefuse() throws Exception {
        long zone = createZone();
        postSensorExpectBadRequest(
            "{\"name\":\"P1\",\"type\":\"PRESSURE\",\"zoneId\":" + zone + "}");
    }

    @Test
    void capteurSansAssetIdRefuse() throws Exception {
        long zone = createZone();
        postSensorExpectBadRequest(
            "{\"name\":\"P1\",\"type\":\"PRESSURE\",\"zoneId\":" + zone + ",\"assetType\":\"PUMP\"}");
    }

    @Test
    void capteurSansAssetTypeRefuse() throws Exception {
        long zone = createZone();
        long pump = createPump(zone);
        postSensorExpectBadRequest(
            "{\"name\":\"P1\",\"type\":\"PRESSURE\",\"zoneId\":" + zone + ",\"assetId\":" + pump + "}");
    }

    @Test
    void capteurZoneInexistanteRefuse() throws Exception {
        long zone = createZone();
        long pump = createPump(zone);
        postSensorExpectBadRequest(
            "{\"name\":\"P1\",\"type\":\"PRESSURE\",\"zoneId\":999999,\"assetType\":\"PUMP\",\"assetId\":" + pump + "}");
    }

    @Test
    void capteurActifInexistantRefuse() throws Exception {
        long zone = createZone();
        postSensorExpectBadRequest(
            "{\"name\":\"P1\",\"type\":\"PRESSURE\",\"zoneId\":" + zone
                + ",\"assetType\":\"PUMP\",\"assetId\":999999}");
    }

    @Test
    void capteurAssetTypeInvalideRefuse() throws Exception {
        long zone = createZone();
        postSensorExpectBadRequest(
            "{\"name\":\"P1\",\"type\":\"PRESSURE\",\"zoneId\":" + zone
                + ",\"assetType\":\"VALVE\",\"assetId\":1}");
    }

    @Test
    void capteurSansNomRefuse() throws Exception {
        long zone = createZone();
        long pump = createPump(zone);
        postSensorExpectBadRequest(
            "{\"name\":\"\",\"type\":\"PRESSURE\",\"zoneId\":" + zone
                + ",\"assetType\":\"PUMP\",\"assetId\":" + pump + "}");
    }

    @Test
    void capteurTypeInvalideRefuse() throws Exception {
        long zone = createZone();
        long pump = createPump(zone);
        postSensorExpectBadRequest(
            "{\"name\":\"P1\",\"type\":\"TEMPERATURE\",\"zoneId\":" + zone
                + ",\"assetType\":\"PUMP\",\"assetId\":" + pump + "}");
    }
}
