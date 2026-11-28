package kagaz.software.kagaz.auth;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ActivityTrackerTest {

    @TempDir Path dir;

    @SuppressWarnings("unchecked")
    @Test
    void countsSignInsSignUpsAndToolRunsPerUser() {
        UserStore store = new UserStore(dir.resolve("u.tsv").toString());
        store.create("alice", "password-1", UserStore.Role.USER, false);
        store.create("bob", "password-2", UserStore.Role.USER, false);
        ActivityTracker tracker =
                new ActivityTracker(new AuditLog(dir.resolve("a.log").toString()), store);

        tracker.on(new AuthEvent("login", "alice", "alice", "1.1.1.1", true));
        tracker.on(new AuthEvent("login", "bob", "bob", "2.2.2.2", false));
        tracker.on(new AuthEvent("signup", "bob", "bob", "2.2.2.2", true));
        tracker.on(
                new AuthEvent("api:POST", "alice", "/api/v1/general/merge-pdfs", "1.1.1.1", true));
        tracker.on(
                new AuthEvent("api:POST", "alice", "/api/v1/general/merge-pdfs", "1.1.1.1", false));

        Map<String, Object> snap = tracker.snapshot(Instant.now());
        Map<String, Object> totals = (Map<String, Object>) snap.get("totals");
        assertEquals(1, totals.get("loginsOk"));
        assertEquals(1, totals.get("loginsFailed"));
        assertEquals(1, totals.get("signups"));
        assertEquals(2, totals.get("toolCalls"));
        assertEquals(1, totals.get("toolFailures"));
        assertEquals(2L, totals.get("activeNow"));

        List<Map<String, Object>> tools = (List<Map<String, Object>>) snap.get("tools");
        assertEquals("merge-pdfs", tools.get(0).get("tool"));
        assertEquals(2, tools.get(0).get("calls"));

        List<Map<String, Object>> users = (List<Map<String, Object>>) snap.get("users");
        Map<String, Object> alice =
                users.stream()
                        .filter(u -> "alice".equals(u.get("username")))
                        .findFirst()
                        .orElseThrow();
        assertEquals(2, alice.get("toolCalls"));
        assertEquals("merge-pdfs", alice.get("lastTool"));
    }

    @Test
    void restoresHistoryFromTheAuditLog() {
        UserStore store = new UserStore(dir.resolve("u.tsv").toString());
        AuditLog log = new AuditLog(dir.resolve("a.log").toString());
        log.on(new AuthEvent("api:POST", "carol", "/api/v1/misc/compress-pdf", "3.3.3.3", true));
        ActivityTracker tracker = new ActivityTracker(log, store);
        assertEquals(1, ((List<?>) tracker.snapshot().get("recent")).size());
    }
}
