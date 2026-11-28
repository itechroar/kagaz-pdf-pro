package kagaz.software.kagaz.auth;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Adds baseline browser hardening headers to every response. The CSP is applied only to the
 * static auth/admin/AI pages, which ship no inline script; the editor bundle is left alone because
 * a strict policy there would break it. HSTS is sent only on secure requests so plain-HTTP local
 * and offline use is unaffected. Auth API responses are never cacheable.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class SecurityHeadersFilter extends OncePerRequestFilter {

    static final String CSP =
            "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:;"
                    + " frame-ancestors 'self'; base-uri 'self'; form-action 'self'";

    private static final String[] CSP_PAGES = {
        "/login.html", "/register.html", "/profile.html", "/admin-user.html", "/admin-users.html", "/admin-audit.html", "/admin-features.html", "/admin-dashboard.html", "/developer.html", "/ai.html"
    };

    @Override
    protected void doFilterInternal(
            HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        res.setHeader("X-Content-Type-Options", "nosniff");
        res.setHeader("X-Frame-Options", "SAMEORIGIN");
        res.setHeader("Referrer-Policy", "same-origin");
        res.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        if (req.isSecure()) {
            res.setHeader("Strict-Transport-Security", "max-age=31536000");
        }
        String path = req.getRequestURI();
        if (path.startsWith("/api/v1/auth/")) {
            res.setHeader("Cache-Control", "no-store");
        }
        for (String page : CSP_PAGES) {
            if (path.equals(page)) {
                res.setHeader("Content-Security-Policy", CSP);
                break;
            }
        }
        chain.doFilter(req, res);
    }
}
