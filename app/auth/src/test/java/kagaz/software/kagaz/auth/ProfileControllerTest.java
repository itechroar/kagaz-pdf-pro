package kagaz.software.kagaz.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ProfileControllerTest {

    private static final UserStore.User ANNA =
            new UserStore.User("anna", "h", UserStore.Role.USER, true, "", false, 1L);

    private static AuditLog.Entry entry(
            String time, String action, String actor, String target, String ip, boolean ok) {
        return new AuditLog.Entry(time, action, actor, target, ip, ok);
    }

    @Test
    void countsOnlyTheUsersOwnActivity() {
        List<AuditLog.Entry> newestFirst =
                List.of(
                        entry("t5", "api:POST", "anna", "/api/v1/misc/compress-pdf", "1.1.1.1", true),
                        entry("t4", "login", "anna", "anna", "1.1.1.1", true),
                        entry("t3", "login", "bob", "bob", "2.2.2.2", true),
                        entry("t2", "login", "anna", "anna", "3.3.3.3", false),
                        entry("t1", "user-update", "root", "anna", "9.9.9.9", true));

        Map<String, Object> p = ProfileController.summarise(ANNA, newestFirst, 4);

        assertEquals(1, p.get("logins"));
        assertEquals(1, p.get("failedLogins"));
        assertEquals(1, p.get("toolRuns"));
        assertEquals("t4", p.get("lastLogin"));
        assertEquals("t5", p.get("lastActive"));
        assertEquals(4, p.get("usedToday"));
        assertEquals(4, ((List<?>) p.get("recent")).size());
    }

    @Test
    void quietAccountHasNoLastLogin() {
        Map<String, Object> p = ProfileController.summarise(ANNA, List.of(), 0);

        assertNull(p.get("lastLogin"));
        assertEquals(0, p.get("toolRuns"));
        assertEquals(false, p.get("hasApiKey"));
    }
}
