package kagaz.software.kagaz.auth;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Gatekeeper for every request when {@code security.enableLogin=true}. Accepts a session created
 * by {@code /api/v1/auth/login} or an API key ({@code X-API-KEY} or {@code Authorization: Bearer}).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@ConditionalOnProperty(name = "security.enableLogin", havingValue = "true")
public class AuthFilter extends OncePerRequestFilter {

    public static final String SESSION_USER = "kagaz.user";
    public static final String REQUEST_USER = "kagaz.user";

    private static final Set<String> PUBLIC_EXACT =
            Set.of(
                    "/login.html",
                    "/api/v1/auth/login",
                    "/api/v1/auth/status",
                    "/api/v1/auth/signup",
                    "/favicon.ico",
                    "/actuator/health");
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private static final Set<String> PASSWORD_CHANGE_ALLOWED =
            Set.of("/api/v1/auth/password", "/api/v1/auth/logout", "/api/v1/auth/me");

    private final UserStore store;

    public AuthFilter(UserStore store) {
        this.store = store;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (isPublic(path)) {
            chain.doFilter(request, response);
            return;
        }

        boolean viaSession = false;
        Optional<UserStore.User> user = fromApiKey(request);
        if (user.isEmpty()) {
            user = fromSession(request);
            viaSession = user.isPresent();
        }

        if (user.isEmpty()) {
            reject(request, response, path);
            return;
        }
        if (viaSession && !SAFE_METHODS.contains(request.getMethod()) && crossOrigin(request)) {
            json(response, 403, "cross_origin", "Cross-origin request refused");
            return;
        }
        UserStore.User u = user.get();
        if (u.mustChangePassword() && !PASSWORD_CHANGE_ALLOWED.contains(path)) {
            if (isPageRequest(request)) {
                response.sendRedirect(request.getContextPath() + "/login.html?change=1");
            } else {
                json(response, 403, "password_change_required", "Change your password first");
            }
            return;
        }
        if (isAdminPath(path) && u.role() != UserStore.Role.ADMIN) {
            json(response, 403, "forbidden", "Administrator access required");
            return;
        }
        request.setAttribute(REQUEST_USER, u);
        chain.doFilter(request, response);
    }

    /** Pages and APIs that only administrators may use. */
    static boolean isAdminPath(String path) {
        return path.equals("/admin-users.html")
                || path.equals("/admin-audit.html")
                || path.equals("/admin-features.html")
                || path.equals("/admin-dashboard.html")
                || path.equals("/admin")
                || path.startsWith("/api/v1/auth/admin/");
    }

    private static boolean isPublic(String path) {
        return PUBLIC_EXACT.contains(path)
                || path.startsWith("/kagaz-auth/")
                || path.startsWith("/api/v1/auth/sso/");
    }

    private Optional<UserStore.User> fromApiKey(HttpServletRequest request) {
        String key = request.getHeader("X-API-KEY");
        if (key == null) {
            String authz = request.getHeader("Authorization");
            if (authz != null && authz.regionMatches(true, 0, "Bearer ", 0, 7)) {
                key = authz.substring(7).trim();
            }
        }
        return store.findByApiKey(key);
    }

    private Optional<UserStore.User> fromSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute(SESSION_USER) instanceof String name)) {
            return Optional.empty();
        }
        // Re-read from the store so disabling or deleting a user takes effect immediately.
        Optional<UserStore.User> current = store.find(name).filter(UserStore.User::enabled);
        if (current.isEmpty()) {
            session.invalidate();
        }
        return current;
    }

    private static boolean crossOrigin(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin == null) {
            return false;
        }
        try {
            URI uri = URI.create(origin);
            String host = request.getHeader("Host");
            String originHost = uri.getPort() == -1 ? uri.getHost() : uri.getHost() + ":" + uri.getPort();
            return host == null || !host.equalsIgnoreCase(originHost);
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    private static boolean isPageRequest(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return "GET".equals(request.getMethod()) && accept != null && accept.contains("text/html");
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, String path)
            throws IOException {
        if (isPageRequest(request)) {
            String query = request.getQueryString();
            String next = query == null ? path : path + "?" + query;
            response.sendRedirect(
                    request.getContextPath()
                            + "/login.html?next="
                            + URLEncoder.encode(next, StandardCharsets.UTF_8));
        } else {
            json(response, 401, "unauthenticated", "Login required");
        }
    }

    private static void json(HttpServletResponse response, int status, String code, String msg)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter()
                .write("{\"error\":\"" + code + "\",\"message\":\"" + msg.replace("\"", "'") + "\"}");
    }
}
