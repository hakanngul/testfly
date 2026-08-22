package io.testfly.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.testfly.config.TestFlyConfig;
import io.testfly.internal.TestFlyContext;
import io.testfly.steps.StepLogger;
import io.testfly.steps.StepStatus;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Fluent HTTP client for API testing — zero boilerplate, same philosophy as BasePage.
 *
 * <pre>
 * // Pure API call
 * ApiResponse res = apiClient().post("/api/login")
 *         .body(Map.of("username", "admin", "password", "pass"))
 *         .send();
 * res.assertStatus(200);
 * String token = res.json("$.token");
 *
 * // Different base URL
 * ApiResponse health = ApiClient.to("https://other-service.com")
 *         .get("/health")
 *         .send();
 * </pre>
 */
public class ApiClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient   HTTP   = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** Thread-local global auth — applied to every request on this thread unless overridden. */
    private static final ThreadLocal<ApiAuth> GLOBAL_AUTH = new ThreadLocal<>();

    /** Thread-local cookie jar — shared across requests on the same thread when cookies are enabled. */
    private static final ThreadLocal<Map<String, String>> COOKIE_JAR =
            ThreadLocal.withInitial(HashMap::new);

    /** Global request interceptors — applied to every request. */
    private static final List<RequestInterceptor> REQUEST_INTERCEPTORS = new CopyOnWriteArrayList<>();

    /** Global response interceptors — applied to every response. */
    private static final List<ResponseInterceptor> RESPONSE_INTERCEPTORS = new CopyOnWriteArrayList<>();

    /** Set once (e.g. in {@code @BeforeSuite}) — all requests on this thread use it automatically. */
    public static void setGlobalAuth(ApiAuth auth)  { GLOBAL_AUTH.set(auth); }

    /** Remove global auth for this thread. Called automatically by the framework after each test. */
    public static void clearGlobalAuth()            { GLOBAL_AUTH.remove(); }

    /** Clear all cookies for this thread. */
    public static void clearCookies()               { COOKIE_JAR.remove(); }

    /** Register a request interceptor — applied to every request before sending. */
    public static void addRequestInterceptor(RequestInterceptor interceptor) {
        REQUEST_INTERCEPTORS.add(interceptor);
    }

    /** Register a response interceptor — applied to every response after receiving. */
    public static void addResponseInterceptor(ResponseInterceptor interceptor) {
        RESPONSE_INTERCEPTORS.add(interceptor);
    }

    /** Remove all registered interceptors. */
    public static void clearInterceptors() {
        REQUEST_INTERCEPTORS.clear();
        RESPONSE_INTERCEPTORS.clear();
    }

    private String              baseUrl;
    private String              method;
    private String              path;
    private final Map<String, String> headers = new LinkedHashMap<>();
    private final Map<String, String> queryParams = new LinkedHashMap<>();
    private Object              body;
    private ApiAuth             auth;
    private Integer             timeoutOverride;
    private boolean             cookiesEnabled;

    private ApiClient() {}

    // ── Factory methods ───────────────────────────────────────────────────────

    /** Returns a blank ApiClient — use fluent methods to set method and path. */
    public static ApiClient create() { return new ApiClient(); }

    public static ApiClient get(String path)    { return method("GET",    path); }
    public static ApiClient post(String path)   { return method("POST",   path); }
    public static ApiClient put(String path)    { return method("PUT",    path); }
    public static ApiClient patch(String path)  { return method("PATCH",  path); }
    public static ApiClient delete(String path) { return method("DELETE", path); }

    /** Override base URL for this request only. */
    public static ApiClient to(String baseUrl) {
        ApiClient c = new ApiClient();
        c.baseUrl = baseUrl;
        return c;
    }

    public ApiClient get()    { this.method = "GET";    return this; }
    public ApiClient post()   { this.method = "POST";   return this; }
    public ApiClient put()    { this.method = "PUT";    return this; }
    public ApiClient patch()  { this.method = "PATCH";  return this; }
    public ApiClient delete() { this.method = "DELETE"; return this; }
    public ApiClient path(String path) { this.path = path; return this; }

    // ── Builder methods ───────────────────────────────────────────────────────

    public ApiClient header(String name, String value) {
        headers.put(name, value);
        return this;
    }

    public ApiClient contentType(String contentType) {
        return header("Content-Type", contentType);
    }

    public ApiClient body(Object payload) {
        this.body = payload;
        return this;
    }

    public ApiClient auth(ApiAuth auth) {
        this.auth = auth;
        return this;
    }

    /** Override the request timeout for this request only. */
    public ApiClient timeout(int seconds) {
        this.timeoutOverride = seconds;
        return this;
    }

    /** Add a query parameter (URL-encoded automatically). */
    public ApiClient queryParam(String name, Object value) {
        queryParams.put(name, String.valueOf(value));
        return this;
    }

    /** Add multiple query parameters at once. */
    public ApiClient queryParams(Map<String, ?> params) {
        params.forEach((k, v) -> queryParams.put(k, String.valueOf(v)));
        return this;
    }

    /** Enable cookie jar for this request — captures Set-Cookie and sends them on subsequent requests. */
    public ApiClient withCookies() {
        this.cookiesEnabled = true;
        return this;
    }

    // ── Execute ───────────────────────────────────────────────────────────────

    public ApiResponse send() {
        String url = buildUrl();
        int timeout = resolveTimeout();

        boolean retryEnabled = false;
        int maxAttempts = 1;
        long backoffMs = 500;
        List<Integer> retryOnStatus = List.of();
        boolean retryOnException = true;

        try {
            TestFlyConfig.Api.RetryConfig retry = TestFlyContext.getConfig().getApi().getRetry();
            if (retry != null && retry.isEnabled()) {
                retryEnabled = true;
                maxAttempts = retry.getMaxAttempts();
                backoffMs = retry.getBackoffMs();
                retryOnStatus = retry.getRetryOnStatus();
                retryOnException = retry.isRetryOnException();
            }
        } catch (Exception ignored) {
            // Config not available — no retry
        }

        if (!retryEnabled) {
            return executeRequest(url, timeout);
        }

        RuntimeException lastException = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                ApiResponse response = executeRequest(url, timeout);

                if (attempt < maxAttempts && retryOnStatus.contains(response.status())) {
                    StepLogger.step("[API] Retry " + attempt + "/" + maxAttempts
                            + " — status " + response.status(), StepStatus.WARN);
                    sleep(backoffMs * attempt);
                    continue;
                }
                return response;
            } catch (RuntimeException e) {
                lastException = e;
                if (attempt < maxAttempts && retryOnException) {
                    StepLogger.step("[API] Retry " + attempt + "/" + maxAttempts
                            + " — " + e.getMessage(), StepStatus.WARN);
                    sleep(backoffMs * attempt);
                }
            }
        }
        throw lastException;
    }

    // ── Internal: execute single request ──────────────────────────────────────

    private ApiResponse executeRequest(String url, int timeout) {
        try {
            String bodyStr = serializeBody();

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(timeout));

            // Default content type for body requests
            if (bodyStr != null && !headers.containsKey("Content-Type")) {
                builder.header("Content-Type", "application/json");
            }
            headers.forEach(builder::header);
            ApiAuth effectiveAuth = this.auth != null ? this.auth : GLOBAL_AUTH.get();
            if (effectiveAuth != null) effectiveAuth.apply(builder);

            // Apply cookies
            if (cookiesEnabled) applyCookies(builder);

            // Apply request interceptors
            for (RequestInterceptor interceptor : REQUEST_INTERCEPTORS) {
                interceptor.intercept(builder);
            }

            HttpRequest.BodyPublisher publisher = bodyStr != null
                    ? HttpRequest.BodyPublishers.ofString(bodyStr)
                    : HttpRequest.BodyPublishers.noBody();

            builder.method(method, publisher);

            long start    = System.currentTimeMillis();
            HttpResponse<String> raw = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            long duration = System.currentTimeMillis() - start;

            // Capture cookies
            if (cookiesEnabled) captureCookies(raw);

            ApiResponse response = new ApiResponse(raw, duration);

            // Apply response interceptors
            for (ResponseInterceptor interceptor : RESPONSE_INTERCEPTORS) {
                interceptor.intercept(response);
            }

            logStep(response);
            return response;

        } catch (Exception e) {
            StepLogger.step("[API] " + method + " " + url + " → ERROR: " + e.getMessage(), StepStatus.FAIL);
            throw new RuntimeException("[ApiClient] Request failed: " + method + " " + url, e);
        }
    }

    // ── Internal: URL building ────────────────────────────────────────────────

    private String buildUrl() {
        String base = isAbsolute(path) ? path
                : resolveBaseUrl() + (path.startsWith("/") ? path : "/" + path);

        if (!queryParams.isEmpty()) {
            String query = queryParams.entrySet().stream()
                    .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                            + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                    .collect(Collectors.joining("&"));
            base += (base.contains("?") ? "&" : "?") + query;
        }
        return base;
    }

    private String resolveBaseUrl() {
        if (baseUrl != null) return baseUrl;
        try {
            TestFlyConfig config = TestFlyContext.getConfig();
            TestFlyConfig.Api api = config.getApi();
            if (api != null && api.getBaseUrl() != null) return api.getBaseUrl();
            return config.getExecution().getBaseUrl();
        } catch (Exception e) {
            throw new IllegalStateException("[ApiClient] No baseUrl configured. Set execution.baseUrl or api.baseUrl in testfly.yml");
        }
    }

    private int resolveTimeout() {
        if (timeoutOverride != null) return timeoutOverride;
        try {
            TestFlyConfig.Api api = TestFlyContext.getConfig().getApi();
            return api != null ? api.getTimeoutSeconds() : 30;
        } catch (Exception e) {
            return 30;
        }
    }

    private String serializeBody() throws Exception {
        if (body == null) return null;
        if (body instanceof String) return (String) body;
        return MAPPER.writeValueAsString(body);
    }

    // ── Cookie management ─────────────────────────────────────────────────────

    private void applyCookies(HttpRequest.Builder builder) {
        Map<String, String> jar = COOKIE_JAR.get();
        if (!jar.isEmpty()) {
            String cookieHeader = jar.entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining("; "));
            builder.header("Cookie", cookieHeader);
        }
    }

    private void captureCookies(HttpResponse<String> response) {
        response.headers().allValues("set-cookie").forEach(cookie -> {
            int eqIdx = cookie.indexOf('=');
            if (eqIdx > 0) {
                String name = cookie.substring(0, eqIdx);
                String value = cookie.substring(eqIdx + 1);
                int semiIdx = value.indexOf(';');
                if (semiIdx > 0) value = value.substring(0, semiIdx);
                COOKIE_JAR.get().put(name, value);
            }
        });
    }

    // ── Logging ───────────────────────────────────────────────────────────────

    private void logStep(ApiResponse response) {
        boolean logBody = false;
        int truncationLimit = 300;
        try {
            TestFlyConfig.Api api = TestFlyContext.getConfig().getApi();
            logBody = api != null && api.isLogBody();
            if (api != null) truncationLimit = api.getTruncationLimit();
        } catch (Exception ignored) {}

        StepStatus status = response.status() >= 400 ? StepStatus.FAIL : StepStatus.PASS;
        String log = "[API] " + method + " " + path + " → " + response.status() + " (" + response.durationMs() + "ms)";
        if (logBody && response.body() != null && !response.body().isBlank()) {
            log += "\n  Body: " + truncate(response.body(), truncationLimit);
        }
        StepLogger.step(log, status);
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private static ApiClient method(String method, String path) {
        ApiClient c = new ApiClient();
        c.method = method;
        c.path   = path;
        return c;
    }

    private boolean isAbsolute(String path) {
        return path != null && (path.startsWith("http://") || path.startsWith("https://"));
    }

    private String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) + "..." : s;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ── Interceptor interfaces ────────────────────────────────────────────────

    /** Intercepts HTTP requests before they are sent. Use to add headers, log, etc. */
    @FunctionalInterface
    public interface RequestInterceptor {
        void intercept(HttpRequest.Builder builder);
    }

    /** Intercepts HTTP responses after they are received. Use to log, collect metrics, etc. */
    @FunctionalInterface
    public interface ResponseInterceptor {
        void intercept(ApiResponse response);
    }
}
