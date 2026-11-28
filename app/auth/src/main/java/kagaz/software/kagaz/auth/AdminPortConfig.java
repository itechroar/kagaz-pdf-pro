package kagaz.software.kagaz.auth;

import org.apache.catalina.connector.Connector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Opens the administrator site's port next to the main one. It binds to {@code
 * kagaz.admin-bind} when set, otherwise to {@code server.address}, otherwise to every interface.
 */
@Configuration
@ConditionalOnExpression("${kagaz.admin-port:0} > 0")
public class AdminPortConfig {

    @Bean
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> adminSiteConnector(
            @Value("${kagaz.admin-port}") int port,
            @Value("${kagaz.admin-bind:${server.address:}}") String bind) {
        return factory -> {
            Connector connector = new Connector("org.apache.coyote.http11.Http11NioProtocol");
            connector.setPort(port);
            if (!bind.isBlank()) {
                connector.setProperty("address", bind);
            }
            factory.addAdditionalConnectors(connector);
        };
    }
}
