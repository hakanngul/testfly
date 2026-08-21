package io.testfly.ai;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Logger;

/**
 * OpenAI-compatible provider — works with DeepSeek, Google Gemini, OpenAI, Groq,
 * Together AI, Ollama, and any service that exposes the {@code /v1/chat/completions} endpoint.
 *
 * <p>Usage in {@code testfly.yml}:
 * <pre>
 * ai:
 *   provider: openai-compatible
 *   baseUrl: https://api.deepseek.com        # DeepSeek
 *   model: deepseek-chat                      # ~$0.14/1M tokens
 *   apiKey: ${DEEPSEEK_API_KEY}
 *
 *   # Google Gemini:
 *   baseUrl: https://generativelanguage.googleapis.com/v1beta/openai
 *   model: gemini-2.0-flash
 *   apiKey: ${GEMINI_API_KEY}
 *
 *   # Local Ollama:
 *   baseUrl: http://localhost:11434/v1
 *   model: llama3.2
 *   apiKey: ollama                            # not used, but field required
 * </pre>
 */
public final class OpenAiCompatibleProvider implements AiProvider {

    private static final Logger LOG = Logger.getLogger(OpenAiCompatibleProvider.class.getName());

    private final String baseUrl;

    /**
     * @param baseUrl API base URL, e.g. {@code https://api.deepseek.com}.
     *                {@code /v1/chat/completions} is appended automatically.
     */
    public OpenAiCompatibleProvider(String baseUrl) {
        this.baseUrl = baseUrl != null && baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
    }

    @Override
    public String name() {
        return "openai-compatible";
    }

    @Override
    public String call(String apiKey, String model, String prompt, int timeoutSeconds) {
        try {
            String url = baseUrl + "/v1/chat/completions";
            String body = buildRequestBody(model, prompt);

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                    .build();

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("content-type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .POST(HttpRequest.BodyPublishers.ofString(body));

            HttpRequest request = reqBuilder.build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return extractContent(response.body());
            }
            LOG.warning("[OpenAiCompatible] HTTP " + response.statusCode()
                    + ": " + response.body().substring(0, Math.min(200, response.body().length())));
            return null;
        } catch (Exception e) {
            LOG.warning("[OpenAiCompatible] HTTP call failed: " + e.getMessage());
            return null;
        }
    }

    private static String buildRequestBody(String model, String prompt) {
        String escaped = ClaudeProvider.escapeJson(prompt);
        return "{\"model\":\"" + model + "\","
                + "\"max_tokens\":512,"
                + "\"messages\":[{\"role\":\"user\",\"content\":\"" + escaped + "\"}]"
                + "}";
    }

    /** Extracts {@code choices[0].message.content} from an OpenAI-compatible response. */
    public static String extractContent(String json) {
        int contentIdx = json.indexOf("\"content\"");
        if (contentIdx < 0) return null;
        int colon = json.indexOf(':', contentIdx);
        if (colon < 0) return null;

        // Handle null content
        int nextNonSpace = colon + 1;
        while (nextNonSpace < json.length() && json.charAt(nextNonSpace) == ' ') nextNonSpace++;
        if (nextNonSpace < json.length() && json.charAt(nextNonSpace) == 'n') return null; // null

        int start = json.indexOf('"', nextNonSpace);
        if (start < 0) return null;

        StringBuilder sb = new StringBuilder();
        int i = start + 1;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                char next = json.charAt(i + 1);
                switch (next) {
                    case '"':  sb.append('"');  i += 2; continue;
                    case '\\': sb.append('\\'); i += 2; continue;
                    case 'n':  sb.append('\n'); i += 2; continue;
                    case 'r':  sb.append('\r'); i += 2; continue;
                    case 't':  sb.append('\t'); i += 2; continue;
                    default:   sb.append(next); i += 2; continue;
                }
            }
            if (c == '"') break;
            sb.append(c);
            i++;
        }
        String result = sb.toString().trim();
        return result.isEmpty() ? null : result;
    }
}
