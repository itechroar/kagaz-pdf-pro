package kagaz.software.kagaz.auth;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class LoginLimiterTest {

    static class MutableClock extends Clock {
        long now = 1_000_000L;

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

    @Test
    void locksAfterFiveFailuresAndExpires() {
        MutableClock clock = new MutableClock();
        LoginLimiter l = new LoginLimiter(clock);
        for (int i = 0; i < 4; i++) {
            l.recordFailure("1.1.1.1", "bob");
        }
        assertFalse(l.isLocked("1.1.1.1", "bob"));
        l.recordFailure("1.1.1.1", "BOB");
        assertTrue(l.isLocked("1.1.1.1", "bob"));
        assertFalse(l.isLocked("2.2.2.2", "bob"));
        clock.now += LoginLimiter.WINDOW_MS + 1;
        assertFalse(l.isLocked("1.1.1.1", "bob"));
    }

    @Test
    void successResets() {
        LoginLimiter l = new LoginLimiter(new MutableClock());
        for (int i = 0; i < 5; i++) {
            l.recordFailure("1.1.1.1", "bob");
        }
        l.recordSuccess("1.1.1.1", "bob");
        assertFalse(l.isLocked("1.1.1.1", "bob"));
    }
}
