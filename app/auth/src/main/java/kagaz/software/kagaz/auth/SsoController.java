package kagaz.software.kagaz.auth;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/** Browser redirect endpoints for single sign-on. Reached anonymously (see {@link AuthFilter}). */
@RestController
@RequestMapping("/api/v1/auth/sso")
@ConditionalOnExpression(
        "'${security.enableLogin:false}' == 'true' && !'${kagaz.auth.oidc.issuer:}'.isEmpty()"
                + " && !'${kagaz.auth.oidc.client-id:}'.isEmpty() && !'${kagaz.auth.public-url:}'.isEmpty()")
public class SsoController {

    private static final Logger log = LoggerFactory.getLogger(SsoController.class);
    private static final String S_STATE = "kagaz.sso.state";
    private static final String S_NONCE = "kagaz.sso.nonce";
    private static final String S_VERIFIER = "kagaz.sso.verifier";

    private final OidcService oidc;
    private final UserStore store;
    private final ApplicationEventPublisher events;
    private final boolean autoCreate;
    private final List<String> allowedDomains;
    private final List<String> adminEmails;

    public SsoController(
            OidcService oidc,
            UserStore store,
            ApplicationEventPublisher events,
            @Value("${kagaz.auth.oidc.auto-create:true}") boolean autoCreate,
            @Value("${kagaz.auth.oidc.allowed-email-domains:}") String allowedDomains,
            @Value("${kagaz.auth.oidc.admin-emails:}") String adminEmails) {
        this.oidc = oidc;
        this.store = store;
        this.events = events;
        this.autoCreate = autoCreate;
        this.allowedDomains = OidcService.csv(allowedDomains);
        this.adminEmails = OidcService.csv(adminEmails);
    }

    @GetMapping("/login")
    public void login(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        HttpSession session = request.getSession(true);
        String state = oidc.randomToken();
        String nonce = oidc.randomToken();
        String verifier = oidc.randomToken();
        session.setAttribute(S_STATE, state);
        session.setAttribute(S_NONCE, nonce);
        session.setAttribute(S_VERIFIER, verifier);
        try {
            response.sendRedirect(oidc.authorizationUrl(state, nonce, verifier));
        } catch (RuntimeException e) {
            log.error("SSO provider unreachable", e);
            response.sendRedirect("/login.html?sso=unavailable");
        }
    }

    @GetMapping("/callback")
    public void callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            HttpServletRequest request,
            HttpServletResponse response)
            throws IOException {
        String ip = request.getRemoteAddr();
        HttpSession session = request.getSession(false);
        String expectedState = session == null ? null : (String) session.getAttribute(S_STATE);
        String nonce = session == null ? null : (String) session.getAttribute(S_NONCE);
        String verifier = session == null ? null : (String) session.getAttribute(S_VERIFIER);
        if (session != null) {
            session.removeAttribute(S_STATE);
            session.removeAttribute(S_NONCE);
            session.removeAttribute(S_VERIFIER);
        }
        if (error != null
                || code == null
                || expectedState == null
                || !java.security.MessageDigest.isEqual(
                        expectedState.getBytes(), String.valueOf(state).getBytes())) {
            fail(response, ip, "sso:denied");
            return;
        }
        OidcService.Claims claims;
        try {
            claims = oidc.redeem(code, verifier, nonce);
        } catch (RuntimeException e) {
            log.warn("SSO token validation failed: {}", e.getMessage());
            fail(response, ip, "sso:invalid-token");
            return;
        }
        Optional<UserStore.User> user = resolve(claims);
        if (user.isEmpty() || !user.get().enabled()) {
            fail(response, ip, "sso:not-permitted");
            return;
        }
        request.changeSessionId();
        request.getSession(true).setAttribute(AuthFilter.SESSION_USER, user.get().username());
        events.publishEvent(
                new AuthEvent("sso-login", user.get().username(), claims.subject(), ip, true));
        response.sendRedirect("/");
    }

    private Optional<UserStore.User> resolve(OidcService.Claims claims) {
        String email = claims.email();
        if (email == null || !claims.emailVerified()) {
            return Optional.empty();
        }
        int at = email.lastIndexOf('@');
        String domain = at < 0 ? "" : email.substring(at + 1);
        if (!allowedDomains.isEmpty() && !allowedDomains.contains(domain)) {
            return Optional.empty();
        }
        String username = email.replaceAll("[^A-Za-z0-9_.@-]", "_");
        if (username.length() > 64) {
            return Optional.empty();
        }
        Optional<UserStore.User> existing = store.find(username);
        if (existing.isPresent()) {
            return existing;
        }
        if (!autoCreate) {
            return Optional.empty();
        }
        UserStore.Role role =
                adminEmails.contains(email) ? UserStore.Role.ADMIN : UserStore.Role.USER;
        // SSO accounts get an unguessable local password, so they can only sign in through the IdP.
        byte[] pw = new byte[32];
        new SecureRandom().nextBytes(pw);
        try {
            return Optional.of(
                    store.create(
                            username,
                            Base64.getUrlEncoder().withoutPadding().encodeToString(pw),
                            role,
                            false));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private void fail(HttpServletResponse response, String ip, String action) throws IOException {
        events.publishEvent(new AuthEvent(action, "anonymous", "", ip, false));
        response.sendRedirect("/login.html?sso=failed");
    }
}
