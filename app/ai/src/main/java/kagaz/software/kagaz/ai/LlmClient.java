package kagaz.software.kagaz.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

/**
 * Sends one prompt to a language model and returns its text. Configuration never stops the server
 * from starting: a missing key, unknown provider or blocked URL is reported by {@link #problem()}
 * and surfaces as a clear error only when someone actually uses the assistant.
 */
@Component
public class LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);

    public static class LlmException extends RuntimeException {
        private final boolean notConfigured;

        public LlmException(String message, Throwable cause) {
            super(message, cause);
            this.notConfigured = false;
        }

        public LlmException(String message, boolean notConfigured) {
            super(message);
            this.notConfigured = notConfigured;
        }

        public boolean isNotConfigured() {
            return notConfigured;
        }
    }

    private final String providerName;
    private final Providers.Preset preset;
    private final String baseUrl;
    private final String chatPath;
    private final String apiKey;
    private final String model;
    private final int timeoutSeconds;
    private final String problem;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();

    public LlmClient(
            @Value("${kagaz.ai.provider:}") String provider,
            @Value("${kagaz.ai.base-url:}") String baseUrl,
            @Value("${kagaz.ai.chat-path:}") String chatPath,
            @Value("${kagaz.ai.api-key:}") String apiKey,
            @Value("${kagaz.ai.model:}") String model,
            @Value("${kagaz.ai.timeout-seconds:300}") int timeoutSeconds,
            @Value("${kagaz.offline:false}") boolean offline) {
        this.providerName = provider.trim().toLowerCase();
        Optional<Providers.Preset> found = Providers.find(providerName);
        String why = null;
        Providers.Preset p = found.orElse(null);
        String base = "";
        if (providerName.isEmpty()) {
            why = "AI is not enabled: set KAGAZ_AI_PROVIDER (" + String.join(", ", new java.util.TreeSet<>(Providers.ALL.keySet())) + ")";
        } else if (p == null) {
            why = "Unknown AI provider '" + providerName + "'. Use one of: "
                    + String.join(", ", new java.util.TreeSet<>(Providers.ALL.keySet()));
        } else {
            base = baseUrl.isBlank() ? p.baseUrl() : baseUrl.trim();
            base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
            if (base.isEmpty()) {
                why = "Set KAGAZ_AI_BASE_URL for provider '" + providerName + "'";
            } else if (!base.startsWith("http://") && !base.startsWith("https://")) {
                why = "KAGAZ_AI_BASE_URL must start with http:// or https://";
            } else if (offline && !Providers.isLocalUrl(base)) {
                why = "Offline mode is on and the model server is not local; use a local model such as Ollama";
            } else if (model.isBlank() && p.defaultModel().isEmpty()) {
                why = "Set KAGAZ_AI_MODEL for provider '" + providerName + "'";
            } else if (apiKey.isBlank() && p.needsKey() && baseUrl.isBlank()) {
                why = "Add your API key: set KAGAZ_AI_API_KEY";
            }
        }
        this.preset = p;
        this.baseUrl = base;
        this.chatPath = !chatPath.isBlank() ? chatPath.trim() : p == null ? "" : p.chatPath();
        this.apiKey = apiKey;
        this.model = !model.isBlank() ? model.trim() : p == null ? "" : p.defaultModel();
        this.timeoutSeconds = Math.max(5, timeoutSeconds);
        this.problem = why;
        if (why != null && !providerName.isEmpty()) {
            log.warn("AI assistant not ready: {}", why);
        }
    }

    /** Null when ready; otherwise a human-readable reason the assistant cannot be used yet. */
    public String problem() {
        return problem;
    }

    public boolean enabled() {
        return !providerName.isEmpty();
    }

    public String provider() {
        return providerName;
    }

    public String model() {
        return model;
    }

    public String complete(String system, String user) {
        if (problem != null) {
            throw new LlmException(problem, true);
        }
        String body;
        try {
            body = mapper.writeValueAsString(requestBody(preset.format(), model, system, user));
        } catch (RuntimeException e) {
            throw new LlmException("Could not build the model request", e);
        }
        HttpRequest.Builder req =
                HttpRequest.newBuilder(URI.create(baseUrl + chatPath))
                        .timeout(Duration.ofSeconds(timeoutSeconds))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body));
        if (preset.format() == Providers.Format.ANTHROPIC) {
            req.header("x-api-key", apiKey).header("anthropic-version", "2023-06-01");
        } else if (!apiKey.isBlank()) {
            req.header("Authorization", "Bearer " + apiKey);
        }
        try {
            HttpResponse<String> r = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() == 401 || r.statusCode() == 403) {
                throw new LlmException("The model provider rejected the API key", false);
            }
            if (r.statusCode() == 429) {
                throw new LlmException("The model provider is rate limiting requests; try again shortly", false);
            }
            if (r.statusCode() / 100 != 2) {
                throw new LlmException("Model provider returned HTTP " + r.statusCode(), false);
            }
            return parseReply(preset.format(), mapper.readValue(r.body(), Map.class));
        } catch (IOException | RuntimeException e) {
            if (e instanceof LlmException le) {
                throw le;
            }
            throw new LlmException("Cannot reach the model provider", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("Interrupted", e);
        }
    }

    static Map<String, Object> requestBody(
            Providers.Format format, String model, String system, String user) {
        if (format == Providers.Format.ANTHROPIC) {
            return Map.of(
                    "model", model,
                    "max_tokens", 4096,
                    "system", system,
                    "messages", List.of(Map.of("role", "user", "content", user)));
        }
        return Map.of(
                "model", model,
                "messages",
                        List.of(
                                Map.of("role", "system", "content", system),
                                Map.of("role", "user", "content", user)));
    }

    /** Pulls the reply text out of a decoded provider response; throws if the shape is wrong. */
    static String parseReply(Providers.Format format, Map<?, ?> response) {
        Object text = null;
        if (format == Providers.Format.ANTHROPIC) {
            if (response.get("content") instanceof List<?> parts) {
                StringBuilder sb = new StringBuilder();
                for (Object part : parts) {
                    if (part instanceof Map<?, ?> m && m.get("text") instanceof String s) {
                        sb.append(s);
                    }
                }
                text = sb.toString();
            }
        } else if (response.get("choices") instanceof List<?> choices
                && !choices.isEmpty()
                && choices.get(0) instanceof Map<?, ?> first
                && first.get("message") instanceof Map<?, ?> message) {
            text = message.get("content");
        }
        if (!(text instanceof String s) || s.isBlank()) {
            throw new LlmException("Model provider returned an unexpected response", false);
        }
        return s;
    }
}
