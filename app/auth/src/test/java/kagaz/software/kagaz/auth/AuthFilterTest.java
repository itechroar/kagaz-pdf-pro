package kagaz.software.kagaz.auth;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthFilterTest {

    @TempDir Path dir;
    UserStore store;
    AuthFilter filter;

    @BeforeEach
    void setUp() {
        store = new UserStore(dir.resolve("u.tsv").toString());
        store.create("admin", "longenough", UserStore.Role.ADMIN, false);
        store.create("bob", "longenough", UserStore.Role.USER, false);
        filter = new AuthFilter(store);
    }

    private MockHttpServletResponse run(MockHttpServletRequest req) throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(req, res, chain);
        return res;
    }

    private MockHttpServletRequest req(String method, String uri) {
        MockHttpServletRequest r = new MockHttpServletRequest(method, uri);
        r.setRequestURI(uri);
        return r;
    }

    @Test
    void loginPageIsPublic() throws Exception {
        assertEquals(200, run(req("GET", "/login.html")).getStatus());
        assertEquals(200, run(req("GET", "/kagaz-auth/auth.css")).getStatus());
    }

    @Test
    void anonymousApiGets401AndBrowserGetsRedirect() throws Exception {
        assertEquals(401, run(req("POST", "/api/v1/general/merge-pdfs")).getStatus());
        MockHttpServletRequest page = req("GET", "/");
        page.addHeader("Accept", "text/html");
        MockHttpServletResponse res = run(page);
        assertEquals(302, res.getStatus());
        assertTrue(res.getRedirectedUrl().startsWith("/login.html?next="));
    }

    @Test
    void apiKeyAuthenticates() throws Exception {
        String key = store.issueApiKey("bob");
        MockHttpServletRequest r = req("POST", "/api/v1/general/merge-pdfs");
        r.addHeader("X-API-KEY", key);
        assertEquals(200, run(r).getStatus());
        MockHttpServletRequest bearer = req("POST", "/api/v1/general/merge-pdfs");
        bearer.addHeader("Authorization", "Bearer " + key);
        assertEquals(200, run(bearer).getStatus());
        MockHttpServletRequest bad = req("POST", "/api/v1/general/merge-pdfs");
        bad.addHeader("X-API-KEY", "kpdf_wrong");
        assertEquals(401, run(bad).getStatus());
    }

    @Test
    void adminAreaRequiresAdmin() throws Exception {
        MockHttpServletRequest user = req("GET", "/api/v1/auth/admin/users");
        user.addHeader("X-API-KEY", store.issueApiKey("bob"));
        assertEquals(403, run(user).getStatus());
        MockHttpServletRequest admin = req("GET", "/api/v1/auth/admin/users");
        admin.addHeader("X-API-KEY", store.issueApiKey("admin"));
        assertEquals(200, run(admin).getStatus());
    }

    @Test
    void sessionWorksAndDisablingTakesEffect() throws Exception {
        MockHttpServletRequest r = req("GET", "/api/v1/auth/me");
        r.getSession(true).setAttribute(AuthFilter.SESSION_USER, "bob");
        assertEquals(200, run(r).getStatus());
        store.update("bob", null, false);
        assertEquals(401, run(r).getStatus());
    }

    @Test
    void crossOriginSessionWriteIsRefused() throws Exception {
        MockHttpServletRequest r = req("POST", "/api/v1/auth/logout");
        r.getSession(true).setAttribute(AuthFilter.SESSION_USER, "bob");
        r.addHeader("Host", "pdf.example.com");
        r.addHeader("Origin", "https://evil.example");
        assertEquals(403, run(r).getStatus());
        MockHttpServletRequest same = req("POST", "/api/v1/auth/logout");
        same.getSession(true).setAttribute(AuthFilter.SESSION_USER, "bob");
        same.addHeader("Host", "pdf.example.com");
        same.addHeader("Origin", "https://pdf.example.com");
        assertEquals(200, run(same).getStatus());
    }

    @Test
    void mustChangePasswordBlocksEverythingElse() throws Exception {
        store.create("temp", "longenough", UserStore.Role.USER, true);
        String key = store.issueApiKey("temp");
        MockHttpServletRequest blocked = req("POST", "/api/v1/general/merge-pdfs");
        blocked.addHeader("X-API-KEY", key);
        assertEquals(403, run(blocked).getStatus());
        MockHttpServletRequest allowed = req("POST", "/api/v1/auth/password");
        allowed.addHeader("X-API-KEY", key);
        assertEquals(200, run(allowed).getStatus());
    }
}
