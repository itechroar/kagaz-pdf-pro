package kagaz.software.kagaz.auth;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Locks a (client address, username) pair after repeated failed logins. */
@Component
@ConditionalOnProperty(name = "security.enableLogin", havingValue = "true")
public class LoginLimiter {

    static final int MAX_FAILURES = 5;
    static final long WINDOW_MS = 15 * 60 * 1000L;

    private record Entry(int failures, long windowStart) {}

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginLimiter() {
        this(Clock.systemUTC());
    }

    LoginLimiter(Clock clock) {
        this.clock = clock;
    }

    private static String key(String ip, String username) {
        return ip + "|" + (username == null ? "" : username.trim().toLowerCase());
    }

    public boolean isLocked(String ip, String username) {
        Entry e = entries.get(key(ip, username));
        if (e == null) {
            return false;
        }
        if (clock.millis() - e.windowStart() > WINDOW_MS) {
            entries.remove(key(ip, username));
            return false;
        }
        return e.failures() >= MAX_FAILURES;
    }

    public void recordFailure(String ip, String username) {
        long now = clock.millis();
        entries.merge(
                key(ip, username),
                new Entry(1, now),
                (old, ignored) ->
                        now - old.windowStart() > WINDOW_MS
                                ? new Entry(1, now)
                                : new Entry(old.failures() + 1, old.windowStart()));
        if (entries.size() > 10_000) {
            entries.entrySet().removeIf(en -> now - en.getValue().windowStart() > WINDOW_MS);
        }
    }

    public void recordSuccess(String ip, String username) {
        entries.remove(key(ip, username));
    }
}
