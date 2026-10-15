package ma.aquaops.assets.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    private static final String[] TOUS = {"operateur", "superviseur", "technicien", "administrateur"};
    private static final String[] ECRITURE = {"administrateur", "superviseur"};
    // Changement de statut d'une intervention : le controleur verifie ensuite
    // que le technicien est bien celui qui est assigne (SCRUM-42).
    private static final String[] STATUT_INTERVENTION = {"technicien", "superviseur", "administrateur"};

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        JwtAuthenticationConverter jwtConverter = new JwtAuthenticationConverter();
        jwtConverter.setJwtGrantedAuthoritiesConverter(new KeycloakRoleConverter());

        http.csrf(c -> c.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/actuator/health/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/assets/**").hasAnyRole(TOUS)
                // Regle precise AVANT la regle generale PUT : la premiere qui correspond s'applique.
                .requestMatchers(HttpMethod.PUT, "/api/assets/interventions/*/status").hasAnyRole(STATUT_INTERVENTION)
                .requestMatchers(HttpMethod.POST, "/api/assets/**").hasAnyRole(ECRITURE)
                .requestMatchers(HttpMethod.PUT, "/api/assets/**").hasAnyRole(ECRITURE)
                .requestMatchers(HttpMethod.DELETE, "/api/assets/**").hasAnyRole(ECRITURE)
                .anyRequest().denyAll())
            .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(jwtConverter)));
        return http.build();
    }
}
