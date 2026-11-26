package kagaz.software.kagaz.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Logs a startup warning when login is off, so open access is never silent. */
@Component
public class OpenAccessWarning {

    private static final Logger log = LoggerFactory.getLogger(OpenAccessWarning.class);

    private final boolean loginEnabled;

    public OpenAccessWarning(@Value("${security.enableLogin:false}") boolean loginEnabled) {
        this.loginEnabled = loginEnabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warn() {
        if (!loginEnabled) {
            log.warn(
                    "Login is disabled: anyone who can reach this server can use it. Fine for"
                            + " localhost/desktop; set security.enableLogin=true before exposing it.");
        }
    }
}
