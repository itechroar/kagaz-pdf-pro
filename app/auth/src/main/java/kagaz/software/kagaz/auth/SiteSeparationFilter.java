package kagaz.software.kagaz.auth;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Keeps the two sites apart once {@link SiteMode#separated()}. The user site answers 404 for every
 * administrator address, so the console cannot even be discovered there. The administrator site
 * serves only administrators: anyone else who signs in gets a refusal that points them to the user
 * site. Runs after {@link AuthFilter}, which supplies the signed-in user.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 11)
@ConditionalOnProperty(name = "security.enableLogin", havingValue = "true")
public class SiteSeparationFilter extends OncePerRequestFilter {

    private final SiteMode site;

    public SiteSeparationFilter(SiteMode site) {
        this.site = site;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !site.separated();
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (!site.isAdminSite(request)) {
            if (AuthFilter.isAdminPath(path)) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            chain.doFilter(request, response);
            return;
        }
        Object who = request.getAttribute(AuthFilter.REQUEST_USER);
        if (who instanceof UserStore.User user && user.role() != UserStore.Role.ADMIN) {
            refuse(request, response);
            return;
        }
        chain.doFilter(request, response);
    }

    private static void refuse(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String message =
                "This is the administrator site and your account is not an administrator. "
                        + "Use the main site instead.";
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("text/html")) {
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter()
                    .write("<!doctype html><meta charset=utf-8><title>Administrators only</title>"
                            + "<body style=\"font:16px system-ui;margin:3rem auto;max-width:34rem\">"
                            + "<h1>Administrators only</h1><p>" + message + "</p>"
                            + "<p><a href=\"/api/v1/auth/logout\" "
                            + "onclick=\"fetch('/api/v1/auth/logout',{method:'POST'})."
                            + "then(()=>location.href='/login.html');return false\">Sign out</a></p>");
        } else {
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"admin_site_only\",\"message\":\"" + message + "\"}");
        }
    }
}
