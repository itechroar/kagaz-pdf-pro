package kagaz.software.kagaz.auth;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import kagaz.software.SPDF.config.EndpointConfiguration;

/**
 * In-memory view of recent sign-ins, sign-ups and tool runs for the administrator dashboard,
 * seeded from the audit log at startup and fed by {@link AuthEvent}s afterwards. Only the newest
 * {@value #MAX_EVENTS} events are kept, so figures describe the retained window, not all time.
 *
 * <p>Thread-safe: state is guarded by this instance; subscriber sends happen outside the lock.
 */
@Component
@ConditionalOnProperty(name = "security.enableLogin", havingValue = "true")
public class ActivityTracker {

    static final int MAX_EVENTS = 5000;
    static final Duration WINDOW = Duration.ofHours(24);
    static final Duration ACTIVE = Duration.ofMinutes(5);
    private static final int RECENT = 60;
    private static final int MINUTES = 30;

    public record Event(
            String time,
            String kind,
            String action,
            String actor,
            String target,
            String tool,
            String ip,
            boolean success) {}

    private final UserStore users;
    private final Deque<Event> events = new ArrayDeque<>();
    private final List<SseEmitter> subscribers = new CopyOnWriteArrayList<>();

    public ActivityTracker(AuditLog audit, UserStore users) {
        this.users = users;
        List<AuditLog.Entry> stored = audit.recent(MAX_EVENTS);
        for (int i = stored.size() - 1; i >= 0; i--) {
            AuditLog.Entry e = stored.get(i);
            events.addLast(
                    classify(e.time(), e.action(), e.actor(), e.target(), e.ip(), e.success()));
        }
    }

    static Event classify(
            String time, String action, String actor, String target, String ip, boolean ok) {
        String kind;
        String tool = "";
        if ("login".equals(action) || "sso-login".equals(action)) {
            kind = "login";
        } else if ("signup".equals(action) || "user-create".equals(action)) {
            kind = "signup";
        } else if (action.startsWith("api:")) {
            kind = "tool";
            String key = EndpointConfiguration.endpointKeyForUri(target);
            tool = key != null ? key : target;
        } else {
            kind = "admin";
        }
        return new Event(time, kind, action, actor, target, tool, ip, ok);
    }

