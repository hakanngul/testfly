package io.testfly.ai;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Logger;

/**
 * Anthropic Claude provider.
 *
 * <p>Uses the native Messages API ({@code https://api.anthropic.com/v1/messages}).
 * Default model: {@code claude-haiku-4-5-20251001}.
 */
public final class ClaudeProvider implements AiProvider {

    private static final Logger LOG = Logger.getLogger(ClaudeProvider.class.getName());
    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String API_VERSION = "2023-06-01";

    @Override
    public String name() {
        return "claude";
    }

    @Override
    public String call(String apiKey, String model, String prompt, int timeoutSeconds) {
        try {
            String body = buildRequestBody(model, prompt);

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", API_VERSION)
                    .header("content-type", "application/json")
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return extractContent(response.body());
            }
            LOG.warning("[ClaudeProvider] HTTP " + response.statusCode()
                    + ": " + response.body().substring(0, Math.min(200, response.body().length())));
            return null;
        } catch (Exception e) {
            LOG.warning("[ClaudeProvider] HTTP call failed: " + e.getMessage());
            return null;
        }
    }

    private static String buildRequestBody(String model, String prompt) {
        String escaped = escapeJson(prompt);
        return "{\"model\":\"" + model + "\","
                + "\"max_tokens\":512,"
                + "\"messages\":[{\"role\":\"user\",\"content\":\"" + escaped + "\"}]"
                + "}";
    }

    /** Extracts {@code content[0].text} from a Claude API response. */
    public static String extractContent(String json) {
        int textIdx = json.indexOf("\"text\"");
        if (textIdx < 0) return null;
        int colon = json.indexOf(':', textIdx);
        if (colon < 0) return null;
        int start = json.indexOf('"', colon + 1);
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

    public static String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "")
                .replace("\t", "\\t");
    }
}
