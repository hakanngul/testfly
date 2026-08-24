package io.testfly.client;

import io.testfly.api.TestFlyApi;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reusable request specification — DRY for headers, auth, baseUrl, query defaults.
 * Immutable after build, thread-safe.
 *
 * <pre>
 * ApiRequestSpec spec = ApiRequestSpec.builder()
 *     .baseUrl("https://api.example.com")
 *     .header("Accept", "application/json")
 *     .bearerToken(token)
 *     .build();
 *
 * apiClient().spec(spec).get("/users/{id}").pathParam("id", 1).send();
 * ApiClient.setGlobalSpec(spec); // all requests use it
 * </pre>
 */
@TestFlyApi(since = "1.10.0")
public final class ApiRequestSpec {

    private final String baseUrl;
    private final Map<String, String> headers;
    private final Map<String, String> queryParams;
    private final ApiAuth auth;
    private final String contentType;

    private ApiRequestSpec(Builder b) {
        this.baseUrl = b.baseUrl;
        this.headers = Map.copyOf(b.headers);
        this.queryParams = Map.copyOf(b.queryParams);
        this.auth = b.auth;
        this.contentType = b.contentType;
    }

    public static Builder builder() { return new Builder(); }

    /** Apply spec to client — overwrites existing values. */
    public void applyTo(ApiClient client) {
        if (baseUrl != null) client.baseUrl(baseUrl);
        headers.forEach(client::header);
        queryParams.forEach(client::queryParam);
        if (auth != null) client.auth(auth);
        if (contentType != null) client.contentType(contentType);
    }

    /** Apply only if client hasn't already set the value — global spec uses this. */
    void applyToIfAbsent(ApiClient client) {
        // baseUrl: only if client hasn't set one
        // We check via reflection-free approach: ApiClient.baseUrl is null means not set
        // So we need a getter — add package-private getter in ApiClient
        // For now, apply headers/query only if absent is not trivial without getters.
        // Simplest: apply headers that don't already exist, query that don't exist.
        // baseUrl/auth/contentType only if client hasn't set them.
        // To keep it simple, we apply headers/query additively and baseUrl/auth only if client field is null.
        // ApiClient will expose package-private hasBaseUrl/hasAuth checks.
        if (baseUrl != null && !client.hasBaseUrl()) client.baseUrl(baseUrl);
        for (Map.Entry<String, String> e : headers.entrySet()) {
            if (!client.hasHeader(e.getKey())) client.header(e.getKey(), e.getValue());
        }
        for (Map.Entry<String, String> e : queryParams.entrySet()) {
            if (!client.hasQueryParam(e.getKey())) client.queryParam(e.getKey(), e.getValue());
        }
        if (auth != null && !client.hasAuth()) client.auth(auth);
        if (contentType != null && !client.hasHeader("Content-Type")) client.contentType(contentType);
    }

    public static final class Builder {
        private String baseUrl;
        private final Map<String, String> headers = new LinkedHashMap<>();
        private final Map<String, String> queryParams = new LinkedHashMap<>();
        private ApiAuth auth;
        private String contentType;

        public Builder baseUrl(String v) { this.baseUrl = v; return this; }
        public Builder header(String k, String v) { headers.put(k, v); return this; }
        public Builder headers(Map<String, String> m) { headers.putAll(m); return this; }
        public Builder queryParam(String k, Object v) { queryParams.put(k, String.valueOf(v)); return this; }
        public Builder queryParams(Map<String, ?> m) { m.forEach((k, v) -> queryParams.put(k, String.valueOf(v))); return this; }
        public Builder auth(ApiAuth v) { this.auth = v; return this; }
        public Builder contentType(String v) { this.contentType = v; return this; }
        public Builder bearerToken(String token) { this.auth = ApiAuth.bearerToken(token); return this; }
        public ApiRequestSpec build() { return new ApiRequestSpec(this); }
    }
}
