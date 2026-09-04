package io.testfly.ai;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Logger;

/**
 * Google Gemini provider.
 *
 * <p>Uses the Google Generative AI REST API
 * ({@code https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent}).
 * Default model: {@code gemini-2.0-flash}.
 *
 * <p>Usage in {@code testfly.yml}:
 * <pre>
 * ai:
 *   failureAnalysis: true
 *   provider: gemini
 *   apiKey: ${GEMINI_API_KEY}
 *   model: gemini-2.0-flash
 * </pre>
 */
public final class GeminiProvider implements AiProvider {

    private static final Logger LOG = Logger.getLogger(GeminiProvider.class.getName());
    private static final String DEFAULT_MODEL = "gemini-2.0-flash";
    private static final String API_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";

    private static final java.util.regex.Pattern MODEL_PATTERN = java.util.regex.Pattern.compile("^[a-zA-Z0-9._-]+$");

    @Override
    public String name() {
        return "gemini";
    }

    @Override
    public String call(String apiKey, String model, String prompt, int timeoutSeconds) {
        try {
            String selectedModel = (model != null && !model.isBlank()) ? model.strip() : DEFAULT_MODEL;
            if (!MODEL_PATTERN.matcher(selectedModel).matches()) {
                LOG.warning("[GeminiProvider] Invalid model name: " + selectedModel);
                return null;
            }
            String url = API_BASE_URL + selectedModel + ":generateContent";
            String body = buildRequestBody(prompt);

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("x-goog-api-key", apiKey)
                    .header("content-type", "application/json")
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return extractContent(response.body());
            }
            LOG.warning("[GeminiProvider] HTTP " + response.statusCode()
                    + ": " + response.body().substring(0, Math.min(200, response.body().length())));
            return null;
        } catch (Exception e) {
            LOG.warning("[GeminiProvider] HTTP call failed: " + e.getMessage());
            return null;
        }
    }

    public static String buildRequestBody(String prompt) {
        String escaped = escapeJson(prompt);
        return "{\"contents\":[{\"parts\":[{\"text\":\"" + escaped + "\"}]}],"
                + "\"generationConfig\":{\"maxOutputTokens\":512}}";
    }

    /** Extracts text content from a Gemini generateContent API JSON response. */
    public static String extractContent(String json) {
        if (json == null) return null;
        int partsIdx = json.indexOf("\"parts\"");
        int searchStart = partsIdx >= 0 ? partsIdx : 0;
        int textIdx = json.indexOf("\"text\"", searchStart);
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
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "")
                .replace("\t", "\\t");
    }
}
