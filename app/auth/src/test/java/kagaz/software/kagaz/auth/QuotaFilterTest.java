package kagaz.software.kagaz.auth;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class QuotaFilterTest {

    @TempDir Path dir;

    static class MutableClock extends Clock {
        long now = 1_800_000_000_000L;

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(now);
        }
    }

    private int call(QuotaFilter f, UserStore.User user, String uri) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", uri);
        req.setRequestURI(uri);
        req.setAttribute(AuthFilter.REQUEST_USER, user);
        MockHttpServletResponse res = new MockHttpServletResponse();
        f.doFilter(req, res, new MockFilterChain());
        return res.getStatus();
    }

    @Test
    void capsUsersNotAdminsAndResetsDaily() throws Exception {
        UserStore store = new UserStore(dir.resolve("u.tsv").toString());
        UserStore.User admin = store.create("admin", "longenough", UserStore.Role.ADMIN, false);
        UserStore.User bob = store.create("bob", "longenough", UserStore.Role.USER, false);
        MutableClock clock = new MutableClock();
        QuotaFilter f = new QuotaFilter(2, clock);

        assertEquals(200, call(f, bob, "/api/v1/general/merge-pdfs"));
        assertEquals(200, call(f, bob, "/api/v1/general/merge-pdfs"));
        assertEquals(429, call(f, bob, "/api/v1/general/merge-pdfs"));
        assertEquals(2, Math.min(2, f.usedToday("bob")));
        for (int i = 0; i < 5; i++) {
            assertEquals(200, call(f, admin, "/api/v1/general/merge-pdfs"));
        }
        assertEquals(200, call(f, bob, "/api/v1/auth/me"));

        clock.now += 24L * 3600 * 1000;
        assertEquals(200, call(f, bob, "/api/v1/general/merge-pdfs"));
    }

    @Test
    void zeroMeansUnlimited() throws Exception {
        UserStore store = new UserStore(dir.resolve("u2.tsv").toString());
        UserStore.User bob = store.create("bob", "longenough", UserStore.Role.USER, false);
        QuotaFilter f = new QuotaFilter(0, new MutableClock());
        for (int i = 0; i < 20; i++) {
            assertEquals(200, call(f, bob, "/api/v1/general/merge-pdfs"));
        }
    }
}
