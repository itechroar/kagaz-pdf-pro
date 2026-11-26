package kagaz.software.kagaz.auth;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Optional per-user daily cap on {@code /api/v1/} processing calls
 * ({@code kagaz.auth.quota.daily-requests}, 0 = unlimited). Administrators are exempt. Counts live
 * in memory and reset at 00:00 UTC and on restart, so this limits runaway scripts rather than
 * metering for billing.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 15)
@ConditionalOnProperty(name = "security.enableLogin", havingValue = "true")
public class QuotaFilter extends OncePerRequestFilter {

    private final int limit;
    private final Clock clock;
    private final Map<String, AtomicInteger> counts = new ConcurrentHashMap<>();
    private volatile LocalDate day;

    @Autowired
    public QuotaFilter(@Value("${kagaz.auth.quota.daily-requests:0}") int limit) {
        this(limit, Clock.systemUTC());
    }

    QuotaFilter(int limit, Clock clock) {
        this.limit = limit;
        this.clock = clock;
        this.day = LocalDate.now(clock);
    }

    public int limit() {
        return limit;
    }

    public int usedToday(String username) {
        rollover();
        AtomicInteger c = counts.get(username);
        return c == null ? 0 : c.get();
    }

    private synchronized void rollover() {
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        if (!today.equals(day)) {
            counts.clear();
            day = today;
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return limit <= 0 || !path.startsWith("/api/v1/") || path.startsWith("/api/v1/auth/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Object u = request.getAttribute(AuthFilter.REQUEST_USER);
        if (!(u instanceof UserStore.User user) || user.role() == UserStore.Role.ADMIN) {
            chain.doFilter(request, response);
            return;
        }
        rollover();
        int used = counts.computeIfAbsent(user.username(), k -> new AtomicInteger()).incrementAndGet();
        if (used > limit) {
            response.setStatus(429);
            response.setHeader("Retry-After", "3600");
            response.setContentType("application/json");
            response.getWriter()
                    .write("{\"error\":\"quota_exceeded\",\"message\":\"Daily request limit reached\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
