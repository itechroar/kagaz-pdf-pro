package kagaz.software.kagaz.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Explains in the log why SSO is off when it was only partly configured. Never blocks startup. */
@Component
@ConditionalOnProperty(name = "security.enableLogin", havingValue = "true")
public class SsoConfigCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SsoConfigCheck.class);

    private final String issuer;
    private final String clientId;
    private final String publicUrl;

    public SsoConfigCheck(
            @Value("${kagaz.auth.oidc.issuer:}") String issuer,
            @Value("${kagaz.auth.oidc.client-id:}") String clientId,
            @Value("${kagaz.auth.public-url:}") String publicUrl) {
        this.issuer = issuer;
        this.clientId = clientId;
        this.publicUrl = publicUrl;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!issuer.isBlank() && (clientId.isBlank() || publicUrl.isBlank())) {
            log.warn(
                    "SSO is OFF: KAGAZ_AUTH_OIDC_ISSUER is set but KAGAZ_AUTH_OIDC_CLIENT_ID and"
                            + " KAGAZ_AUTH_PUBLIC_URL are both required.");
        }
    }
}
