package ma.aquaops.detection.config;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/** Transforme les roles Keycloak (realm_access.roles) en autorites ROLE_xxx (meme logique que assets-service). */
public class KeycloakRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Object realmAccess = jwt.getClaims().get("realm_access");
        if (!(realmAccess instanceof Map<?, ?> map)) {
            return Collections.emptyList();
        }
        Object roles = map.get("roles");
        if (!(roles instanceof List<?> list)) {
            return Collections.emptyList();
        }
        return list.stream()
            .map(Object::toString)
            .<GrantedAuthority>map(r -> new SimpleGrantedAuthority("ROLE_" + r))
            .toList();
    }
}
