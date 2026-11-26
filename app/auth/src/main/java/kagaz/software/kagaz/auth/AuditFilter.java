package kagaz.software.kagaz.auth;

import java.io.IOException;
import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Records who ran which state-changing or processing API call, and whether it succeeded. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@ConditionalOnProperty(name = "security.enableLogin", havingValue = "true")
public class AuditFilter extends OncePerRequestFilter {

    private static final Set<String> SKIPPED = Set.of("GET", "HEAD", "OPTIONS");

    private final ApplicationEventPublisher events;

    public AuditFilter(ApplicationEventPublisher events) {
        this.events = events;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return SKIPPED.contains(request.getMethod())
                || !path.startsWith("/api/v1/")
                || path.startsWith("/api/v1/auth/"); // auth actions publish their own events
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean threw = true;
        try {
            chain.doFilter(request, response);
            threw = false;
        } finally {
            Object u = request.getAttribute(AuthFilter.REQUEST_USER);
            String actor = u instanceof UserStore.User user ? user.username() : "anonymous";
            boolean ok = !threw && response.getStatus() < 400;
            events.publishEvent(
                    new AuthEvent(
                            "api:" + request.getMethod(),
                            actor,
                            request.getRequestURI(),
                            request.getRemoteAddr(),
                            ok));
        }
    }
}
