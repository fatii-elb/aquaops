package ma.aquaops.assets.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Transactional
class SecurityRolesTests {

    @Autowired WebApplicationContext wac;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(wac).apply(springSecurity()).build();
    }

    private RequestPostProcessor role(String r) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + r));
    }

    private static final String ZONE = "{\"name\":\"Zone test\"}";

    @Test
    void sansJetonRenvoie401() throws Exception {
        mvc.perform(get("/api/assets/zones")).andExpect(status().isUnauthorized());
    }

    @Test
    void healthResteOuvert() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void lecturePermisePourLesQuatreRoles() throws Exception {
        for (String r : List.of("operateur", "superviseur", "technicien", "administrateur")) {
            mvc.perform(get("/api/assets/zones").with(role(r))).andExpect(status().isOk());
        }
    }

    @Test
    void ecritureInterditePourOperateurEtTechnicien403() throws Exception {
        for (String r : List.of("operateur", "technicien")) {
            mvc.perform(post("/api/assets/zones").with(role(r))
                    .contentType(MediaType.APPLICATION_JSON).content(ZONE))
                .andExpect(status().isForbidden());
            mvc.perform(put("/api/assets/zones/1").with(role(r))
                    .contentType(MediaType.APPLICATION_JSON).content(ZONE))
                .andExpect(status().isForbidden());
            mvc.perform(delete("/api/assets/zones/1").with(role(r)))
                .andExpect(status().isForbidden());
        }
    }

    @Test
    void ecriturePermisePourAdministrateurEtSuperviseur() throws Exception {
        for (String r : List.of("administrateur", "superviseur")) {
            mvc.perform(post("/api/assets/zones").with(role(r))
                    .contentType(MediaType.APPLICATION_JSON).content(ZONE))
                .andExpect(status().isCreated());
        }
    }

    @Test
    void convertisseurLitLesRolesKeycloak() {
        Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(60),
            Map.of("alg", "none"),
            Map.of("sub", "u1", "realm_access", Map.of("roles", List.of("technicien", "operateur"))));
        List<String> noms = new KeycloakRoleConverter().convert(jwt).stream()
            .map(GrantedAuthority::getAuthority).toList();
        assertEquals(2, noms.size());
        assertTrue(noms.contains("ROLE_technicien"));
        assertTrue(noms.contains("ROLE_operateur"));
    }

    @Test
    void convertisseurSansRolesRenvoieVide() {
        Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(60),
            Map.of("alg", "none"), Map.of("sub", "u1"));
        assertTrue(new KeycloakRoleConverter().convert(jwt).isEmpty());
    }
}
