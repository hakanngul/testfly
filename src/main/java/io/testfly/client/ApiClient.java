package io.testfly.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.testfly.api.TestFlyApi;
import io.testfly.config.TestFlyConfig;
import io.testfly.internal.TestFlyContext;
import io.testfly.steps.StepLogger;
import io.testfly.steps.StepStatus;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * // Path params (URL-encoded, fail-fast on unresolved placeholders)
 * ApiResponse user = apiClient().get("/api/users/{id}")
 *         .pathParam("id", 42)
 *         .send();
 *
 * // Form + multipart
 * apiClient().post("/login").formParam("username", "admin").formParam("password", "secret").send();
 * apiClient().post("/upload").multipart("file", Path.of("avatar.png")).send();
 *
 * // Different base URL / service
 * ApiResponse health = ApiClient.to("https://other-service.com").get("/health").send();
 * ApiResponse pay = ApiClient.toService("payment").get("/charges").send();
 * </pre>
 */
@TestFlyApi(since = "1.0.0")
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

    /** Global request spec — applied to every request on any thread. */
    private static volatile ApiRequestSpec GLOBAL_SPEC;

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

    /** Set a global request spec applied to every request. */
    public static void setGlobalSpec(ApiRequestSpec spec) { GLOBAL_SPEC = spec; }

    /** Remove global request spec. */
    public static void clearGlobalSpec() { GLOBAL_SPEC = null; }

    private String              baseUrl;
    private String              method;
    private String              path;
    private final Map<String, String> headers = new LinkedHashMap<>();
    private final Map<String, String> queryParams = new LinkedHashMap<>();
    private final Map<String, String> pathParams = new LinkedHashMap<>();
    private final Map<String, String> formParams = new LinkedHashMap<>();
    private final Map<String, Path>   fileParams = new LinkedHashMap<>();
    private final Map<String, String> fieldParams = new LinkedHashMap<>();
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

    /** Resolve base URL from multi-service map: api.baseUrls[service] or api.baseUrl. */
    public static ApiClient toService(String service) {
        try {
            TestFlyConfig.Api api = TestFlyContext.getConfig().getApi();
            String url = null;
            if (api != null) {
                url = api.baseUrlFor(service);
            }
            if (url == null) {
                throw new IllegalStateException("[ApiClient] No baseUrl for service '" + service + "'. Set api.baseUrls." + service + " or api.baseUrl in testfly.yml");
            }
            return to(url);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("[ApiClient] No baseUrl for service '" + service + "'", e);
        }
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

    public ApiClient headers(Map<String, String> map) {
        headers.putAll(map);
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

    /** Set a path template placeholder — {@code {name}} is replaced with URL-encoded value. */
    public ApiClient pathParam(String name, Object value) {
        pathParams.put(name, URLEncoder.encode(String.valueOf(value), StandardCharsets.UTF_8));
        return this;
    }

    /** Set multiple path params at once. */
    public ApiClient pathParams(Map<String, ?> params) {
        params.forEach((k, v) -> pathParam(k, v));
        return this;
    }

    /** Add a form field (application/x-www-form-urlencoded). */
    public ApiClient formParam(String name, Object value) {
        formParams.put(name, String.valueOf(value));
        return this;
    }

    /** Add multiple form fields at once. */
    public ApiClient formParams(Map<String, ?> params) {
        params.forEach((k, v) -> formParams.put(k, String.valueOf(v)));
        return this;
    }

    /** Add a multipart file part. */
    public ApiClient multipart(String name, Path file) {
        fileParams.put(name, file);
        return this;
    }

    /** Add a multipart text field. */
    public ApiClient field(String name, String value) {
        fieldParams.put(name, value);
        return this;
    }

    /** Apply a reusable request spec to this client. */
    public ApiClient spec(ApiRequestSpec spec) {
        spec.applyTo(this);
        return this;
    }

    /** Package-private — used by ApiRequestSpec to set baseUrl. */
    ApiClient baseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
        return this;
    }

    boolean hasBaseUrl() { return baseUrl != null; }
    boolean hasHeader(String name) { return headers.containsKey(name); }
    boolean hasQueryParam(String name) { return queryParams.containsKey(name); }
    boolean hasAuth() { return auth != null; }

    /** Enable cookie jar for this request — captures Set-Cookie and sends them on subsequent requests. */
    public ApiClient withCookies() {
        this.cookiesEnabled = true;
        return this;
    }

    // ── Execute ───────────────────────────────────────────────────────────────

    public ApiResponse send() {
        // Apply global spec first (if any) — per-request values win
        if (GLOBAL_SPEC != null) {
            GLOBAL_SPEC.applyToIfAbsent(this);
        }
        // Allow auth to modify client (e.g. apiKeyQuery adds query param) before URL is built
        if (auth != null) auth.applyToClient(this);
        else if (GLOBAL_AUTH.get() != null) GLOBAL_AUTH.get().applyToClient(this);

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

        ApiException lastException = null;
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
            } catch (ApiException e) {
                lastException = e;
                if (attempt < maxAttempts && retryOnException) {
                    StepLogger.step("[API] Retry " + attempt + "/" + maxAttempts
                            + " — " + e.getMessage(), StepStatus.WARN);
                    sleep(backoffMs * attempt);
                } else if (attempt >= maxAttempts) {
                    throw e;
                }
            } catch (RuntimeException e) {
                // Wrap non-ApiException runtime as ApiException for retry classification
                ApiException wrapped = new ApiException(method, url, e);
                lastException = wrapped;
                if (attempt < maxAttempts && retryOnException) {
                    StepLogger.step("[API] Retry " + attempt + "/" + maxAttempts
                            + " — " + e.getMessage(), StepStatus.WARN);
                    sleep(backoffMs * attempt);
                } else if (attempt >= maxAttempts) {
                    throw wrapped;
                }
            }
        }
        throw lastException;
    }

    // ── Internal: execute single request ──────────────────────────────────────

    private ApiResponse executeRequest(String url, int timeout) {
        try {
            byte[] bodyBytes = null;
            String bodyStr = null;
            String contentTypeOverride = null;

            if (!fileParams.isEmpty() || !fieldParams.isEmpty()) {
                MultipartBody mp = buildMultipartBody();
                bodyBytes = mp.bytes;
                contentTypeOverride = mp.contentType;
            } else if (!formParams.isEmpty()) {
                bodyStr = formParams.entrySet().stream()
                        .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                                + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                        .collect(Collectors.joining("&"));
                contentTypeOverride = "application/x-www-form-urlencoded";
            } else {
                bodyStr = serializeBody();
            }

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(timeout));

            // Content-Type handling
            if (contentTypeOverride != null && !headers.containsKey("Content-Type")) {
                builder.header("Content-Type", contentTypeOverride);
            } else if (bodyStr != null && !headers.containsKey("Content-Type")) {
                builder.header("Content-Type", "application/json");
            } else if (bodyBytes != null && contentTypeOverride != null && !headers.containsKey("Content-Type")) {
                builder.header("Content-Type", contentTypeOverride);
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

            HttpRequest.BodyPublisher publisher;
            if (bodyBytes != null) {
                publisher = HttpRequest.BodyPublishers.ofByteArray(bodyBytes);
            } else if (bodyStr != null) {
                publisher = HttpRequest.BodyPublishers.ofString(bodyStr);
            } else {
                publisher = HttpRequest.BodyPublishers.noBody();
            }

            builder.method(method, publisher);

            long start    = System.currentTimeMillis();
            HttpResponse<String> raw = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            long duration = System.currentTimeMillis() - start;

            // Capture cookies
            if (cookiesEnabled) captureCookies(raw);

            ApiResponse response = new ApiResponse(raw, duration, method, url);

            // Apply response interceptors
            for (ResponseInterceptor interceptor : RESPONSE_INTERCEPTORS) {
                interceptor.intercept(response);
            }

            logStep(response);
            return response;

        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            StepLogger.step("[API] " + method + " " + url + " → ERROR: " + e.getMessage(), StepStatus.FAIL);
            throw new ApiException(method, url, e);
        }
    }

    // ── Internal: URL building ────────────────────────────────────────────────

    private String buildUrl() {
        String resolvedPath = path;
        if (resolvedPath != null && !pathParams.isEmpty()) {
            for (Map.Entry<String, String> e : pathParams.entrySet()) {
                resolvedPath = resolvedPath.replace("{" + e.getKey() + "}", e.getValue());
            }
        }
        if (resolvedPath != null && resolvedPath.contains("{") && resolvedPath.contains("}")) {
            throw new IllegalStateException("[ApiClient] Unresolved path params in: " + resolvedPath + " — missing pathParam() for placeholder");
        }

        String base = isAbsolute(resolvedPath) ? resolvedPath
                : resolveBaseUrl() + (resolvedPath != null && resolvedPath.startsWith("/") ? resolvedPath : "/" + resolvedPath);

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

    // ── Multipart ─────────────────────────────────────────────────────────────

    private static final class MultipartBody {
        final byte[] bytes;
        final String contentType;
        MultipartBody(byte[] bytes, String contentType) { this.bytes = bytes; this.contentType = contentType; }
    }

    private MultipartBody buildMultipartBody() throws IOException {
        String boundary = "TestFlyBoundary" + System.currentTimeMillis();
        String contentType = "multipart/form-data; boundary=" + boundary;
        List<byte[]> parts = new ArrayList<>();
        byte[] crlf = "\r\n".getBytes(StandardCharsets.UTF_8);
        byte[] dashBoundary = ("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8);

        for (Map.Entry<String, String> e : fieldParams.entrySet()) {
            parts.add(dashBoundary);
            parts.add(("Content-Disposition: form-data; name=\"" + e.getKey() + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            parts.add(e.getValue().getBytes(StandardCharsets.UTF_8));
            parts.add(crlf);
        }
        for (Map.Entry<String, Path> e : fileParams.entrySet()) {
            Path file = e.getValue();
            String fileName = file.getFileName().toString();
            String mime = Files.probeContentType(file);
            if (mime == null) mime = "application/octet-stream";
            parts.add(dashBoundary);
            parts.add(("Content-Disposition: form-data; name=\"" + e.getKey() + "\"; filename=\"" + fileName + "\"\r\n").getBytes(StandardCharsets.UTF_8));
            parts.add(("Content-Type: " + mime + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            parts.add(Files.readAllBytes(file));
            parts.add(crlf);
        }
        parts.add(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        int total = parts.stream().mapToInt(b -> b.length).sum();
        byte[] all = new byte[total];
        int pos = 0;
        for (byte[] p : parts) { System.arraycopy(p, 0, all, pos, p.length); pos += p.length; }
        return new MultipartBody(all, contentType);
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
        boolean prettyLog = false;
        boolean logCurl = false;
        int truncationLimit = 300;
        Set<String> maskedHeaders = Set.of("Authorization", "Cookie", "X-Api-Key");
        try {
            TestFlyConfig.Api api = TestFlyContext.getConfig().getApi();
            if (api != null) {
                logBody = api.isLogBody();
                prettyLog = api.isPrettyLog();
                logCurl = api.isLogCurl();
                truncationLimit = api.getTruncationLimit();
                if (api.getMaskedHeaders() != null && !api.getMaskedHeaders().isEmpty()) {
                    maskedHeaders = Set.copyOf(api.getMaskedHeaders());
                }
            }
        } catch (Exception ignored) {}

        StepStatus status = response.status() >= 400 ? StepStatus.FAIL : StepStatus.PASS;
        StringBuilder log = new StringBuilder("[API] " + method + " " + path + " → " + response.status() + " (" + response.durationMs() + "ms)");

        if (!headers.isEmpty()) {
            log.append("\n  Headers: ");
            for (Map.Entry<String, String> e : headers.entrySet()) {
                String val = maskedHeaders.contains(e.getKey()) ? "***" : e.getValue();
                log.append(e.getKey()).append("=").append(val).append(" ");
            }
        }

        if (logBody && response.body() != null && !response.body().isBlank()) {
            String body = response.body();
            if (prettyLog) {
                try { body = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(MAPPER.readTree(body)); }
                catch (Exception ignored) {}
            }
            log.append("\n  Body: ").append(truncate(body, truncationLimit));
        }

        if (logCurl) {
            log.append("\n  curl: ").append(toCurl());
        }

        StepLogger.step(log.toString(), status);
    }

    private String toCurl() {
        StringBuilder curl = new StringBuilder("curl -X ").append(method).append(" '").append(buildUrl()).append("'");
        headers.forEach((k, v) -> curl.append(" -H '").append(k).append(": ").append(v).append("'"));
        if (body != null) {
            try { curl.append(" -d '").append(serializeBody()).append("'"); }
            catch (Exception ignored) {}
        } else if (!formParams.isEmpty()) {
            String form = formParams.entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining("&"));
            curl.append(" -d '").append(form).append("'");
        }
        return curl.toString();
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
