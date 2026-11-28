package kagaz.software.kagaz.auth;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Tells the user site from the administrator site. With {@code kagaz.admin-port} set, the server
 * listens on that extra port as the administrator site and the ordinary port stops serving every
 * administrator page and API; without it one site serves both, as before. The two sites share
 * accounts, sessions and data because they are the same process.
 */
@Component
@ConditionalOnProperty(name = "security.enableLogin", havingValue = "true")
public class SiteMode {

    private final int adminPort;

    public SiteMode(@Value("${kagaz.admin-port:0}") int adminPort) {
        this.adminPort = adminPort;
    }

    public boolean separated() {
        return adminPort > 0;
    }

    /** True when the request arrived on the administrator site's own port. */
    public boolean isAdminSite(HttpServletRequest request) {
        return separated() && request.getLocalPort() == adminPort;
    }

    /** True when administrator pages are reachable on the site that received the request. */
    public boolean hasAdminConsole(HttpServletRequest request) {
        return !separated() || isAdminSite(request);
    }
}
