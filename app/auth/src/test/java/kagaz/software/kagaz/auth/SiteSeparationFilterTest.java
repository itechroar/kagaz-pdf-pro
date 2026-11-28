package kagaz.software.kagaz.auth;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SiteSeparationFilterTest {

    @TempDir Path dir;

    private UserStore.User admin;
    private UserStore.User member;

    @BeforeEach
    void users() {
        UserStore store = new UserStore(dir.resolve("u.tsv").toString());
        admin = store.create("root", "password-root", UserStore.Role.ADMIN, false);
        member = store.create("mia", "password-mia", UserStore.Role.USER, false);
    }

    private MockHttpServletResponse run(
            int adminPort, int localPort, String path, UserStore.User who, String accept)
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setLocalPort(localPort);
        if (who != null) {
            request.setAttribute(AuthFilter.REQUEST_USER, who);
        }
        if (accept != null) {
            request.addHeader("Accept", accept);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        new SiteSeparationFilter(new SiteMode(adminPort)).doFilter(request, response, chain);
        if (chain.getRequest() != null && response.getStatus() == 200) {
            response.addHeader("X-Passed", "yes");
        }
        return response;
    }

    @Test
    void withoutAnAdminPortNothingChanges() throws Exception {
        assertEquals("yes", run(0, 8080, "/admin-dashboard.html", admin, null).getHeader("X-Passed"));
    }

    @Test
    void userSiteHidesEveryAdministratorAddress() throws Exception {
        assertEquals(404, run(8081, 8080, "/admin-dashboard.html", admin, null).getStatus());
        assertEquals(404, run(8081, 8080, "/api/v1/auth/admin/activity", admin, null).getStatus());
        assertEquals(404, run(8081, 8080, "/admin", admin, null).getStatus());
        assertEquals(404, run(8081, 8080, "/admin-user.html", admin, null).getStatus());
        assertEquals("yes", run(8081, 8080, "/", member, null).getHeader("X-Passed"));
    }

    @Test
    void adminSiteServesAdministratorsOnly() throws Exception {
        assertEquals("yes", run(8081, 8081, "/admin-dashboard.html", admin, null).getHeader("X-Passed"));
        MockHttpServletResponse refused = run(8081, 8081, "/", member, "text/html");
        assertEquals(403, refused.getStatus());
        assertTrue(refused.getContentAsString().contains("Administrators only"));
        assertTrue(run(8081, 8081, "/api/v1/info/status", member, "application/json").getContentAsString().contains("admin_site_only"));
    }

    @Test
    void adminSiteStillLetsTheSignInPageLoad() throws Exception {
        assertEquals("yes", run(8081, 8081, "/login.html", null, null).getHeader("X-Passed"));
    }

    @Test
    void siteModeReportsWhereTheConsoleLives() {
        MockHttpServletRequest onUser = new MockHttpServletRequest();
        onUser.setLocalPort(8080);
        MockHttpServletRequest onAdmin = new MockHttpServletRequest();
        onAdmin.setLocalPort(8081);
        SiteMode separated = new SiteMode(8081);
        assertFalse(separated.hasAdminConsole(onUser));
        assertTrue(separated.hasAdminConsole(onAdmin));
        assertTrue(new SiteMode(0).hasAdminConsole(onUser));
    }
}
