package io.testfly.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.testfly.api.TestFlyApi;
import io.testfly.config.TestFlyConfig;
import io.testfly.internal.TestFlyContext;
import io.testfly.steps.StepLogger;
import io.testfly.steps.StepStatus;

import java.io.IOException;
import io.testfly.internal.api.ApiExecution;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.CancellationException;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Fluent HTTP client for API testing — zero boilerplate, same philosophy as
 * BasePage.
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
 * ApiResponse health = ApiClient.to("https://other-service.com").path("/health").get().send();
 * ApiResponse pay = ApiClient.toService("payment").path("/charges").get().send();
 * </pre>
 */
@TestFlyApi(since = "1.0.0")
public class ApiClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();


    /**
     * Thread-local global auth — applied to every request on this thread unless
     * overridden.
     */
    private static final ThreadLocal<ApiAuth> GLOBAL_AUTH = new ThreadLocal<>();

    /**
     * Thread-local cookie jar — shared across requests on the same thread when
     * cookies are enabled.
     */
    private static final ThreadLocal<CookieJar> COOKIE_JAR = ThreadLocal.withInitial(CookieJar::new);

    /** JVM-global request interceptors — applied to every request on every thread; never cleared automatically. */
    private static final List<RequestInterceptor> REQUEST_INTERCEPTORS = new CopyOnWriteArrayList<>();

    /** JVM-global response interceptors — applied to every response on every thread; never cleared automatically. */
    private static final List<ResponseInterceptor> RESPONSE_INTERCEPTORS = new CopyOnWriteArrayList<>();

    /** JVM-global request spec — applied to every request on any thread; never cleared automatically. */
    private static volatile ApiRequestSpec GLOBAL_SPEC;

    /** Test-scoped request spec — current test thread only; cleared after each test. */
    private static final ThreadLocal<ApiRequestSpec> TEST_SPEC = new ThreadLocal<>();

    /**
     * Sets the default auth for the <b>current test thread</b>. The framework clears it after
     * every test, so set it in {@code @BeforeMethod} (or inside the test), not in
     * {@code @BeforeSuite} / {@code @BeforeClass}: those run once and possibly on another thread.
     * For suite-wide defaults use {@link #setGlobalSpec(ApiRequestSpec)} or {@code @UseAuth}.
     */
    public static void setGlobalAuth(ApiAuth auth) {
        GLOBAL_AUTH.set(auth);
    }

    /**
     * Remove global auth for this thread. Called automatically by the framework
     * after each test.
     */
    public static void clearGlobalAuth() {
        GLOBAL_AUTH.remove();
    }

    /** Clear all cookies for this thread. */
    public static void clearCookies() {
        COOKIE_JAR.remove();
    }

    /** Per-test Digest challenges keyed by origin; shared with the test's async calls. */
    private static final ThreadLocal<Map<String, DigestAuth.Challenge>> AUTH_CHALLENGES =
            ThreadLocal.withInitial(ConcurrentHashMap::new);

    static Map<String, DigestAuth.Challenge> challengeCache() {
        return AUTH_CHALLENGES.get();
    }

    /** Forgets cached authentication challenges (Digest nonces) of this thread. Called after each test. */
    @TestFlyApi(since = "1.1.0")
    public static void clearAuthChallenges() {
        AUTH_CHALLENGES.remove();
    }

    /**
     * Registers a JVM-global request hook applied to every request on every thread. It is not
     * cleared between tests; register it once and remove it with {@link #clearInterceptors()}.
     * For test-scoped middleware use {@link #addInterceptor(ApiInterceptor)}.
     */
    public static void addRequestInterceptor(RequestInterceptor interceptor) {
        REQUEST_INTERCEPTORS.add(interceptor);
    }

    /**
     * Registers a JVM-global response hook applied to every response on every thread. It is not
     * cleared between tests; register it once and remove it with {@link #clearInterceptors()}.
     */
    public static void addResponseInterceptor(ResponseInterceptor interceptor) {
        RESPONSE_INTERCEPTORS.add(interceptor);
    }

    /** Remove all registered interceptors. */
    public static void clearInterceptors() {
        REQUEST_INTERCEPTORS.clear();
        RESPONSE_INTERCEPTORS.clear();
        clearChainInterceptors();
    }

    /**
     * Sets a JVM-global request spec applied to every request on every thread. Values already
     * set on the request win. It is not cleared between tests; call {@link #clearGlobalSpec()}
     * to remove it. For a spec that applies to the current test only use
     * {@link #setTestSpec(ApiRequestSpec)}.
     */
    public static void setGlobalSpec(ApiRequestSpec spec) {
        GLOBAL_SPEC = spec;
    }

    /** Remove global request spec. */
    public static void clearGlobalSpec() {
        GLOBAL_SPEC = null;
    }

    /**
     * Sets a request spec for the current test thread only. It is applied before the global
     * spec (values already set on the request still win) and is cleared after each test.
     */
    @TestFlyApi(since = "1.1.0")
    public static void setTestSpec(ApiRequestSpec spec) {
        if (spec == null) TEST_SPEC.remove();
        else TEST_SPEC.set(spec);
    }

    /** Removes the test-scoped request spec of the current thread. */
    @TestFlyApi(since = "1.1.0")
    public static void clearTestSpec() {
        TEST_SPEC.remove();
    }

    private String baseUrl;
    private String method;
    private String path;
    private final Map<String, String> headers = new LinkedHashMap<>();
    private final Map<String, String> queryParams = new LinkedHashMap<>();
    /** Additional values for repeated query keys ({@code ?id=1&id=2}); the first value lives in {@link #queryParams}. */
    private final Map<String, List<String>> extraQueryValues = new LinkedHashMap<>();
    private final Map<String, String> pathParams = new LinkedHashMap<>();
    private final Map<String, String> formParams = new LinkedHashMap<>();
    private final Map<String, Path> fileParams = new LinkedHashMap<>();
    private final Map<String, String> fieldParams = new LinkedHashMap<>();
    private Object body;
    private static final ThreadLocal<List<ApiInterceptor>> CHAIN_INTERCEPTORS = ThreadLocal.withInitial(ArrayList::new);
    private final List<ApiInterceptor> interceptors = new ArrayList<>();

    /** Register middleware for the current test thread. */
    @TestFlyApi(since = "1.1.0")
    public static void addInterceptor(ApiInterceptor interceptor) {
        CHAIN_INTERCEPTORS.get().add(Objects.requireNonNull(interceptor));
    }

    @TestFlyApi(since = "1.1.0")
    public static void clearChainInterceptors() { CHAIN_INTERCEPTORS.remove(); }

    /** Register middleware for this request instance. */
    @TestFlyApi(since = "1.1.0")
    public ApiClient interceptor(ApiInterceptor interceptor) {
        interceptors.add(Objects.requireNonNull(interceptor));
        return this;
    }

    private ApiAuth auth;
    private Duration timeoutOverride;
    private io.testfly.internal.api.ApiTransport.SslSelection sslOverride;
    private io.testfly.internal.api.ApiTransport.ProxySelection proxyOverride;

    /** Sends this request through an HTTP proxy, overriding {@code api.proxy}. */
    @TestFlyApi(since = "1.1.0")
    public ApiClient proxy(String host, int port) {
        proxyOverride = io.testfly.internal.api.ApiTransport.ProxySelection.of(host, port);
        return this;
    }

    /** Connects directly for this request even when {@code api.proxy} is configured. */
    @TestFlyApi(since = "1.1.0")
    public ApiClient noProxy() {
        proxyOverride = io.testfly.internal.api.ApiTransport.ProxySelection.direct();
        return this;
    }
    private boolean sslConflict;
    private final List<ApiMockRule> mockRules = new ArrayList<>();
    private static final ThreadLocal<List<ApiMockRule>> MOCK_RULES = ThreadLocal.withInitial(ArrayList::new);

    @TestFlyApi(since = "1.1.0")
    public static void addMockRule(ApiMockRule rule) { MOCK_RULES.get().add(Objects.requireNonNull(rule)); }
    @TestFlyApi(since = "1.1.0")
    public static void clearMockRules() { MOCK_RULES.remove(); }
    @TestFlyApi(since = "1.1.0")
    public ApiClient mockRule(ApiMockRule rule) { mockRules.add(Objects.requireNonNull(rule)); return this; }
    @TestFlyApi(since = "1.1.0")
    public ApiClient trustAllCerts() {
        if (sslOverride != null && !sslOverride.trustAll()) sslConflict = true;
        sslOverride = io.testfly.internal.api.ApiTransport.SslSelection.trustAllSelection(); return this;
    }
    @TestFlyApi(since = "1.1.0")
    public ApiClient trustStore(Path path, char[] password) { return trustStore(path, password, "PKCS12"); }
    @TestFlyApi(since = "1.1.0")
    public ApiClient trustStore(Path path, char[] password, String type) {
        if (sslOverride != null && sslOverride.trustAll()) sslConflict = true;
        sslOverride = io.testfly.internal.api.ApiTransport.SslSelection.store(path, password, type); return this;
    }
    @TestFlyApi(since = "1.1.0")
    public ApiClient requestTimeout(Duration value) {
        if (value == null || value.isZero() || value.isNegative()) throw new IllegalArgumentException("Positive request timeout required");
        timeoutOverride = value; return this;
    }
    private boolean cookiesEnabled;
    private Integer retryAttemptsOverride;
    private Long retryBackoffOverride;
    private List<Integer> retryStatusesOverride;
    private Boolean retryExceptionsOverride;

    /**
     * Retries this request up to {@code maxAttempts} times in total, using the configured
     * {@code api.retry} statuses/backoff (or their defaults: 502/503/504, 500 ms, transport errors).
     * Enables retry for this request even when {@code api.retry.enabled} is false.
     */
    @TestFlyApi(since = "1.1.0")
    public ApiClient retry(int maxAttempts) {
        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be >= 1");
        retryAttemptsOverride = maxAttempts;
        return this;
    }

    /** Like {@link #retry(int)} with a per-request linear backoff base ({@code backoff * attempt}). */
    @TestFlyApi(since = "1.1.0")
    public ApiClient retry(int maxAttempts, Duration backoff) {
        if (backoff == null || backoff.isNegative()) throw new IllegalArgumentException("backoff must be >= 0");
        retry(maxAttempts);
        retryBackoffOverride = backoff.toMillis();
        return this;
    }

    /** Retries this request on the given statuses (replaces {@code api.retry.retryOnStatus}); enables retry. */
    @TestFlyApi(since = "1.1.0")
    public ApiClient retryOn(int... statuses) {
        List<Integer> list = new ArrayList<>();
        for (int s : statuses) list.add(s);
        retryStatusesOverride = List.copyOf(list);
        return this;
    }

    /** Whether transport failures (connection reset, timeout) are retried for this request; enables retry. */
    @TestFlyApi(since = "1.1.0")
    public ApiClient retryOnException(boolean retry) {
        retryExceptionsOverride = retry;
        return this;
    }

    /** Disables retry for this request even when {@code api.retry.enabled} is true. */
    @TestFlyApi(since = "1.1.0")
    public ApiClient noRetry() {
        retryAttemptsOverride = 1;
        retryBackoffOverride = null;
        retryStatusesOverride = null;
        retryExceptionsOverride = null;
        return this;
    }

    private ApiClient() {
    }

    // ── Factory methods ───────────────────────────────────────────────────────

    /** Returns a blank ApiClient — use fluent methods to set method and path. */
    public static ApiClient create() {
        return new ApiClient();
    }

    public static ApiClient get(String path) {
        return method("GET", path);
    }

    public static ApiClient post(String path) {
        return method("POST", path);
    }

    public static ApiClient put(String path) {
        return method("PUT", path);
    }

    public static ApiClient patch(String path) {
        return method("PATCH", path);
    }

    public static ApiClient delete(String path) {
        return method("DELETE", path);
    }

    @TestFlyApi(since = "1.1.0")
    public static ApiClient head(String path) {
        return method("HEAD", path);
    }

    @TestFlyApi(since = "1.1.0")
    public static ApiClient options(String path) {
        return method("OPTIONS", path);
    }

    /**
     * Override base URL for this request only. Continue with {@code path(..)} and an instance
     * verb: {@code ApiClient.to(url).path("/health").get().send()}. Do not chain the static
     * {@code get(String)}/{@code post(String)} factories here: they create a new client and drop
     * this base URL.
     */
    public static ApiClient to(String baseUrl) {
        ApiClient c = new ApiClient();
        c.baseUrl = baseUrl;
        return c;
    }

    /**
     * Resolve base URL from multi-service map: api.baseUrls[service] or
     * api.baseUrl.
     */
    public static ApiClient toService(String service) {
        try {
            TestFlyConfig.Api api = TestFlyContext.getConfig().getApi();
            String url = null;
            if (api != null) {
                url = api.baseUrlFor(service);
            }
            if (url == null) {
                throw new IllegalStateException("[ApiClient] No baseUrl for service '" + service
                        + "'. Set api.baseUrls." + service + " or api.baseUrl in testfly.yml");
            }
            return to(url);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("[ApiClient] No baseUrl for service '" + service + "'", e);
        }
    }

    public ApiClient get() {
        this.method = "GET";
        return this;
    }

    public ApiClient post() {
        this.method = "POST";
        return this;
    }

    public ApiClient put() {
        this.method = "PUT";
        return this;
    }

    public ApiClient patch() {
        this.method = "PATCH";
        return this;
    }

    public ApiClient delete() {
        this.method = "DELETE";
        return this;
    }

    @TestFlyApi(since = "1.1.0")
    public ApiClient head() {
        this.method = "HEAD";
        return this;
    }

    @TestFlyApi(since = "1.1.0")
    public ApiClient options() {
        this.method = "OPTIONS";
        return this;
    }

    public ApiClient path(String path) {
        this.path = path;
        return this;
    }

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

    /**
     * Request body: a {@code String} is sent as-is, a {@code byte[]} is sent raw
     * ({@code application/octet-stream} unless a Content-Type is set), anything else is
     * serialised to JSON.
     */
    public ApiClient body(Object payload) {
        this.body = payload;
        return this;
    }

    /** Raw binary request body ({@code application/octet-stream} unless a Content-Type is set). */
    @TestFlyApi(since = "1.1.0")
    public ApiClient body(byte[] payload) {
        this.body = payload == null ? null : payload.clone();
        return this;
    }

    public ApiClient auth(ApiAuth auth) {
        this.auth = auth;
        return this;
    }

    /** Override the request timeout for this request only. */
    public ApiClient timeout(int seconds) {
        return requestTimeout(Duration.ofSeconds(seconds));
    }

    /**
     * Sets a query parameter (URL-encoded automatically), replacing earlier values of the same
     * name. Use {@link #addQueryParam(String, Object)} for repeated keys.
     */
    public ApiClient queryParam(String name, Object value) {
        queryParams.put(name, String.valueOf(value));
        extraQueryValues.remove(name);
        return this;
    }

    /** Add multiple query parameters at once (each replaces earlier values of the same name). */
    public ApiClient queryParams(Map<String, ?> params) {
        params.forEach(this::queryParam);
        return this;
    }

    /** Appends a query parameter value, keeping earlier values: {@code ?id=1&id=2}. */
    @TestFlyApi(since = "1.1.0")
    public ApiClient addQueryParam(String name, Object value) {
        if (!queryParams.containsKey(name)) queryParams.put(name, String.valueOf(value));
        else extraQueryValues.computeIfAbsent(name, k -> new ArrayList<>()).add(String.valueOf(value));
        return this;
    }

    /** Appends one value per element: {@code addQueryParams("id", List.of(1, 2))} gives {@code ?id=1&id=2}. */
    @TestFlyApi(since = "1.1.0")
    public ApiClient addQueryParams(String name, Iterable<?> values) {
        for (Object value : values) addQueryParam(name, value);
        return this;
    }

    /**
     * Set a path template placeholder — {@code {name}} is replaced with URL-encoded
     * value.
     */
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

    boolean hasBaseUrl() {
        return baseUrl != null;
    }

    boolean hasHeader(String name) {
        return headers.keySet().stream().anyMatch(k -> k.equalsIgnoreCase(name));
    }

    boolean hasQueryParam(String name) {
        return queryParams.containsKey(name);
    }

    boolean hasAuth() {
        return auth != null;
    }

    /**
     * Enable cookie jar for this request — captures Set-Cookie and sends them on
     * subsequent requests of the same test. Cookies are scoped by host/Domain, Path,
     * Secure and Max-Age/Expires (a cookie without Path applies to {@code /}).
     */
    public ApiClient withCookies() {
        this.cookiesEnabled = true;
        return this;
    }

    // ── Polling ───────────────────────────────────────────────────────────────

    /**
     * Repeatedly executes this API request until {@code condition} returns true or
     * {@code maxTimeout} expires.
     * Uses a default polling interval of 1 second.
     *
     * <pre>
     * ApiResponse res = apiClient().get("/orders/123")
     *         .pollUntil(r -> "COMPLETED".equals(r.json("$.status")), Duration.ofSeconds(30));
     * </pre>
     *
     * @param condition  predicate evaluated against each received response
     * @param maxTimeout maximum duration to continue polling
     * @return the successful {@link ApiResponse} meeting the condition
     */
    public ApiResponse pollUntil(java.util.function.Predicate<ApiResponse> condition, Duration maxTimeout) {
        return pollUntil(condition, maxTimeout, Duration.ofSeconds(1));
    }

    /**
     * Repeatedly executes this API request until {@code condition} returns true or
     * {@code maxTimeout} expires.
     *
     * @param condition    predicate evaluated against each received response
     * @param maxTimeout   maximum duration to continue polling
     * @param pollInterval interval between subsequent polling attempts
     * @return the successful {@link ApiResponse} meeting the condition
     */
    public ApiResponse pollUntil(java.util.function.Predicate<ApiResponse> condition, Duration maxTimeout,
            Duration pollInterval) {
        return pollUntil(condition, ApiPolling.within(maxTimeout).interval(pollInterval));
    }

    /**
     * Repeatedly executes this API request until {@code condition} returns true, using the
     * given {@link ApiPolling} options (timeout, interval, fail-fast statuses, attempt limit,
     * predicate error handling).
     *
     * @throws ApiException on timeout, attempt limit, a fail-fast status, or (when enabled)
     *                      a predicate error; the last failure is attached as the cause
     */
    @TestFlyApi(since = "1.1.0")
    public ApiResponse pollUntil(java.util.function.Predicate<ApiResponse> condition, ApiPolling polling) {
        Objects.requireNonNull(condition, "condition");
        Objects.requireNonNull(polling, "polling");
        long start = System.currentTimeMillis();
        long maxMs = polling.timeout().toMillis();
        long intervalMs = Math.max(50, polling.interval().toMillis());
        int attempt = 0;
        ApiResponse lastResponse = null;
        RuntimeException lastError = null;
        String target = method + " " + (path != null ? path : "");

        StepLogger.step("[API Polling] Started polling " + target + " (timeout: "
                + polling.timeout().getSeconds() + "s)");

        while ((System.currentTimeMillis() - start) < maxMs) {
            attempt++;
            ApiResponse current = null;
            try {
                current = send();
                lastResponse = current;
                lastError = null;
            } catch (CancellationException e) {
                throw e;
            } catch (RuntimeException e) {
                lastError = e;
                StepLogger.step("[API Polling] Attempt " + attempt + " failed with error: " + e.getMessage(),
                        StepStatus.WARN);
            }
            if (current != null) {
                if (polling.failFastStatuses().contains(current.status())) {
                    String msg = "[ApiClient] Polling aborted on attempt " + attempt + ": fail-fast status "
                            + current.status() + " for " + target;
                    StepLogger.step(msg, StepStatus.FAIL);
                    throw new ApiException(current.requestMethod(), current.requestUrl(), current.status(),
                            current.body(), msg);
                }
                try {
                    if (condition.test(current)) {
                        StepLogger.step("[API Polling] Condition satisfied on attempt " + attempt + " ("
                                + (System.currentTimeMillis() - start) + "ms)");
                        return current;
                    }
                } catch (CancellationException e) {
                    throw e;
                } catch (RuntimeException e) {
                    if (polling.rethrowsPredicateErrors()) {
                        String msg = "[ApiClient] Polling condition threw on attempt " + attempt + " for " + target
                                + ": " + e;
                        StepLogger.step(msg, StepStatus.FAIL);
                        throw new ApiException(current.requestMethod(), current.requestUrl(), current.status(),
                                current.body(), msg, e);
                    }
                    lastError = e;
                    StepLogger.step("[API Polling] Attempt " + attempt + " condition error: " + e,
                            StepStatus.WARN);
                }
            }

            if (polling.maxAttempts() > 0 && attempt >= polling.maxAttempts()) break;
            long elapsed = System.currentTimeMillis() - start;
            if (elapsed + intervalMs >= maxMs) {
                break;
            }
            ApiExecution.delay(intervalMs, ApiExecution.scope());
        }

        long totalElapsed = System.currentTimeMillis() - start;
        boolean limited = polling.maxAttempts() > 0 && attempt >= polling.maxAttempts();
        String errMsg = "[ApiClient] Polling " + (limited ? "attempt limit reached" : "timeout exceeded")
                + " (" + totalElapsed + "ms, " + attempt + " attempts) for " + target
                + (lastResponse != null
                        ? " — Last status: " + lastResponse.status() + ", body: " + truncate(lastResponse.body(), 200)
                        : "")
                + (lastError != null ? " — Last error: " + lastError : "");
        StepLogger.step(errMsg, StepStatus.FAIL);
        String url;
        try { url = buildUrl(); } catch (RuntimeException e) { url = path; }
        throw new ApiException(method, url, lastResponse != null ? lastResponse.status() : 0,
                lastResponse != null ? lastResponse.body() : null, errMsg, lastError);
    }

    // ── Execute ───────────────────────────────────────────────────────────────

    public ApiResponse send() {
        return execute(prepare());
    }

    @TestFlyApi(since = "1.2.0")
    public CompletableFuture<ApiResponse> sendAsync() { return sendAsync(null); }

    // Batch calls snapshot on the caller thread, then limit active logical calls on workers.
    CompletableFuture<ApiResponse> sendAsync(Semaphore limit) {
        final Call call;
        try { call = prepare(); }
        catch (RuntimeException error) { return CompletableFuture.failedFuture(error); }
        return ApiExecution.submit(call.scope, () -> {
            GLOBAL_AUTH.set(call.auth);
            COOKIE_JAR.set(call.cookies);
            AUTH_CHALLENGES.set(call.challenges);
            CHAIN_INTERCEPTORS.set(new ArrayList<>(call.testInterceptors));
            MOCK_RULES.set(new ArrayList<>(call.testMockRules));
            boolean acquired = false;
            try {
                if (limit != null) { limit.acquire(); acquired = true; }
                ApiExecution.check(call.scope);
                return execute(call);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new CancellationException("API batch cancelled");
            } finally {
                if (acquired) limit.release();
                GLOBAL_AUTH.remove();
                COOKIE_JAR.remove();
                AUTH_CHALLENGES.remove();
                CHAIN_INTERCEPTORS.remove();
                MOCK_RULES.remove();
            }
        });
    }

    private Call prepare() {
        if (sslConflict) throw new IllegalArgumentException("Conflicting SSL selection");
        ApiRequestSpec testSpec = TEST_SPEC.get();
        if (testSpec != null) testSpec.applyToIfAbsent(this);
        ApiRequestSpec globalSpec = GLOBAL_SPEC;
        if (globalSpec != null) globalSpec.applyToIfAbsent(this);
        ApiAuth effectiveAuth = auth != null ? auth : GLOBAL_AUTH.get();
        if (effectiveAuth != null) effectiveAuth.applyToClient(this);
        ApiRequest request;
        try { request = buildRequest(buildUrl(), resolveTimeout()); }
        catch (RuntimeException error) { throw error; }
        catch (Exception error) { throw new ApiException(method, buildUrl(), error); }
        List<ApiInterceptor> test = List.copyOf(CHAIN_INTERCEPTORS.get());
        List<ApiInterceptor> all = new ArrayList<>(test);
        all.addAll(interceptors);
        // Challenge/signing auth runs innermost so it sees (and may re-send) the exact request.
        if (effectiveAuth instanceof ApiInterceptor authInterceptor) all.add(authInterceptor);
        int attempts = 1;
        long backoff = 500;
        List<Integer> statuses = List.of();
        boolean exceptions = false;
        TestFlyConfig.Api api = null;
        if (TestFlyContext.isInitialized()) api = TestFlyContext.getConfig().getApi();
        boolean configured = api != null && api.getRetry() != null && api.getRetry().isEnabled();
        boolean overridden = retryAttemptsOverride != null || retryBackoffOverride != null
                || retryStatusesOverride != null || retryExceptionsOverride != null;
        if (configured || overridden) {
            var retry = api != null && api.getRetry() != null ? api.getRetry() : new TestFlyConfig.Api.RetryConfig();
            attempts = retryAttemptsOverride != null ? retryAttemptsOverride : retry.getMaxAttempts();
            backoff = retryBackoffOverride != null ? retryBackoffOverride : retry.getBackoffMs();
            statuses = retryStatusesOverride != null ? retryStatusesOverride : List.copyOf(retry.getRetryOnStatus());
            exceptions = retryExceptionsOverride != null ? retryExceptionsOverride : retry.isRetryOnException();
            if (attempts < 1 || backoff < 0) throw new IllegalArgumentException("Invalid API retry attempts/backoff");
        }
        List<ApiMockRule> rules = new ArrayList<>(mockRules);
        rules.addAll(MOCK_RULES.get());
        ApiExecution.Scope scope = ApiExecution.scope();
        var profile = io.testfly.internal.api.ApiTransport.prepare(scope, api, sslOverride, proxyOverride);
        return new Call(request, effectiveAuth, COOKIE_JAR.get(), AUTH_CHALLENGES.get(), cookiesEnabled,
                test, List.copyOf(all), List.copyOf(REQUEST_INTERCEPTORS), List.copyOf(RESPONSE_INTERCEPTORS),
                attempts, backoff, statuses, exceptions, LogSettings.capture(api), scope, List.copyOf(rules), List.copyOf(MOCK_RULES.get()), profile);
    }

    private record Call(ApiRequest request, ApiAuth auth, CookieJar cookies,
                        Map<String, DigestAuth.Challenge> challenges, boolean cookiesEnabled,
                        List<ApiInterceptor> testInterceptors, List<ApiInterceptor> interceptors,
                        List<RequestInterceptor> requestHooks, List<ResponseInterceptor> responseHooks,
                        int attempts, long backoff, List<Integer> retryStatuses, boolean retryExceptions,
                        LogSettings logging, ApiExecution.Scope scope, List<ApiMockRule> mockRules, List<ApiMockRule> testMockRules,
                        io.testfly.internal.api.ApiTransport.Profile profile) {}

    private ApiResponse execute(Call call) {
        long started = System.nanoTime();
        int[] exchanges = {0};
        try {
            for (int attempt = 1; attempt <= call.attempts; attempt++) {
                ApiExecution.check(call.scope);
                // Build auth afresh for every outer attempt, before user middleware.
                HttpRequest.Builder authenticated = HttpRequest.newBuilder(call.request.toHttpRequest(), (k, v) -> true);
                if (call.auth != null) call.auth.apply(authenticated);
                ApiRequest.Builder prepared = call.request.newBuilder();
                call.request.headers().keySet().forEach(prepared::removeHeader);
                authenticated.build().headers().map().forEach((k, values) -> values.forEach(v -> prepared.addHeader(k, v)));
                try {
                    ApiResponse response = ApiExecution.invoke(call.interceptors, prepared.build(),
                            request -> transport(request, call, exchanges), call.scope);
                    ApiExecution.check(call.scope);
                    // Hook errors are not transport failures and must not be retried.
                    for (ResponseInterceptor hook : call.responseHooks) {
                        ApiExecution.check(call.scope);
                        hook.intercept(response);
                    }
                    ApiExecution.check(call.scope);
                    if (attempt < call.attempts && call.retryStatuses.contains(response.status())) {
                        int current = attempt;
                        call.scope.log(() -> StepLogger.step("[API] Retry " + current + "/" + call.attempts
                                + " — status " + response.status(), StepStatus.WARN));
                        ApiExecution.delay(Math.multiplyExact(call.backoff, attempt), call.scope);
                        continue;
                    }
                    logStep(response, call.logging, call.scope, elapsed(started));
                    return response;
                } catch (ApiExecution.TransportFailure error) {
                    if (!call.retryExceptions || attempt == call.attempts) throw error;
                    int current = attempt;
                    call.scope.log(() -> StepLogger.step("[API] Retry " + current + "/" + call.attempts
                            + " — transport failure", StepStatus.WARN));
                    ApiExecution.delay(Math.multiplyExact(call.backoff, attempt), call.scope);
                }
            }
            throw new IllegalStateException("Unreachable API retry state");
        } catch (CancellationException error) {
            throw error;
        } catch (RuntimeException error) {
            call.scope.log(() -> StepLogger.step("[API] " + call.request.method() + " " + call.request.uri()
                    + " → ERROR: " + error.getClass().getSimpleName(), StepStatus.FAIL));
            throw error;
        }
    }

    private ApiResponse transport(ApiRequest request, Call call, int[] exchanges) {
        ApiExecution.check(call.scope);
        for (ApiMockRule rule : call.mockRules) {
            if (rule.matches(request)) return rule.respond(request);
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder(request.toHttpRequest(), (k, v) -> true);
        if (call.cookiesEnabled) {
            String cookieHeader = call.cookies.headerFor(request.uri());
            if (cookieHeader != null) builder.header("Cookie", cookieHeader);
        }
        for (RequestInterceptor hook : call.requestHooks) hook.intercept(builder);
        // Preserve the legacy builder hook contract: method/body are assigned after hooks.
        builder.method(request.method(), HttpRequest.BodyPublishers.ofByteArray(request.body()));
        HttpRequest http = builder.build();
        ApiRequest.Builder sentBuilder = request.newBuilder().uri(http.uri()).timeout(http.timeout().orElse(request.timeout()));
        request.headers().keySet().forEach(sentBuilder::removeHeader);
        http.headers().map().forEach((k, values) -> values.forEach(v -> sentBuilder.addHeader(k, v)));
        ApiRequest sent = sentBuilder.build();
        long started;
        int exchange = ++exchanges[0];
        try {
            ApiExecution.check(call.scope);
            HttpResponse<byte[]> raw;
            try (var permit = io.testfly.internal.api.ApiTransport.acquire(call.profile, call.scope)) {
                ApiExecution.check(call.scope);
                io.testfly.internal.api.ApiTransport.warnTrustAll(call.profile, call.scope, http.uri());
                started = System.nanoTime();
                raw = call.profile.client().send(http, HttpResponse.BodyHandlers.ofByteArray());
            }
            ApiExecution.check(call.scope);
            if (call.cookiesEnabled) call.cookies.capture(http.uri(), raw.headers().allValues("set-cookie"));
            ApiResponse response = ApiResponse.fromBytes(raw, elapsed(started), sent.method(), sent.uri().toString()).withRequest(sent);
            call.scope.log(() -> StepLogger.step("[API] Exchange " + exchange + " " + sent.method() + " " + sent.uri()
                    + " → " + response.status() + " (" + response.durationMs() + "ms)", StepStatus.INFO));
            return response;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new CancellationException("API transport interrupted");
        } catch (IOException error) {
            ApiExecution.check(call.scope);
            throw new ApiExecution.TransportFailure(sent, error);
        }
    }

    private static long elapsed(long start) { return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start); }

    private ApiRequest buildRequest(String url, Duration timeout) throws Exception {
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
        } else if (body instanceof byte[] raw) {
            bodyBytes = raw.clone();
            contentTypeOverride = "application/octet-stream";
        } else {
            bodyStr = serializeBody();
        }

        ApiRequest.Builder builder = ApiRequest.builder().uri(URI.create(url))
                .method(method).timeout(timeout);
        boolean contentTypeSet = headers.keySet().stream().anyMatch(k -> k.equalsIgnoreCase("Content-Type"));
        if (!contentTypeSet && contentTypeOverride != null) builder.header("Content-Type", contentTypeOverride);
        else if (!contentTypeSet && bodyStr != null) builder.header("Content-Type", "application/json");
        headers.forEach(builder::header);
        if (bodyBytes != null) builder.body(bodyBytes);
        else if (bodyStr != null) builder.body(bodyStr.getBytes(StandardCharsets.UTF_8));
        return builder.build();
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
            throw new IllegalStateException("[ApiClient] Unresolved path params in: " + resolvedPath
                    + " — missing pathParam() for placeholder");
        }

        String base = isAbsolute(resolvedPath) ? resolvedPath
                : resolveBaseUrl()
                        + (resolvedPath != null && resolvedPath.startsWith("/") ? resolvedPath : "/" + resolvedPath);

        if (!queryParams.isEmpty()) {
            List<String> pairs = new ArrayList<>();
            queryParams.forEach((k, v) -> {
                pairs.add(encodeQuery(k, v));
                extraQueryValues.getOrDefault(k, List.of()).forEach(extra -> pairs.add(encodeQuery(k, extra)));
            });
            base += (base.contains("?") ? "&" : "?") + String.join("&", pairs);
        }
        return base;
    }

    private static String encodeQuery(String key, String value) {
        return URLEncoder.encode(key, StandardCharsets.UTF_8) + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String resolveBaseUrl() {
        if (baseUrl != null)
            return baseUrl;
        try {
            TestFlyConfig config = TestFlyContext.getConfig();
            TestFlyConfig.Api api = config.getApi();
            if (api != null && api.getBaseUrl() != null)
                return api.getBaseUrl();
            String executionBase = config.getExecution().getBaseUrl();
            if (executionBase == null) {
                throw new IllegalStateException(
                        "[ApiClient] No baseUrl configured. Set execution.baseUrl or api.baseUrl in testfly.yml");
            }
            return executionBase;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "[ApiClient] No baseUrl configured. Set execution.baseUrl or api.baseUrl in testfly.yml");
        }
    }

    private Duration resolveTimeout() {
        if (timeoutOverride != null)
            return timeoutOverride;
        try {
            TestFlyConfig.Api api = TestFlyContext.getConfig().getApi();
            return Duration.ofSeconds(api != null ? api.getTimeoutSeconds() : 30);
        } catch (Exception e) {
            return Duration.ofSeconds(30);
        }
    }

    private String serializeBody() throws Exception {
        if (body == null)
            return null;
        if (body instanceof String)
            return (String) body;
        return MAPPER.writeValueAsString(body);
    }

    // ── Multipart ─────────────────────────────────────────────────────────────

    private static final class MultipartBody {
        final byte[] bytes;
        final String contentType;

        MultipartBody(byte[] bytes, String contentType) {
            this.bytes = bytes;
            this.contentType = contentType;
        }
    }

    private MultipartBody buildMultipartBody() throws IOException {
        String boundary = "TestFlyBoundary" + System.currentTimeMillis();
        String contentType = "multipart/form-data; boundary=" + boundary;
        List<byte[]> parts = new ArrayList<>();
        byte[] crlf = "\r\n".getBytes(StandardCharsets.UTF_8);
        byte[] dashBoundary = ("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8);

        for (Map.Entry<String, String> e : fieldParams.entrySet()) {
            parts.add(dashBoundary);
            parts.add(("Content-Disposition: form-data; name=\"" + e.getKey() + "\"\r\n\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            parts.add(e.getValue().getBytes(StandardCharsets.UTF_8));
            parts.add(crlf);
        }
        for (Map.Entry<String, Path> e : fileParams.entrySet()) {
            Path file = e.getValue();
            String fileName = file.getFileName().toString();
            String mime = Files.probeContentType(file);
            if (mime == null)
                mime = "application/octet-stream";
            parts.add(dashBoundary);
            parts.add(("Content-Disposition: form-data; name=\"" + e.getKey() + "\"; filename=\"" + fileName + "\"\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            parts.add(("Content-Type: " + mime + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            parts.add(Files.readAllBytes(file));
            parts.add(crlf);
        }
        parts.add(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        int total = parts.stream().mapToInt(b -> b.length).sum();
        byte[] all = new byte[total];
        int pos = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, all, pos, p.length);
            pos += p.length;
        }
        return new MultipartBody(all, contentType);
    }

    // ── Logging ───────────────────────────────────────────────────────────────

    private record LogSettings(boolean body, boolean pretty, boolean curl, int limit, Set<String> masked) {
        static LogSettings capture(TestFlyConfig.Api api) {
            Set<String> masked = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            masked.addAll(List.of("Authorization", "Cookie", "X-Api-Key"));
            if (api != null && api.getMaskedHeaders() != null) masked.addAll(api.getMaskedHeaders());
            return new LogSettings(api != null && api.isLogBody(), api != null && api.isPrettyLog(),
                    api != null && api.isLogCurl(), api == null ? 300 : api.getTruncationLimit(),
                    java.util.Collections.unmodifiableSet(masked));
        }
    }

    private void logStep(ApiResponse response, LogSettings settings, ApiExecution.Scope scope, long totalMs) {
        ApiRequest sent = response.request();
        StringBuilder log = new StringBuilder("[API] " + response.requestMethod() + " " + response.requestUrl()
                + " → " + response.status() + " (" + response.durationMs() + "ms; total " + totalMs + "ms)"
                + (response.isSynthetic() ? " [synthetic]" : ""));
        if (sent != null && !sent.headers().isEmpty()) {
            log.append("\n  Headers: ");
            sent.headers().forEach((k, values) -> log.append(k).append("=")
                    .append(settings.masked.contains(k) ? "***" : String.join(", ", values)).append(" "));
        }
        if (settings.body && response.body() != null && !response.body().isBlank()) {
            String value = response.body();
            if (settings.pretty) {
                try { value = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(MAPPER.readTree(value)); }
                catch (Exception ignored) { }
            }
            log.append("\n  Body: ").append(truncate(value, settings.limit));
        }
        if (settings.body && sent != null && sent.body().length > 0) {
            log.append("\n  Request body: ").append(describeRequestBody(sent, settings));
        }
        if (settings.curl && sent != null) log.append("\n  curl: ").append(toCurl(sent, settings));
        scope.log(() -> StepLogger.step(log.toString(), response.status() >= 400 ? StepStatus.FAIL : StepStatus.PASS));
    }

    /** Text bodies are logged (truncated); binary and multipart bodies only as a size summary. */
    private String describeRequestBody(ApiRequest request, LogSettings settings) {
        byte[] bytes = request.body();
        String type = request.header("Content-Type");
        String lower = type == null ? "" : type.toLowerCase(java.util.Locale.ROOT);
        boolean text = lower.startsWith("text/") || lower.contains("json") || lower.contains("xml")
                || lower.startsWith("application/x-www-form-urlencoded");
        if (!text) return "<binary, " + bytes.length + " bytes" + (type == null ? "" : ", " + type) + ">";
        String value = new String(bytes, StandardCharsets.UTF_8);
        if (settings.pretty && lower.contains("json")) {
            try { value = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(MAPPER.readTree(value)); }
            catch (Exception ignored) { }
        }
        return truncate(value, settings.limit);
    }

    private String toCurl(ApiRequest request, LogSettings settings) {
        StringBuilder curl = new StringBuilder("curl -X ").append(request.method()).append(" ").append(shellQuote(request.uri().toString()));
        request.headers().forEach((k, values) -> values.forEach(value -> curl.append(" -H ")
                .append(shellQuote(k + ": " + (settings.masked.contains(k) ? "***" : value)))));
        if (request.body().length > 0) curl.append(" --data-binary ")
                .append(shellQuote(new String(request.body(), StandardCharsets.UTF_8)));
        return curl.toString();
    }

    private static String shellQuote(String value) { return "'" + value.replace("'", "'\"'\"'") + "'"; }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private static ApiClient method(String method, String path) {
        ApiClient c = new ApiClient();
        c.method = method;
        c.path = path;
        return c;
    }

    private boolean isAbsolute(String path) {
        return path != null && (path.startsWith("http://") || path.startsWith("https://"));
    }

    private String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) + "..." : s;
    }

    // ── Interceptor interfaces ────────────────────────────────────────────────

    /**
     * Intercepts HTTP requests before they are sent. Use to add headers, log, etc.
     */
    @FunctionalInterface
    public interface RequestInterceptor {
        void intercept(HttpRequest.Builder builder);
    }

    /**
     * Intercepts HTTP responses after they are received. Use to log, collect
     * metrics, etc.
     */
    @FunctionalInterface
    public interface ResponseInterceptor {
        void intercept(ApiResponse response);
    }
}
