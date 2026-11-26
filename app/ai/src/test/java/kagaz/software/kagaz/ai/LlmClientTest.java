package kagaz.software.kagaz.ai;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class LlmClientTest {

    private static LlmClient client(String provider, String base, String key, String model, boolean offline) {
        return new LlmClient(provider, base, "", key, model, 30, offline);
    }

    @Test
    void anthropicRequestAndReply() {
        Map<String, Object> body = LlmClient.requestBody(Providers.Format.ANTHROPIC, "m1", "sys", "hi");
        assertEquals("sys", body.get("system"));
        Map<String, Object> reply = Map.of("content", List.of(Map.of("type", "text", "text", "hello")));
        assertEquals("hello", LlmClient.parseReply(Providers.Format.ANTHROPIC, reply));
    }

    @Test
    void openaiRequestAndReply() {
        Map<String, Object> body = LlmClient.requestBody(Providers.Format.OPENAI, "m1", "sys", "hi");
        assertEquals(2, ((List<?>) body.get("messages")).size());
        Map<String, Object> reply = Map.of("choices", List.of(Map.of("message", Map.of("content", "hello"))));
        assertEquals("hello", LlmClient.parseReply(Providers.Format.OPENAI, reply));
    }

    @Test
    void malformedRepliesAreRejected() {
        assertThrows(LlmClient.LlmException.class, () -> LlmClient.parseReply(Providers.Format.ANTHROPIC, Map.of()));
        assertThrows(LlmClient.LlmException.class, () -> LlmClient.parseReply(Providers.Format.OPENAI, Map.of("choices", List.of())));
        assertThrows(LlmClient.LlmException.class,
                () -> LlmClient.parseReply(Providers.Format.ANTHROPIC, Map.of("content", List.of(Map.of("text", " ")))));
    }

    @Test
    void constructorNeverThrowsAndExplainsProblems() {
        assertFalse(client("", "", "", "", false).enabled());
        assertTrue(client("nonsense", "", "k", "m", false).problem().contains("Unknown AI provider"));
        assertTrue(client("anthropic", "", "", "", false).problem().contains("API key"));
        assertNull(client("anthropic", "", "sk-x", "", false).problem());
        assertTrue(client("openrouter", "", "k", "", false).problem().contains("KAGAZ_AI_MODEL"));
        assertTrue(client("custom", "", "", "m", false).problem().contains("BASE_URL"));
        assertTrue(client("custom", "ftp://x", "", "m", false).problem().contains("http"));
        assertNull(client("ollama", "", "", "", false).problem());
        assertNull(client("custom", "http://my-server:8000", "", "m", false).problem());
    }

    @Test
    void notConfiguredCallsFailCleanly() {
        LlmClient c = client("anthropic", "", "", "", false);
        LlmClient.LlmException e = assertThrows(LlmClient.LlmException.class, () -> c.complete("s", "u"));
        assertTrue(e.isNotConfigured());
    }

    @Test
    void offlineModeBlocksRemoteServers() {
        assertTrue(client("openai", "", "k", "", true).problem().contains("Offline"));
        assertNull(client("ollama", "", "", "", true).problem());
        assertNull(client("custom", "http://10.0.0.5:8000", "", "m", true).problem());
        assertTrue(client("custom", "https://api.example.com", "", "m", true).problem().contains("Offline"));
    }

    @Test
    void localUrlDetection() {
        assertTrue(Providers.isLocalUrl("http://localhost:11434"));
        assertTrue(Providers.isLocalUrl("http://192.168.1.4"));
        assertTrue(Providers.isLocalUrl("http://172.20.0.2"));
        assertTrue(Providers.isLocalUrl("http://ollama:11434"));
        assertFalse(Providers.isLocalUrl("http://172.40.0.2"));
        assertFalse(Providers.isLocalUrl("https://api.openai.com"));
    }

    @Test
    void everyPresetIsUsable() {
        for (var e : Providers.ALL.entrySet()) {
            assertTrue(e.getValue().chatPath().startsWith("/"), e.getKey());
        }
    }
}
