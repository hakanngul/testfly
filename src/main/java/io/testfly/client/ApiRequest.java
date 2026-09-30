package io.testfly.client;

import io.testfly.api.TestFlyApi;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.*;

/** Immutable, replayable request presented to API interceptors. */
@TestFlyApi(since = "1.1.0")
public final class ApiRequest {
    private final String method;
    private final URI uri;
    private final Duration timeout;
    private final Map<String, List<String>> headers;
    private final byte[] body;

    private ApiRequest(Builder b) {
        method = Objects.requireNonNull(b.method, "method");
        uri = Objects.requireNonNull(b.uri, "uri");
        timeout = Objects.requireNonNull(b.timeout, "timeout");
        if (!uri.isAbsolute() || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())))
            throw new IllegalArgumentException("An absolute HTTP(S) URI is required");
        if (timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("timeout must be positive");
        Map<String, List<String>> copy = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        b.headers.forEach((k, v) -> copy.put(k, List.copyOf(v)));
        headers = Collections.unmodifiableMap(copy);
        body = b.body.clone();
        // Validate method and header syntax with the same rules as the transport.
        toHttpRequest();
    }

    public static Builder builder() { return new Builder(); }
    public Builder newBuilder() {
        Builder b = new Builder().method(method).uri(uri).timeout(timeout).body(body);
        headers.forEach((k, values) -> values.forEach(v -> b.addHeader(k, v)));
        return b;
    }
    public String method() { return method; }
    public URI uri() { return uri; }
    public Duration timeout() { return timeout; }
    public Map<String, List<String>> headers() { return headers; }
    public String header(String name) {
        List<String> values = headers.get(name);
        return values == null || values.isEmpty() ? null : values.getFirst();
    }
    public byte[] body() { return body.clone(); }
    public HttpRequest toHttpRequest() {
        HttpRequest.Builder b = HttpRequest.newBuilder(uri).timeout(timeout);
        headers.forEach((k, values) -> values.forEach(v -> b.header(k, v)));
        return b.method(method, HttpRequest.BodyPublishers.ofByteArray(body)).build();
    }

    @TestFlyApi(since = "1.1.0")
    public static final class Builder {
        private String method = "GET";
        private URI uri;
        private Duration timeout = Duration.ofSeconds(30);
        private final Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        private byte[] body = new byte[0];
        private Builder() {}
        public Builder method(String value) { method = Objects.requireNonNull(value); return this; }
        public Builder uri(URI value) { uri = Objects.requireNonNull(value); return this; }
        public Builder timeout(Duration value) { timeout = Objects.requireNonNull(value); return this; }
        /** Replaces all values, ignoring header name case. */
        public Builder header(String name, String value) {
            headers.put(Objects.requireNonNull(name), new ArrayList<>(List.of(value))); return this;
        }
        public Builder addHeader(String name, String value) {
            headers.computeIfAbsent(Objects.requireNonNull(name), k -> new ArrayList<>()).add(Objects.requireNonNull(value)); return this;
        }
        public Builder removeHeader(String name) { headers.remove(Objects.requireNonNull(name)); return this; }
        public Builder body(byte[] value) { body = Objects.requireNonNull(value).clone(); return this; }
        public ApiRequest build() { return new ApiRequest(this); }
    }
}
