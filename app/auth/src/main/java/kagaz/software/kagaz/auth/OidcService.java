package kagaz.software.kagaz.auth;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

/**
 * OpenID Connect authorization-code flow with PKCE (works with Keycloak, Okta, Auth0, Google,
 * Microsoft Entra and any other compliant provider).
 *
 * <p>The ID token is received directly from the provider's token endpoint over TLS, which OpenID
 * Connect Core section 3.1.3.7 permits to be trusted without checking its signature; issuer,
 * audience, expiry and nonce are still verified.
 */
@Component
@ConditionalOnExpression(
        "'${security.enableLogin:false}' == 'true' && !'${kagaz.auth.oidc.issuer:}'.isEmpty()"
                + " && !'${kagaz.auth.oidc.client-id:}'.isEmpty() && !'${kagaz.auth.public-url:}'.isEmpty()")
public class OidcService {

    public record Endpoints(String authorization, String token) {}

    public record Claims(String subject, String email, boolean emailVerified, String name) {}

    private final String issuer;
    private final String clientId;
    private final String clientSecret;
    private final String scopes;
    private final String redirectUri;
    private final String label;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();
    private final SecureRandom random = new SecureRandom();
    private volatile Endpoints endpoints;

    public OidcService(
            @Value("${kagaz.auth.oidc.issuer}") String issuer,
            @Value("${kagaz.auth.oidc.client-id:}") String clientId,
            @Value("${kagaz.auth.oidc.client-secret:}") String clientSecret,
            @Value("${kagaz.auth.oidc.scopes:openid email profile}") String scopes,
            @Value("${kagaz.auth.public-url:}") String publicUrl,
            @Value("${kagaz.auth.oidc.label:Single sign-on}") String label) {
        if (clientId.isBlank() || publicUrl.isBlank()) {
            throw new IllegalStateException(
                    "SSO needs kagaz.auth.oidc.client-id and kagaz.auth.public-url");
        }
        this.issuer = issuer.endsWith("/") ? issuer.substring(0, issuer.length() - 1) : issuer;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.scopes = scopes;
        this.label = label;
        String base = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
        this.redirectUri = base + "/api/v1/auth/sso/callback";
    }

    public String label() {
        return label;
    }

    public String randomToken() {
        byte[] b = new byte[32];
        random.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    public static String pkceChallenge(String verifier) {
        try {
            byte[] d =
                    MessageDigest.getInstance("SHA-256")
                            .digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(d);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public String authorizationUrl(String state, String nonce, String verifier) {
        return endpoints().authorization()
                + (endpoints().authorization().contains("?") ? "&" : "?")
                + "response_type=code"
                + "&client_id=" + enc(clientId)
                + "&redirect_uri=" + enc(redirectUri)
                + "&scope=" + enc(scopes)
                + "&state=" + enc(state)
                + "&nonce=" + enc(nonce)
                + "&code_challenge=" + enc(pkceChallenge(verifier))
                + "&code_challenge_method=S256";
    }

    /** Exchanges the code and returns validated claims; throws on any failure. */
    public Claims redeem(String code, String verifier, String nonce) {
        String form =
                "grant_type=authorization_code"
                        + "&code=" + enc(code)
                        + "&redirect_uri=" + enc(redirectUri)
                        + "&client_id=" + enc(clientId)
                        + "&code_verifier=" + enc(verifier)
                        + (clientSecret.isBlank() ? "" : "&client_secret=" + enc(clientSecret));
        Map<?, ?> body = sendJson(
                HttpRequest.newBuilder(URI.create(endpoints().token()))
                        .timeout(Duration.ofSeconds(15))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("Accept", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(form))
                        .build());
        Object idToken = body.get("id_token");
        if (!(idToken instanceof String jwt)) {
            throw new IllegalStateException("Provider returned no id_token");
        }
        return validate(payload(jwt), issuer, clientId, nonce, System.currentTimeMillis() / 1000);
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> payload(String jwt) {
        String[] parts = jwt.split("\\.");
        if (parts.length != 3) {
            throw new IllegalStateException("Malformed id_token");
        }
        byte[] json = Base64.getUrlDecoder().decode(parts[1]);
        return mapper.readValue(new String(json, StandardCharsets.UTF_8), Map.class);
    }

    /** Pure validation of ID-token claims; static so it can be unit-tested without a provider. */
    static Claims validate(
            Map<String, Object> c, String issuer, String clientId, String nonce, long nowSeconds) {
        String iss = str(c.get("iss"));
        if (iss == null || !stripSlash(iss).equals(stripSlash(issuer))) {
            throw new IllegalStateException("Issuer mismatch");
        }
        Object aud = c.get("aud");
        boolean audOk =
                aud instanceof String s
                        ? s.equals(clientId)
                        : aud instanceof Collection<?> col && col.contains(clientId);
        if (!audOk) {
            throw new IllegalStateException("Audience mismatch");
        }
        if (!(c.get("exp") instanceof Number exp) || exp.longValue() < nowSeconds - 60) {
            throw new IllegalStateException("Token expired");
        }
        if (nonce == null || !nonce.equals(str(c.get("nonce")))) {
            throw new IllegalStateException("Nonce mismatch");
        }
        String sub = str(c.get("sub"));
        if (sub == null) {
            throw new IllegalStateException("Token has no subject");
        }
        String email = str(c.get("email"));
        boolean verified = !Boolean.FALSE.equals(c.get("email_verified"))
                && !"false".equals(str(c.get("email_verified")));
        String name = str(c.get("name"));
        return new Claims(sub, email == null ? null : email.toLowerCase(Locale.ROOT), verified, name);
    }

    private static String stripSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private static String str(Object o) {
        return o instanceof String s && !s.isBlank() ? s : null;
    }

    private Endpoints endpoints() {
        Endpoints e = endpoints;
        if (e == null) {
            Map<?, ?> doc =
                    sendJson(
                            HttpRequest.newBuilder(
                                            URI.create(issuer + "/.well-known/openid-configuration"))
                                    .timeout(Duration.ofSeconds(10))
                                    .header("Accept", "application/json")
                                    .GET()
                                    .build());
            String auth = str(doc.get("authorization_endpoint"));
            String token = str(doc.get("token_endpoint"));
            if (auth == null || token == null) {
                throw new IllegalStateException("Provider discovery document is incomplete");
            }
            e = new Endpoints(auth, token);
            endpoints = e;
        }
        return e;
    }

    private Map<?, ?> sendJson(HttpRequest request) {
        try {
            HttpResponse<String> r = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() / 100 != 2) {
                throw new IllegalStateException("Provider returned HTTP " + r.statusCode());
            }
            return mapper.readValue(r.body(), Map.class);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot reach identity provider", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted", e);
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    static List<String> csv(String s) {
        if (s == null || s.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(s.split(","))
                .map(v -> v.trim().toLowerCase(Locale.ROOT))
                .filter(v -> !v.isEmpty())
                .toList();
    }
}
