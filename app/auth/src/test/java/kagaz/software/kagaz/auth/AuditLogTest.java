package kagaz.software.kagaz.auth;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AuditLogTest {

    @TempDir Path dir;

    @Test
    void recordsNewestFirstAndSanitizesFields() {
        AuditLog log = new AuditLog(dir.resolve("a.log").toString());
        log.on(new AuthEvent("login", "bob", "bob", "1.2.3.4", true));
        log.on(new AuthEvent("login", "evil\tname\ninjected", "x", "1.2.3.4", false));
        List<AuditLog.Entry> got = log.recent(10);
        assertEquals(2, got.size());
        assertFalse(got.get(0).success());
        assertEquals("evil name injected", got.get(0).actor());
        assertEquals("bob", got.get(1).actor());
        assertEquals(1, log.recent(1).size());
    }

    @Test
    void emptyWhenNoFile() {
        assertTrue(new AuditLog(dir.resolve("none.log").toString()).recent(5).isEmpty());
    }
}
