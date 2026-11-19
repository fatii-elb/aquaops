package ma.aquaops.detection.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Securite de l'API des anomalies (BE-1) : jeton Keycloak obligatoire, lecture seule pour les quatre roles.
 * Tout le reste est refuse, sauf la sonde de sante. Meme principe que assets-service.
 */
@Configuration
public class SecurityConfig {

    private static final String[] TOUS = {"operateur", "superviseur", "technicien", "administrateur"};

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        JwtAuthenticationConverter jwtConverter = new JwtAuthenticationConverter();
        jwtConverter.setJwtGrantedAuthoritiesConverter(new KeycloakRoleConverter());

        http.csrf(c -> c.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/actuator/health/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/actuator/prometheus").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/detection/anomalies", "/api/detection/anomalies/**").hasAnyRole(TOUS)
                // Seuils par zone (FR-13) : lecture pour tous, modification reservee a l'administrateur
                .requestMatchers(HttpMethod.GET, "/api/detection/thresholds", "/api/detection/thresholds/**").hasAnyRole(TOUS)
                .requestMatchers(HttpMethod.PUT, "/api/detection/thresholds/**").hasRole("administrateur")
                .anyRequest().denyAll())
            .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(jwtConverter)));
        return http.build();
    }
}
