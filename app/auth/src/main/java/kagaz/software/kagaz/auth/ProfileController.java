package kagaz.software.kagaz.auth;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

import kagaz.software.SPDF.config.EndpointConfiguration;

/**
 * Account profiles: a signed-in user reads their own, an administrator reads anyone's. Figures come
 * from the audit log, so they cover its retained window rather than all time.
 */
@RestController
@RequestMapping("/api/v1/auth")
@ConditionalOnProperty(name = "security.enableLogin", havingValue = "true")
public class ProfileController {

    private static final int HISTORY = 1000;
    private static final int RECENT = 50;

    private final UserStore store;
    private final AuditLog audit;
    private final QuotaFilter quota;

    public ProfileController(UserStore store, AuditLog audit, QuotaFilter quota) {
        this.store = store;
        this.audit = audit;
        this.quota = quota;
    }

    @GetMapping("/profile")
    public ResponseEntity<Map<String, Object>> own(HttpServletRequest request) {
        if (request.getAttribute(AuthFilter.REQUEST_USER) instanceof UserStore.User me) {
            return ResponseEntity.ok(profile(me));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    @GetMapping("/admin/users/{username}/profile")
    public ResponseEntity<Map<String, Object>> of(@PathVariable String username) {
        return store.find(username)
                .map(u -> ResponseEntity.ok(profile(u)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    private Map<String, Object> profile(UserStore.User user) {
        Map<String, Object> out =
                summarise(user, audit.recent(HISTORY), quota.usedToday(user.username()));
        out.put("dailyLimit", user.role() == UserStore.Role.ADMIN ? 0 : quota.limit());
        return out;
    }

    /** Newest-first {@code entries}; only those that name {@code user} as actor or target count. */
    static Map<String, Object> summarise(
            UserStore.User user, List<AuditLog.Entry> entries, int usedToday) {
        String name = user.username();
        int logins = 0;
        int failedLogins = 0;
        int toolRuns = 0;
        int toolFailures = 0;
        String lastLogin = null;
        String lastActive = null;
        Set<String> addresses = new TreeSet<>();
        Map<String, Integer> perTool = new TreeMap<>();
        List<AuditLog.Entry> mine = new ArrayList<>();
        for (AuditLog.Entry e : entries) {
            boolean actor = name.equals(e.actor());
            if (!actor && !name.equals(e.target())) {
                continue;
            }
            if (mine.size() < RECENT) {
                mine.add(e);
            }
            if (!actor) {
                continue;
            }
            if (lastActive == null) {
                lastActive = e.time();
            }
            if (!e.ip().isEmpty() && addresses.size() < 10) {
                addresses.add(e.ip());
            }
            if ("login".equals(e.action()) || "sso-login".equals(e.action())) {
                if (e.success()) {
                    logins++;
                    if (lastLogin == null) {
                        lastLogin = e.time();
                    }
                } else {
                    failedLogins++;
                }
            } else if (e.action().startsWith("api:")) {
                toolRuns++;
                if (!e.success()) {
                    toolFailures++;
                }
                String key = EndpointConfiguration.endpointKeyForUri(e.target());
                perTool.merge(key != null ? key : e.target(), 1, Integer::sum);
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("username", name);
        out.put("role", user.role().name());
        out.put("enabled", user.enabled());
        out.put("hasApiKey", user.apiKeyHash() != null && !user.apiKeyHash().isEmpty());
        out.put("mustChangePassword", user.mustChangePassword());
        out.put("createdAt", user.createdAt());
        out.put("lastLogin", lastLogin);
        out.put("lastActive", lastActive);
        out.put("logins", logins);
        out.put("failedLogins", failedLogins);
        out.put("toolRuns", toolRuns);
        out.put("toolFailures", toolFailures);
        out.put("usedToday", usedToday);
        out.put("addresses", addresses);
        out.put(
                "topTools",
                perTool.entrySet().stream()
                        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                        .limit(10)
                        .map(t -> Map.of("tool", t.getKey(), "runs", t.getValue()))
                        .toList());
        out.put("recent", mine);
        return out;
    }
}