    @EventListener
    public void on(AuthEvent e) {
        Event event =
                classify(
                        Instant.now().toString(),
                        e.action(),
                        e.actor(),
                        e.target(),
                        e.ip(),
                        e.success());
        synchronized (this) {
            events.addLast(event);
            while (events.size() > MAX_EVENTS) {
                events.removeFirst();
            }
        }
        for (SseEmitter emitter : subscribers) {
            try {
                emitter.send(
                        SseEmitter.event()
                                .name("activity")
                                .data(event, MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException ex) {
                subscribers.remove(emitter);
            }
        }
    }

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(Duration.ofMinutes(30).toMillis());
        subscribers.add(emitter);
        emitter.onCompletion(() -> subscribers.remove(emitter));
        emitter.onTimeout(() -> subscribers.remove(emitter));
        emitter.onError(ex -> subscribers.remove(emitter));
        return emitter;
    }

    /** Keeps idle connections alive through proxies; also drops any that have gone away. */
    public void heartbeat() {
        for (SseEmitter emitter : subscribers) {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (IOException | IllegalStateException ex) {
                subscribers.remove(emitter);
            }
        }
    }

    public int subscriberCount() {
        return subscribers.size();
    }

    public Map<String, Object> snapshot() {
        return snapshot(Instant.now());
    }

    Map<String, Object> snapshot(Instant now) {
        List<Event> all;
        synchronized (this) {
            all = new ArrayList<>(events);
        }
        Instant since = now.minus(WINDOW);
        Instant activeSince = now.minus(ACTIVE);

        Map<String, int[]> perUser = new HashMap<>(); // logins, failed logins, tool calls
        Map<String, String> lastActive = new HashMap<>();
        Map<String, String> lastTool = new HashMap<>();
        Map<String, int[]> perTool = new HashMap<>(); // calls, failures
        Map<String, Set<String>> toolUsers = new HashMap<>();
        int[] minutes = new int[MINUTES];
        int signups = 0;
        Set<String> active = new HashSet<>();

        for (Event e : all) {
            Instant at = Instant.parse(e.time());
            if (!"anonymous".equals(e.actor()) && !e.actor().isEmpty()) {
                lastActive.merge(e.actor(), e.time(), (a, b) -> a.compareTo(b) >= 0 ? a : b);
                if (at.isAfter(activeSince) && e.success()) {
                    active.add(e.actor());
                }
            }
            if (at.isBefore(since)) {
                continue;
            }
            int[] row = perUser.computeIfAbsent(e.actor(), k -> new int[3]);
            switch (e.kind()) {
                case "login" -> row[e.success() ? 0 : 1]++;
                case "signup" -> signups++;
                case "tool" -> {
                    row[2]++;
                    lastTool.put(e.actor(), e.tool());
                    int[] t = perTool.computeIfAbsent(e.tool(), k -> new int[2]);
                    t[0]++;
                    if (!e.success()) {
                        t[1]++;
                    }
                    toolUsers.computeIfAbsent(e.tool(), k -> new HashSet<>()).add(e.actor());
                    long ago = Duration.between(at, now).toMinutes();
                    if (ago >= 0 && ago < MINUTES) {
                        minutes[MINUTES - 1 - (int) ago]++;
                    }
                }
                default -> {}
            }
        }

        int loginsOk = 0;
        int loginsFailed = 0;
        int toolCalls = 0;
        int toolFailures = 0;
        for (int[] r : perUser.values()) {
            loginsOk += r[0];
            loginsFailed += r[1];
            toolCalls += r[2];
        }
        for (int[] t : perTool.values()) {
            toolFailures += t[1];
        }

        List<Map<String, Object>> userRows = new ArrayList<>();
        List<UserStore.User> directory = users.list();
        Set<String> known = new HashSet<>();
        for (UserStore.User u : directory) {
            known.add(u.username());
            int[] r = perUser.getOrDefault(u.username(), new int[3]);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("username", u.username());
            row.put("role", u.role().name());
            row.put("enabled", u.enabled());
            row.put("createdAt", u.createdAt());
            row.put("logins", r[0]);
            row.put("failedLogins", r[1]);
            row.put("toolCalls", r[2]);
            row.put("lastActive", lastActive.get(u.username()));
            row.put("lastTool", lastTool.get(u.username()));
            row.put("activeNow", active.contains(u.username()));
            userRows.add(row);
        }
        userRows.sort(
                Comparator.comparing(
                                (Map<String, Object> m) ->
                                        m.get("lastActive") == null
                                                ? ""
                                                : (String) m.get("lastActive"))
                        .reversed());

        List<Map<String, Object>> toolRows = new ArrayList<>();
        perTool.forEach(
                (tool, t) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("tool", tool);
                    row.put("calls", t[0]);
                    row.put("failures", t[1]);
                    row.put("users", toolUsers.get(tool).size());
                    toolRows.add(row);
                });
        toolRows.sort(Comparator.comparingInt((Map<String, Object> m) -> (int) m.get("calls")).reversed());

        List<Event> recent = new ArrayList<>();
        for (int i = all.size() - 1; i >= 0 && recent.size() < RECENT; i--) {
            recent.add(all.get(i));
        }

        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("users", directory.size());
        totals.put("enabledUsers", directory.stream().filter(UserStore.User::enabled).count());
        totals.put("activeNow", active.stream().filter(known::contains).count());
        totals.put("loginsOk", loginsOk);
        totals.put("loginsFailed", loginsFailed);
        totals.put("signups", signups);
        totals.put("toolCalls", toolCalls);
        totals.put("toolFailures", toolFailures);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("generatedAt", now.toString());
        out.put("windowHours", WINDOW.toHours());
        out.put("liveViewers", subscribers.size());
        out.put("totals", totals);
        out.put("users", userRows);
        out.put("tools", toolRows);
        out.put("perMinute", minutes);
        out.put("recent", recent);
        return out;
    }
}
