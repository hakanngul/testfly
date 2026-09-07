package io.testfly.ai;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Registry of {@link AiProvider} implementations.
 *
 * <p>Built-in providers:
 * <ul>
 *   <li>{@code claude} — Anthropic Claude (native Messages API)</li>
 *   <li>{@code gemini} — Google Gemini (native Google Generative AI REST API)</li>
 *   <li>{@code openai-compatible} — DeepSeek, OpenAI, Groq, Ollama, etc.</li>
 * </ul>
 *
 * <p>Custom providers can be registered programmatically:
 * <pre>
 * AiProviderRegistry.register(new MyCustomProvider());
 * </pre>
 */
public final class AiProviderRegistry {

    private static final Logger LOG = Logger.getLogger(AiProviderRegistry.class.getName());

    private static final Map<String, AiProvider> PROVIDERS = new ConcurrentHashMap<>();

    static {
        register(new ClaudeProvider());
        register(new GeminiProvider());
    }

    private AiProviderRegistry() {}

    public static void register(AiProvider provider) {
        PROVIDERS.put(provider.name(), provider);
        LOG.fine("[AiProviderRegistry] Registered provider: " + provider.name());
    }

    /**
     * Returns the provider for the given name.
     *
     * <p>For {@code "openai-compatible"} or {@code "openai"}, a new instance is created using the supplied
     * base URL. For {@code "claude"} or {@code "anthropic"}, an instance is created using the custom base URL if supplied.
     * For all other names, the registered singleton is returned.
     *
     * @param name    provider name from {@code ai.provider} in {@code testfly.yml}
     * @param baseUrl base URL for the provider (optional)
     * @return the provider, or {@code null} if not found
     */
    public static AiProvider get(String name, String baseUrl) {
        if (name == null || name.isBlank()) {
            return new ClaudeProvider(baseUrl);
        }

        String normalized = name.toLowerCase().trim();
        if ("openai-compatible".equals(normalized) || "openai".equals(normalized)) {
            String url = baseUrl != null ? baseUrl : "https://api.openai.com";
            return new OpenAiCompatibleProvider(url);
        }

        if ("claude".equals(normalized) || "anthropic".equals(normalized)) {
            return new ClaudeProvider(baseUrl);
        }

        return PROVIDERS.get(normalized);
    }

    /** Returns all registered provider names. */
    public static java.util.Set<String> availableProviders() {
        java.util.Set<String> names = new java.util.LinkedHashSet<>(PROVIDERS.keySet());
        names.add("openai-compatible");
        names.add("openai");
        names.add("anthropic");
        return names;
    }
}
