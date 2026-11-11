package ma.aquaops.assets.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Auteur et role lus dans le jeton : pas de base de donnees necessaire. */
class AuditServiceTests {

    private static JwtAuthenticationToken token(Map<String, Object> claims, String... roles) {
        Jwt jwt = new Jwt("t", Instant.now(), Instant.now().plusSeconds(60),
            Map.of("alg", "none"), claims);
        return new JwtAuthenticationToken(jwt,
            List.of(roles).stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList());
    }

    @Test
    void auteurEstLePreferredUsernameDuJeton() {
        var auth = token(Map.of("sub", "id-1", "preferred_username", "alice"), "superviseur");
        assertEquals("alice", AuditService.actorOf(auth));
    }

    @Test
    void sansPreferredUsernameOnPrendLeNomDuJeton() {
        var auth = token(Map.of("sub", "id-1"), "superviseur");
        assertEquals("id-1", AuditService.actorOf(auth));
    }

    @Test
    void sansAuthentificationOuAnonymeAuteurEstSystem() {
        assertEquals("system", AuditService.actorOf(null));
        var anon = new AnonymousAuthenticationToken("k", "anonymousUser",
            AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
        assertEquals("system", AuditService.actorOf(anon));
    }

    @Test
    void roleRetenuEstLePlusPuissant() {
        var auth = token(Map.of("sub", "u"), "operateur", "superviseur");
        assertEquals("superviseur", AuditService.roleOf(auth));
    }

    @Test
    void roleInconnuDonneNull() {
        assertNull(AuditService.roleOf(token(Map.of("sub", "u"), "autre")));
        assertNull(AuditService.roleOf(null));
    }
}
