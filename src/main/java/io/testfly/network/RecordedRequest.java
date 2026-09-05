package io.testfly.network;

import io.testfly.api.TestFlyApi;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * An intercepted request captured for later verification via {@code NetworkAssert}.
 *
 * <p>Header keys are stored lower-cased (HTTP header names are case-insensitive)
 * so lookups do not need to scan. The record is immutable and thread-confined to
 * the test thread that recorded it.
 */
@TestFlyApi(since = "1.6.0")
public record RecordedRequest(
        String             url,
        String             method,     // uppercase
        Map<String,String> headers,    // lower-cased keys, insertion order preserved
        String             body,       // request body, or null when absent
        Instant            timestamp) {

    public RecordedRequest {
        headers = normalize(headers);
    }

    /** Case-insensitive header lookup. */
    public Optional<String> header(String name) {
        if (name == null) return Optional.empty();
        return Optional.ofNullable(headers.get(name.toLowerCase(Locale.ROOT)));
    }

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
}
