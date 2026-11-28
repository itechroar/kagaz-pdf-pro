package kagaz.software.SPDF.config;

import org.eclipse.jetty.server.ServerConnector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.jetty.JettyServerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Opens the administrator site's port next to the main one when {@code kagaz.admin-port} is set.
 * It binds to {@code kagaz.admin-bind}, else {@code server.address}, else every interface. Which
 * requests may use which port is decided in the auth module's {@code SiteMode}.
 */
@Configuration
@ConditionalOnExpression("${kagaz.admin-port:0} > 0")
public class AdminPortConnector {

    @Bean
    public JettyServerCustomizer adminSiteConnector(
            @Value("${kagaz.admin-port}") int port,
            @Value("${kagaz.admin-bind:${server.address:}}") String bind) {
        return server -> {
            ServerConnector connector = new ServerConnector(server);
            connector.setPort(port);
            if (!bind.isBlank()) {
                connector.setHost(bind);
            }
            server.addConnector(connector);
        };
    }
}
