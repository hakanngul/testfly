package io.testfly.ai;

/**
 * Abstraction over different LLM providers (Claude, DeepSeek, Gemini, OpenAI, etc.).
 *
 * <p>Each provider knows how to format the request and parse the response
 * for its specific API. Implementations are registered in {@link AiProviderRegistry}.
 */
public interface AiProvider {

    /** Provider identifier used in {@code testfly.yml} ({@code ai.provider}). */
    String name();

    /**
     * Sends the given prompt and returns the model's text response.
     *
     * @param apiKey         API key or token
     * @param model          model identifier (e.g. {@code deepseek-chat}, {@code gemini-2.0-flash})
     * @param prompt         the user prompt
     * @param timeoutSeconds HTTP timeout
     * @return the model's text response, or {@code null} on failure
     */
    String call(String apiKey, String model, String prompt, int timeoutSeconds);
}
