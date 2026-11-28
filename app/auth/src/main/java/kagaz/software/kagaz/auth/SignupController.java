package kagaz.software.kagaz.auth;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-service account creation, off unless {@code kagaz.auth.signup.enabled=true}. Accounts are
 * always plain users; administrators are only ever made by an administrator. Each client address
 * gets the same attempt budget as failed logins, so the endpoint cannot be used to mass-create
 * accounts.
 */
@RestController
@RequestMapping("/api/v1/auth")
@ConditionalOnProperty(name = "kagaz.auth.signup.enabled", havingValue = "true")
public class SignupController {

    public record SignupRequest(String username, String password) {}

    private static final String BUCKET = "\u0000signup";

    private final UserStore store;
    private final LoginLimiter limiter;
    private final ApplicationEventPublisher events;

    public SignupController(
            UserStore store, LoginLimiter limiter, ApplicationEventPublisher events) {
        this.store = store;
        this.limiter = limiter;
        this.events = events;
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signup(
            @RequestBody(required = false) SignupRequest body, HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        if (body == null || body.username() == null || body.password() == null) {
            return error(HttpStatus.BAD_REQUEST, "bad_request", "Username and password required");
        }
        if (limiter.isLocked(ip, BUCKET)) {
            return error(HttpStatus.TOO_MANY_REQUESTS, "locked", "Too many attempts, try later");
        }
        limiter.recordFailure(ip, BUCKET);
        try {
            UserStore.User created =
                    store.create(body.username(), body.password(), UserStore.Role.USER, false);
            events.publishEvent(new AuthEvent("signup", created.username(), created.username(), ip, true));
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("username", created.username()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            events.publishEvent(new AuthEvent("signup", "anonymous", body.username(), ip, false));
            return error(HttpStatus.BAD_REQUEST, "invalid", e.getMessage());
        }
    }

    private static ResponseEntity<?> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of("error", code, "message", message));
    }
}
