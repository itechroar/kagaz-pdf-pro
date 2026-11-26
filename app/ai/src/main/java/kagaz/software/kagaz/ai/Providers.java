package kagaz.software.kagaz.ai;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Known model providers: wire format, default endpoint, and a sensible default model. */
final class Providers {

    enum Format {
        ANTHROPIC,
        OPENAI
    }

    record Preset(
            Format format,
            String baseUrl,
            String chatPath,
            String defaultModel,
            boolean needsKey,
            boolean local) {}

    private static final String OA = "/v1/chat/completions";

    static final Map<String, Preset> ALL =
            Map.ofEntries(
                    Map.entry(
                            "anthropic",
                            new Preset(Format.ANTHROPIC, "https://api.anthropic.com", "/v1/messages", "claude-sonnet-5-5", true, false)),
                    Map.entry("openai", new Preset(Format.OPENAI, "https://api.openai.com", OA, "gpt-4o-mini", true, false)),
                    Map.entry(
                            "gemini",
                            new Preset(Format.OPENAI, "https://generativelanguage.googleapis.com", "/v1beta/openai/chat/completions", "gemini-2.0-flash", true, false)),
                    Map.entry(
                            "openrouter",
                            new Preset(Format.OPENAI, "https://openrouter.ai", "/api/v1/chat/completions", "", true, false)),
                    Map.entry(
                            "groq",
                            new Preset(Format.OPENAI, "https://api.groq.com", "/openai/v1/chat/completions", "llama-3.3-70b-versatile", true, false)),
                    Map.entry("mistral", new Preset(Format.OPENAI, "https://api.mistral.ai", OA, "mistral-small-latest", true, false)),
                    Map.entry("together", new Preset(Format.OPENAI, "https://api.together.xyz", OA, "", true, false)),
                    Map.entry("deepseek", new Preset(Format.OPENAI, "https://api.deepseek.com", "/chat/completions", "deepseek-chat", true, false)),
                    Map.entry("xai", new Preset(Format.OPENAI, "https://api.x.ai", OA, "", true, false)),
                    Map.entry("ollama", new Preset(Format.OPENAI, "http://localhost:11434", OA, "llama3.2", false, true)),
                    Map.entry("lmstudio", new Preset(Format.OPENAI, "http://localhost:1234", OA, "", false, true)),
                    Map.entry("vllm", new Preset(Format.OPENAI, "http://localhost:8000", OA, "", false, true)),
                    // Any other server that speaks the OpenAI chat format: set kagaz.ai.base-url.
                    Map.entry("custom", new Preset(Format.OPENAI, "", OA, "", false, false)));

    private Providers() {}

    static Optional<Preset> find(String name) {
        return Optional.ofNullable(ALL.get(name == null ? "" : name.trim().toLowerCase(Locale.ROOT)));
    }

    /** True when the URL points at this machine or a private network (no internet needed). */
    static boolean isLocalUrl(String url) {
        String host;
        try {
            host = java.net.URI.create(url).getHost();
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (host == null) {
            return false;
        }
        String h = host.toLowerCase(Locale.ROOT);
        if (h.equals("localhost") || h.equals("::1") || h.equals("[::1]") || h.endsWith(".local")
                || h.endsWith(".internal") || h.endsWith(".lan") || !h.contains(".") && !h.contains(":")) {
            return true;
        }
        if (h.startsWith("127.") || h.startsWith("10.") || h.startsWith("192.168.")
                || h.startsWith("169.254.")) {
            return true;
        }
        if (h.startsWith("172.")) {
            try {
                int second = Integer.parseInt(h.split("\\.")[1]);
                return second >= 16 && second <= 31;
            } catch (RuntimeException e) {
                return false;
            }
        }
        return false;
    }
}
