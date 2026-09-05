package io.testfly.network;

import io.testfly.api.TestFlyApi;
import io.testfly.driver.DriverManager;
import io.testfly.exceptions.NetworkMockException;
import io.testfly.internal.TestFlyContext;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chromium.ChromiumDriver;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.v144.fetch.Fetch;
import org.openqa.selenium.devtools.v144.fetch.model.HeaderEntry;
import org.openqa.selenium.devtools.v144.fetch.model.RequestId;
import org.openqa.selenium.devtools.v144.fetch.model.RequestPattern;
import org.openqa.selenium.devtools.v144.fetch.model.RequestStage;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * CDP-backed network interception, request mocking, and traffic recording.
 *
 * <p>Supported on Chrome and Edge (Chromium-based). On Firefox or other browsers
 * a single warning is logged and all interception is skipped — tests continue.
 *
 * <p>Two DSLs are available and interoperate (rules apply in registration order):
 * <ul>
 *   <li><b>Legacy stubs</b> ({@code @TestFlyApi(since = "1.5.0")}):
 *       {@code networkMock().stub(pattern).returnJson(...)}</li>
 *   <li><b>Route DSL</b> ({@code @TestFlyApi(since = "1.6.0")}):
 *       {@code mockRoute(pattern, Response.json(...))} and
 *       {@code mockRoute(pattern, route -> ...)} for mutation/spy.</li>
 * </ul>
 *
 * <pre>
 * networkMock().stub("**&#47;api/users").returnJson("{\"users\":[]}");   // legacy
 * mockRoute("**&#47;api/me", Response.json(200, "{\"role\":\"ADMIN\"}"));  // new
 * mockRoute("**&#47;api/settings", route -&gt; {                           // mutate
 *     var original = route.fetchOriginal();
 *     route.fulfill(Response.json(200, original.body().replace("false","true")));
 * });
 * assertThatNetwork().request("**&#47;api/checkout").hasCount(1);          // assert
 * </pre>
 */
@TestFlyApi(since = "1.5.0")
public final class NetworkMock {

    private static final Logger LOG = Logger.getLogger(NetworkMock.class.getName());

    private static final ThreadLocal<NetworkMock> INSTANCE =
            ThreadLocal.withInitial(NetworkMock::new);

    // Legacy stubs kept for source compatibility; also mirrored into routes.
    private final List<StubBuilder> stubs = new CopyOnWriteArrayList<>();

    // Unified ordered rule list driving the single listener (1.6.0).
    private final List<RouteRule> routes = new CopyOnWriteArrayList<>();

    // Recorded requests for NetworkAssert (1.6.0).
    private final List<RecordedRequest> recorded = new CopyOnWriteArrayList<>();

    private boolean cdpAttached = false;
    private volatile boolean blocklistActivated = false;
    private DevTools devTools;

    private NetworkMock() {
    }

    /** Returns the per-thread {@link NetworkMock} instance. */
    public static NetworkMock get() {
        return INSTANCE.get();
    }

    /** Removes the per-thread instance (called by the framework after each test). */
    public static void cleanup() {
        NetworkMock mock = INSTANCE.get();
        if (mock != null) mock.clear();
        INSTANCE.remove();
    }

    // ------------------------------------------------------------------
    // Legacy public API (1.5.0)
    // ------------------------------------------------------------------

    /**
     * Begins a stub for requests whose URL matches the given pattern.
     *
     * <p>Pattern syntax:
     * <ul>
     *   <li>{@code *}  — matches any characters except {@code /}</li>
     *   <li>{@code **} — matches any characters including {@code /}</li>
     *   <li>Exact URL — e.g. {@code https://api.example.com/users}</li>
     * </ul>
     *
     * @param urlPattern glob-style URL pattern
     * @return a {@link StubBuilder} to configure the response
     */
    public StubBuilder stub(String urlPattern) {
        return new StubBuilder(this, urlPattern);
    }

    /**
     * Removes all registered stubs/routes and disables CDP interception for this thread.
     * Called automatically by the framework after each test.
     */
    public void clear() {
        stubs.clear();
        routes.clear();
        recorded.clear();
        blocklistActivated = false;
        if (devTools != null) {
            try {
                devTools.send(Fetch.disable());
            } catch (Exception ignored) {
                // DevTools session may already be closed
            }
            devTools = null;
        }
        cdpAttached = false;
    }

