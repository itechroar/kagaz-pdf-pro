package kagaz.software.kagaz.auth;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SecurityHeadersFilterTest {

    private MockHttpServletResponse run(String uri, boolean secure) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", uri);
        req.setRequestURI(uri);
        req.setSecure(secure);
        MockHttpServletResponse res = new MockHttpServletResponse();
        new SecurityHeadersFilter().doFilter(req, res, new MockFilterChain());
        return res;
    }

    @Test
    void baselineHeadersAlwaysSet() throws Exception {
        MockHttpServletResponse res = run("/anything", false);
        assertEquals("nosniff", res.getHeader("X-Content-Type-Options"));
        assertEquals("SAMEORIGIN", res.getHeader("X-Frame-Options"));
        assertNull(res.getHeader("Strict-Transport-Security"));
        assertNull(res.getHeader("Content-Security-Policy"));
    }

    @Test
    void hstsOnlyWhenSecure() throws Exception {
        assertNotNull(run("/x", true).getHeader("Strict-Transport-Security"));
    }

    @Test
    void cspOnAuthPagesAndNoStoreOnAuthApi() throws Exception {
        assertEquals(SecurityHeadersFilter.CSP, run("/login.html", false).getHeader("Content-Security-Policy"));
        assertEquals("no-store", run("/api/v1/auth/me", false).getHeader("Cache-Control"));
    }
}
