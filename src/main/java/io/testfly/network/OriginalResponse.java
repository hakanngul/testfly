package io.testfly.network;

import io.testfly.api.TestFlyApi;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Immutable snapshot of the real backend response returned by
 * {@link Route#fetchOriginal()}. Header keys are lower-cased.
 */
@TestFlyApi(since = "1.6.0")
public final class OriginalResponse {

    private final int                 status;
    private final Map<String, String> headers;   // lower-cased keys
    private final String              body;

    OriginalResponse(int status, Map<String, String> headers, String body) {
        this.status  = status;
        this.headers = normalize(headers);
        this.body    = body == null ? "" : body;
    }

    /** HTTP status code of the real response. */
    public int status() { return status; }

    /** Response headers (lower-cased keys, immutable). */
    public Map<String, String> headers() { return headers; }

    /** Response body decoded as UTF-8 (empty string if unavailable). */
    public String body() { return body; }

    private static Map<String, String> normalize(Map<String, String> in) {
        if (in == null || in.isEmpty()) return Map.of();
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : in.entrySet()) {
            if (e.getKey() != null) {
                out.put(e.getKey().toLowerCase(Locale.ROOT), e.getValue());
            }
        }
        return Map.copyOf(out);
    }

    @Override
    public String toString() {
        return "OriginalResponse{status=" + status + ", headers=" + headers.size()
                + ", bodyLen=" + body.length() + "}";
    }
}
