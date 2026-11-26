package kagaz.software.kagaz.auth;

import java.security.SecureRandom;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Creates the first administrator when login is enabled and no users exist. The password comes
 * from {@code kagaz.auth.admin-password} (env {@code KAGAZ_AUTH_ADMIN_PASSWORD}); if unset, a
 * random one is generated, printed once to the log, and must be changed at first login.
 */
@Component
@ConditionalOnProperty(name = "security.enableLogin", havingValue = "true")
public class BootstrapAdmin implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdmin.class);

    private final UserStore store;
    private final String username;
    private final String password;

    public BootstrapAdmin(
            UserStore store,
            @Value("${kagaz.auth.admin-username:admin}") String username,
            @Value("${kagaz.auth.admin-password:}") String password) {
        this.store = store;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!store.isEmpty()) {
            return;
        }
        if (password != null && !password.isBlank()) {
            store.create(username, password, UserStore.Role.ADMIN, false);
            log.info("Created initial administrator '{}'", username);
            return;
        }
        byte[] buf = new byte[12];
        new SecureRandom().nextBytes(buf);
        String generated = Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
        store.create(username, generated, UserStore.Role.ADMIN, true);
        log.warn(
                "Created initial administrator '{}' with one-time password: {}  "
                        + "(you must change it at first login)",
                username,
                generated);
    }
}