    // ------------------------------------------------------------------
    // Route DSL (1.6.0)
    // ------------------------------------------------------------------

    /** Mock any-method requests matching {@code pattern} with a fixed {@link Response}. */
    @TestFlyApi(since = "1.6.0")
    public NetworkMock mockRoute(String pattern, Response response) {
        routes.add(new RouteRule(pattern, "*", response, null, RouteRule.Source.ROUTE));
        ensureCdpAttached();
        return this;
    }

    /** Mock any-method requests matching {@code pattern} with a programmatic handler. */
    @TestFlyApi(since = "1.6.0")
    public NetworkMock mockRoute(String pattern, Consumer<Route> handler) {
        routes.add(new RouteRule(pattern, "*", null, handler, RouteRule.Source.ROUTE));
        ensureCdpAttached();
        return this;
    }

    /** Mock requests matching {@code method} + {@code pattern} with a fixed {@link Response}. */
    @TestFlyApi(since = "1.6.0")
    public NetworkMock mockRoute(String method, String pattern, Response response) {
        routes.add(new RouteRule(pattern, method, response, null, RouteRule.Source.ROUTE));
        ensureCdpAttached();
        return this;
    }

    /** Mock requests matching {@code method} + {@code pattern} with a programmatic handler. */
    @TestFlyApi(since = "1.6.0")
    public NetworkMock mockRoute(String method, String pattern, Consumer<Route> handler) {
        routes.add(new RouteRule(pattern, method, null, handler, RouteRule.Source.ROUTE));
        ensureCdpAttached();
        return this;
    }

    /** Entry point for traffic assertions over recorded requests. */
    @TestFlyApi(since = "1.6.0")
    public NetworkAssert assertThat() {
        return new NetworkAssert(this);
    }

    // ------------------------------------------------------------------
    // Blocklist (1.6.0)
    // ------------------------------------------------------------------

    /**
     * Activates the global {@code network.blockUrls} blocklist for this session,
     * once. No-op when the list is empty (zero overhead when unused) or already
     * activated.
     */
    @TestFlyApi(since = "1.6.0")
    public void activateBlocklistIfConfigured() {
        if (blocklistActivated) return;
        List<String> block = configBlockUrls();
        if (block == null || block.isEmpty()) return;
        for (String pattern : block) {
            if (pattern == null || pattern.isBlank()) continue;
            routes.add(new RouteRule(pattern, "*",
                    Response.abort(AbortReason.BLOCKED_BY_CLIENT), null,
                    RouteRule.Source.BLOCKLIST));
        }
        ensureCdpAttached();
        blocklistActivated = true;
    }

