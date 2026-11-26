package kagaz.software.kagaz.auth;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/** Login, session, password, API-key and (admin-only) user management endpoints. */
@RestController
@RequestMapping("/api/v1/auth")
@ConditionalOnProperty(name = "security.enableLogin", havingValue = "true")
public class AuthController {

    public record LoginRequest(String username, String password) {}

    public record PasswordRequest(String currentPassword, String newPassword) {}

    public record CreateUserRequest(
            String username, String password, String role, Boolean mustChangePassword) {}

    public record UpdateUserRequest(String role, Boolean enabled) {}

    public record ResetPasswordRequest(String newPassword) {}

    public record UserView(
            String username,
            String role,
            boolean enabled,
            boolean hasApiKey,
            boolean mustChangePassword,
            long createdAt) {
        static UserView of(UserStore.User u) {
            return new UserView(
                    u.username(),
                    u.role().name(),
                    u.enabled(),
                    u.apiKeyHash() != null,
                    u.mustChangePassword(),
                    u.createdAt());
        }
    }

    private final UserStore store;
    private final LoginLimiter limiter;
    private final ApplicationEventPublisher events;
    private final ObjectProvider<OidcService> oidc;
    private final QuotaFilter quota;
    private final AuditLog audit;

    public AuthController(
            UserStore store,
            LoginLimiter limiter,
            ApplicationEventPublisher events,
            ObjectProvider<OidcService> oidc,
            QuotaFilter quota,
            AuditLog audit) {
        this.quota = quota;
        this.audit = audit;
        this.store = store;
        this.limiter = limiter;
        this.events = events;
        this.oidc = oidc;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        OidcService sso = oidc.getIfAvailable();
        return sso == null
                ? Map.of("loginEnabled", true, "sso", false)
                : Map.of("loginEnabled", true, "sso", true, "ssoLabel", sso.label());
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody(required = false) LoginRequest body, HttpServletRequest request) {
        if (body == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "bad_request", "message", "Username and password required"));
        }
        String ip = request.getRemoteAddr();
        String name = body.username() == null ? "" : body.username();
        if (limiter.isLocked(ip, name)) {
            publish("login", name, name, ip, false);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "locked", "message", "Too many attempts, try later"));
        }
        var user = store.authenticate(name, body.password());
        if (user.isEmpty()) {
            limiter.recordFailure(ip, name);
            publish("login", name, name, ip, false);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "invalid", "message", "Invalid username or password"));
        }
        limiter.recordSuccess(ip, name);
        HttpSession old = request.getSession(false);
        if (old != null) {
            request.changeSessionId(); // prevent session fixation
        }
        request.getSession(true).setAttribute(AuthFilter.SESSION_USER, user.get().username());
        publish("login", user.get().username(), user.get().username(), ip, true);
        return ResponseEntity.ok(UserView.of(user.get()));
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletRequest request) {
        UserStore.User me = current(request);
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        publish("logout", me.username(), me.username(), request.getRemoteAddr(), true);
        return Map.of("ok", true);
    }

    @GetMapping("/me")
    public UserView me(HttpServletRequest request) {
        return UserView.of(current(request));
    }

    @PostMapping("/password")
    public Map<String, Object> changePassword(
            @RequestBody PasswordRequest body, HttpServletRequest request) {
        UserStore.User me = current(request);
        if (store.authenticate(me.username(), body.currentPassword()).isEmpty()) {
            publish("password-change", me.username(), me.username(), request.getRemoteAddr(), false);
            throw new IllegalArgumentException("Current password is incorrect");
        }
        store.setPassword(me.username(), body.newPassword(), false);
        publish("password-change", me.username(), me.username(), request.getRemoteAddr(), true);
        return Map.of("ok", true);
    }

    @GetMapping("/usage")
    public Map<String, Object> usage(HttpServletRequest request) {
        UserStore.User me = current(request);
        List<AuditLog.Entry> mine =
                audit.recent(1000).stream()
                        .filter(e -> e.actor().equals(me.username()) && e.action().startsWith("api:"))
                        .limit(50)
                        .toList();
        return Map.of(
                "usedToday", quota.usedToday(me.username()),
                "dailyLimit", me.role() == UserStore.Role.ADMIN ? 0 : quota.limit(),
                "recent", mine);
    }

    @PostMapping("/apikey")
    public Map<String, Object> issueOwnKey(HttpServletRequest request) {
        UserStore.User me = current(request);
        String key = store.issueApiKey(me.username());
        publish("apikey-issue", me.username(), me.username(), request.getRemoteAddr(), true);
        return Map.of("apiKey", key);
    }

    @DeleteMapping("/apikey")
    public Map<String, Object> revokeOwnKey(HttpServletRequest request) {
        UserStore.User me = current(request);
        store.revokeApiKey(me.username());
        publish("apikey-revoke", me.username(), me.username(), request.getRemoteAddr(), true);
        return Map.of("ok", true);
    }

    // ---- admin (AuthFilter already restricts /admin/ to administrators) ----

    @GetMapping("/admin/users")
    public List<UserView> listUsers() {
        return store.list().stream().map(UserView::of).toList();
    }

    @PostMapping("/admin/users")
    public ResponseEntity<UserView> createUser(
            @RequestBody CreateUserRequest body, HttpServletRequest request) {
        UserStore.User me = current(request);
        UserStore.Role role = parseRole(body.role());
        UserStore.User created =
                store.create(
                        body.username(),
                        body.password(),
                        role,
                        body.mustChangePassword() == null || body.mustChangePassword());
        publish("user-create", me.username(), created.username(), request.getRemoteAddr(), true);
        return ResponseEntity.status(HttpStatus.CREATED).body(UserView.of(created));
    }

    @PatchMapping("/admin/users/{username}")
    public UserView updateUser(
            @PathVariable String username,
            @RequestBody UpdateUserRequest body,
            HttpServletRequest request) {
        UserStore.User me = current(request);
        store.update(
                username, body.role() == null ? null : parseRole(body.role()), body.enabled());
        publish("user-update", me.username(), username, request.getRemoteAddr(), true);
        return UserView.of(store.find(username).orElseThrow());
    }

    @DeleteMapping("/admin/users/{username}")
    public Map<String, Object> deleteUser(
            @PathVariable String username, HttpServletRequest request) {
        UserStore.User me = current(request);
        store.delete(username);
        publish("user-delete", me.username(), username, request.getRemoteAddr(), true);
        return Map.of("ok", true);
    }

    @PostMapping("/admin/users/{username}/password")
    public Map<String, Object> resetPassword(
            @PathVariable String username,
            @RequestBody ResetPasswordRequest body,
            HttpServletRequest request) {
        UserStore.User me = current(request);
        store.setPassword(username, body.newPassword(), true);
        publish("password-reset", me.username(), username, request.getRemoteAddr(), true);
        return Map.of("ok", true);
    }

    @PostMapping("/admin/users/{username}/apikey")
    public Map<String, Object> issueKeyFor(
            @PathVariable String username, HttpServletRequest request) {
        UserStore.User me = current(request);
        String key = store.issueApiKey(username);
        publish("apikey-issue", me.username(), username, request.getRemoteAddr(), true);
        return Map.of("apiKey", key);
    }

    // ---- helpers ----

    private static UserStore.User current(HttpServletRequest request) {
        Object u = request.getAttribute(AuthFilter.REQUEST_USER);
        if (u instanceof UserStore.User user) {
            return user;
        }
        throw new IllegalStateException("No authenticated user on request");
    }

    private static UserStore.Role parseRole(String role) {
        if (role == null || role.isBlank()) {
            return UserStore.Role.USER;
        }
        try {
            return UserStore.Role.valueOf(role.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Role must be ADMIN or USER");
        }
    }

    private void publish(String action, String actor, String target, String ip, boolean ok) {
        events.publishEvent(new AuthEvent(action, actor, target, ip, ok));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", "bad_request", "message", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> any(Exception e) {
        if (e instanceof org.springframework.http.converter.HttpMessageNotReadableException
                || e instanceof org.springframework.web.ErrorResponse) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "bad_request", "message", "Invalid request"));
        }
        org.slf4j.LoggerFactory.getLogger(AuthController.class).error("Unexpected auth error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "internal", "message", "Something went wrong; see the server log"));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> conflict(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "conflict", "message", String.valueOf(e.getMessage())));
    }
}
