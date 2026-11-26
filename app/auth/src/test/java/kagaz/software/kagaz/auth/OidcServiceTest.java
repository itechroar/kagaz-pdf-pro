package kagaz.software.kagaz.auth;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class OidcServiceTest {

    private static final long NOW = 1_800_000_000L;

    private Map<String, Object> good() {
        Map<String, Object> m = new HashMap<>();
        m.put("iss", "https://idp.example.com/");
        m.put("aud", List.of("client-1", "other"));
        m.put("exp", NOW + 600);
        m.put("nonce", "n-1");
        m.put("sub", "u-42");
        m.put("email", "Alice@Example.com");
        m.put("email_verified", true);
        return m;
    }

    private OidcService.Claims check(Map<String, Object> c) {
        return OidcService.validate(c, "https://idp.example.com", "client-1", "n-1", NOW);
    }

    @Test
    void acceptsValidTokenAndLowercasesEmail() {
        OidcService.Claims c = check(good());
        assertEquals("alice@example.com", c.email());
        assertTrue(c.emailVerified());
        assertEquals("u-42", c.subject());
    }

    @Test
    void rejectsEachBadClaim() {
        Map<String, Object> m = good();
        m.put("iss", "https://evil.example.com");
        assertThrows(IllegalStateException.class, () -> check(m));
        Map<String, Object> a = good();
        a.put("aud", "someone-else");
        assertThrows(IllegalStateException.class, () -> check(a));
        Map<String, Object> e = good();
        e.put("exp", NOW - 3600);
        assertThrows(IllegalStateException.class, () -> check(e));
        Map<String, Object> n = good();
        n.put("nonce", "replayed");
        assertThrows(IllegalStateException.class, () -> check(n));
        Map<String, Object> s = good();
        s.remove("sub");
        assertThrows(IllegalStateException.class, () -> check(s));
    }

    @Test
    void unverifiedEmailIsFlagged() {
        Map<String, Object> m = good();
        m.put("email_verified", false);
        assertFalse(check(m).emailVerified());
    }

    @Test
    void pkceMatchesRfc7636Example() {
        assertEquals(
                "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
                OidcService.pkceChallenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"));
    }

    @Test
    void csvParsing() {
        assertEquals(List.of("a.com", "b.com"), OidcService.csv(" A.com, b.com ,,"));
        assertTrue(OidcService.csv(null).isEmpty());
    }
}