    private List<String> configBlockUrls() {
        try {
            io.testfly.config.TestFlyConfig.Network net = TestFlyContext.getConfig().getNetwork();
            return net != null ? net.getBlockUrls() : null;
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Internal — called by StubBuilder / NetworkAssert / Route
    // ------------------------------------------------------------------

    /** Legacy stub registration — also mirrored into the unified route list. */
    void register(StubBuilder stub) {
        stubs.add(stub);
        routes.add(RouteRule.fromLegacyStub(stub));
        ensureCdpAttached();
    }

    /** Read-only view of recorded requests (package-private, for NetworkAssert). */
    List<RecordedRequest> recordedRequests() {
        return new ArrayList<>(recorded);
    }

    /** Package-private test hook: seed a recorded request without a live browser. */
    void recordForTest(RecordedRequest request) {
        recorded.add(request);
    }

    /**
     * Whether CDP network interception is active for this thread's browser.
     * Returns {@code false} on non-Chromium browsers or when no CDP implementation
     * matches the installed browser version.
     */
    @TestFlyApi(since = "1.6.0")
    public boolean isInterceptionActive() {
        return cdpAttached && devTools != null;
    }

    /** The browser name for diagnostics when interception is unavailable. */
    String browserName() {
        try {
            WebDriver d = DriverManager.getDriver();
            return d != null ? d.getClass().getSimpleName() : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }

    // ------------------------------------------------------------------
    // CDP wiring
    // ------------------------------------------------------------------

    private void ensureCdpAttached() {
        if (cdpAttached) return;

        WebDriver driver = DriverManager.getDriver();
        if (!(driver instanceof ChromiumDriver)) {
            LOG.warning("[TestFly] Network interception requires Chrome/Edge. " +
                    "Routes/stubs will be ignored on "
                    + (driver == null ? "no active driver" : driver.getClass().getSimpleName())
                    + ".");
            cdpAttached = true; // prevents repeated warnings; no CDP calls attempted
            return;
        }

        try {
            DevTools dt = ((ChromiumDriver) driver).getDevTools();
            dt.createSessionIfThereIsNotOne();

            // Intercept at BOTH request and response stages. Fulfill/abort/stub/blocklist
            // act at REQUEST (fast, never leaves the browser). Handler rules that need
            // fetchOriginal() act at RESPONSE, where Fetch.getResponseBody works.
            List<RequestPattern> patterns = List.of(
                    new RequestPattern(Optional.of("*"), Optional.empty(),
                            Optional.of(RequestStage.REQUEST)),
                    new RequestPattern(Optional.of("*"), Optional.empty(),
                            Optional.of(RequestStage.RESPONSE))
            );
            dt.send(Fetch.enable(Optional.of(patterns), Optional.empty()));
            dt.addListener(Fetch.requestPaused(), event -> handlePaused(event));

            this.devTools = dt;
            cdpAttached = true;
        } catch (org.openqa.selenium.devtools.DevToolsException e) {
            // No matching CDP implementation for this browser version (e.g. the
            // installed Chrome is newer than the Selenium CDP bindings). Degrade
            // gracefully rather than failing the test.
            LOG.warning("[TestFly] Network interception unavailable: no CDP implementation "
                    + "matching this browser version. Routes/stubs will be ignored. "
                    + "Consider aligning the Chrome version with the Selenium release. "
                    + "(" + e.getMessage() + ")");
            this.devTools = null;
            cdpAttached = true; // prevents repeated attempts/warnings
        } catch (Exception e) {
            LOG.warning("[TestFly] Network interception could not attach: " + e.getMessage());
            this.devTools = null;
            cdpAttached = true;
        }
    }

    // Handles a single Fetch.requestPaused event (either REQUEST or RESPONSE stage).
    private void handlePaused(org.openqa.selenium.devtools.v144.fetch.model.RequestPaused event) {
        try {
            String    url    = event.getRequest().getUrl();
            String    method = safeUpper(event.getRequest().getMethod());
            RequestId reqId  = event.getRequestId();
            boolean   atResponseStage = event.getResponseStatusCode().isPresent();

            // Record every request exactly once, at the REQUEST stage.
            if (!atResponseStage) {
                record(event, url, method);
            }

            RouteRule rule = findMatch(url, method);

            // ── No explicit rule: consult blocklist as a last resort ──
            if (rule == null) {
                RouteRule blocked = findBlocklistMatch(url, method);
                if (blocked != null) {
                    if (!atResponseStage) {
                        failRequest(reqId, blocked.response.abortReason());
                    }
                    return;
                }
                // Nothing matched — continue unmodified. Only act at REQUEST stage;
                // if it reaches RESPONSE stage, continue the response too.
                if (atResponseStage) {
                    continueResponse(reqId);
                } else {
                    continueRequest(reqId);
                }
                return;
            }

            // ── Handler rule: needs the RESPONSE stage for fetchOriginal() ──
            if (rule.isHandler()) {
                if (!atResponseStage) {
                    // Let the request hit the network so a real response exists.
                    continueRequest(reqId);
                    return;
                }
                dispatchHandler(rule, event, url, method, reqId);
                return;
            }

            // ── Fulfill / abort / legacy stub: act at REQUEST stage ──
            if (!atResponseStage) {
                applyResponse(reqId, rule.response);
            }
            // If it somehow reaches RESPONSE stage (already terminated at REQUEST),
            // there is nothing to do.
        } catch (Exception e) {
            LOG.fine("[TestFly] Network listener error: " + e.getMessage());
            // Best-effort: never leave a request hanging.
            try { continueRequest(event.getRequestId()); } catch (Exception ignored) {}
        }
    }

    private void dispatchHandler(RouteRule rule,
                                 org.openqa.selenium.devtools.v144.fetch.model.RequestPaused event,
                                 String url, String method, RequestId reqId) {
        Map<String, String> reqHeaders = lowerCasedRequestHeaders(event);
        String reqBody = event.getRequest().getPostData().orElse(null);
        int    respStatus  = event.getResponseStatusCode().orElse(0);
        Map<String, String> respHeaders = lowerCasedResponseHeaders(event);

        Route route = new Route(this, reqId.toString(), url, method, reqHeaders, reqBody);
        route.bindResponseContext(respStatus, respHeaders);

        try {
            rule.handler.accept(route);
        } catch (Exception e) {
            LOG.warning("[TestFly] Route '" + rule.pattern + "' handler threw: "
                    + e.getMessage() + " — defaulting to passthrough.");
            if (!route.isTerminated()) route.passthrough();
            return;
        }

        if (!route.isTerminated()) {
            LOG.warning("[TestFly] Route '" + rule.pattern + "' handler returned without "
                    + "fulfill/abort/passthrough — defaulting to passthrough.");
            route.passthrough();
        }
    }

    // ------------------------------------------------------------------
    // Rule matching
    // ------------------------------------------------------------------

    /**
     * Finds the matching non-blocklist rule for a URL + method.
     * Returns an exact-method match immediately; otherwise the first any-method match.
     */
    private RouteRule findMatch(String url, String method) {
        RouteRule wildcardFallback = null;
        for (RouteRule r : routes) {
            if (r.source == RouteRule.Source.BLOCKLIST) continue;
            if (!NetworkMock.matches(r.pattern, url)) continue;
            if (r.isExactMethod() && r.method.equalsIgnoreCase(method)) {
                return r; // exact method wins immediately
            }
            if (!r.isExactMethod() && wildcardFallback == null) {
                wildcardFallback = r;
            }
        }
        return wildcardFallback;
    }

    /**
     * Package-private test hook: resolves the rule that would apply to a request,
     * mirroring the listener's precedence (explicit route/stub first, then blocklist).
     * Returns {@code null} if nothing matches. Used by unit tests to verify routing
     * precedence without mocking CDP events.
     */
    RouteRule resolveRuleForTest(String url, String method) {
        RouteRule explicit = findMatch(url, method);
        if (explicit != null) return explicit;
        return findBlocklistMatch(url, method);
    }

    /** Blocklist is lowest priority: only consulted when no explicit rule matched. */
    private RouteRule findBlocklistMatch(String url, String method) {
        for (RouteRule r : routes) {
            if (r.source != RouteRule.Source.BLOCKLIST) continue;
            if (r.matches(url, method)) return r;
        }
        return null;
    }

    private void record(org.openqa.selenium.devtools.v144.fetch.model.RequestPaused event,
                        String url, String method) {
        Map<String, String> headers = lowerCasedRequestHeaders(event);
        String body = event.getRequest().getPostData().orElse(null);
        recorded.add(new RecordedRequest(url, method, headers, body, Instant.now()));
    }

    // ------------------------------------------------------------------
    // Response application (package-private helpers used by Route too)
    // ------------------------------------------------------------------

    /** Apply a {@link Response} to a paused request at REQUEST stage. */
    void applyResponse(RequestId reqId, Response response) {
        if (response.delayMs() > 0) {
            sleep(response.delayMs());
        }
        switch (response.kind()) {
            case ABORT:
                failRequest(reqId, response.abortReason());
                break;
            case PASSTHROUGH:
                continueRequest(reqId);
                break;
            case FULFILL:
            default:
                fulfill(reqId, response);
                break;
        }
    }

    void fulfill(RequestId reqId, Response response) {
        String body64 = Base64.getEncoder().encodeToString(
                (response.body() == null ? "" : response.body()).getBytes(StandardCharsets.UTF_8));

        Map<String, String> hdrs = new LinkedHashMap<>();
        if (response.contentType() != null) {
            hdrs.put("content-type", response.contentType());
        }
        hdrs.putAll(response.headers());

        List<HeaderEntry> headerEntries = new ArrayList<>();
        for (Map.Entry<String, String> e : hdrs.entrySet()) {
            headerEntries.add(new HeaderEntry(e.getKey(), e.getValue()));
        }

        devTools.send(Fetch.fulfillRequest(
                reqId,
                response.status(),
                Optional.of(headerEntries),
                Optional.empty(),
                Optional.of(body64),
                Optional.empty()));
    }

    void failRequest(RequestId reqId, AbortReason reason) {
        AbortReason r = (reason == null) ? AbortReason.FAILED : reason;
        devTools.send(Fetch.failRequest(reqId, r.toCdp()));
    }

    void continueRequest(RequestId reqId) {
        devTools.send(Fetch.continueRequest(
                reqId,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()));
    }

    void continueResponse(RequestId reqId) {
        // At RESPONSE stage, continueRequest lets the real response flow to the page.
        try {
            devTools.send(Fetch.continueRequest(
                    reqId,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty()));
        } catch (Exception e) {
            LOG.fine("[TestFly] continueResponse failed: " + e.getMessage());
        }
    }

    /** Fetch the real response body at RESPONSE stage — used by {@link Route#fetchOriginal()}. */
    OriginalResponse fetchResponseBody(RequestId reqId, int status, Map<String, String> headers) {
        try {
            Fetch.GetResponseBodyResponse b = devTools.send(Fetch.getResponseBody(reqId));
            String body = b.getBase64Encoded()
                    ? new String(Base64.getDecoder().decode(b.getBody()), StandardCharsets.UTF_8)
                    : b.getBody();
            return new OriginalResponse(status, headers, body);
        } catch (Exception e) {
            throw new NetworkMockException(
                    "fetchOriginal() failed — the response body was unavailable "
                    + "(request may not have reached the RESPONSE stage, or the browser "
                    + "is not Chromium). Interception was not left hanging.", e);
        }
    }

    DevTools devTools() {
        return devTools;
    }

    // ------------------------------------------------------------------
    // Header helpers
    // ------------------------------------------------------------------

    private static Map<String, String> lowerCasedRequestHeaders(
            org.openqa.selenium.devtools.v144.fetch.model.RequestPaused event) {
        Map<String, String> out = new LinkedHashMap<>();
        try {
            Map<String, Object> raw = event.getRequest().getHeaders();
            if (raw != null) {
                for (Map.Entry<String, Object> e : raw.entrySet()) {
                    if (e.getKey() != null) {
                        out.put(e.getKey().toLowerCase(Locale.ROOT),
                                e.getValue() == null ? "" : e.getValue().toString());
                    }
                }
            }
        } catch (Exception ignored) {
            // headers best-effort
        }
        return out;
    }

    private static Map<String, String> lowerCasedResponseHeaders(
            org.openqa.selenium.devtools.v144.fetch.model.RequestPaused event) {
        Map<String, String> out = new LinkedHashMap<>();
        try {
            event.getResponseHeaders().ifPresent(list -> {
                for (HeaderEntry h : list) {
                    if (h.getName() != null) {
                        out.put(h.getName().toLowerCase(Locale.ROOT), h.getValue());
                    }
                }
            });
        } catch (Exception ignored) {
            // headers best-effort
        }
        return out;
    }

    private static String safeUpper(String s) {
        return s == null ? "GET" : s.toUpperCase(Locale.ROOT);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ------------------------------------------------------------------
    // Pattern matching (1.5.0 public API — unchanged)
    // ------------------------------------------------------------------

    /**
     * Glob-style URL matching.
     * {@code **} matches anything including slashes; {@code *} matches within a path segment.
     */
    public static boolean matches(String pattern, String url) {
        return url.matches(globToRegex(pattern));
    }

    public static String globToRegex(String glob) {
        StringBuilder sb = new StringBuilder("(?i)");
        int i = 0;
        while (i < glob.length()) {
            char c = glob.charAt(i);
            if (c == '*' && i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                sb.append(".*");
                i += 2;
                if (i < glob.length() && glob.charAt(i) == '/') i++; // skip trailing /
            } else if (c == '*') {
                sb.append("[^/]*");
                i++;
            } else if (c == '?') {
                sb.append("[^/]");
                i++;
            } else {
                sb.append(java.util.regex.Pattern.quote(String.valueOf(c)));
                i++;
            }
        }
        return sb.toString();
    }
}
