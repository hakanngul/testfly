package io.testfly.network;

import io.testfly.api.TestFlyApi;
import org.openqa.selenium.devtools.v144.fetch.model.RequestId;

import java.util.Map;
import java.util.logging.Logger;

/**
 * Handler-facing view of a single intercepted request, passed to a
 * {@code Consumer<Route>} registered via {@code mockRoute(pattern, handler)}.
 *
 * <p>A handler inspects the request, optionally fetches the real backend
 * response via {@link #fetchOriginal()}, then completes the request with exactly
 * one terminal action: {@link #fulfill(Response)}, {@link #passthrough()} or
 * {@link #abort()}. The first terminal call wins; later calls are ignored with a
 * logged warning. If the handler returns without terminating, the framework
 * defaults to {@link #passthrough()}.
 *
 * <p>A {@code Route} is created on the CDP listener thread at the RESPONSE stage,
 * is single-use, and is confined to that thread.
 */
@TestFlyApi(since = "1.6.0")
public final class Route {

    private static final Logger LOG = Logger.getLogger(Route.class.getName());

    private final NetworkMock         owner;
    private final RequestId           requestId;
    private final String              url;
    private final String              method;
    private final Map<String, String> headers;
    private final String              body;

    private volatile boolean terminated = false;

    // Response-stage context (bound before the handler runs).
    private int                 respStatus;
    private Map<String, String> respHeaders = Map.of();

    // Package-private — constructed by NetworkMock on the listener thread.
    Route(NetworkMock owner, String requestId, String url, String method,
          Map<String, String> headers, String body) {
        this.owner     = owner;
        this.requestId = new RequestId(requestId);
        this.url       = url;
        this.method    = method;
        this.headers   = headers == null ? Map.of() : headers;
        this.body      = body;
    }

    /** Binds the RESPONSE-stage status/headers so {@link #fetchOriginal()} can report them. */
    void bindResponseContext(int status, Map<String, String> responseHeaders) {
        this.respStatus  = status;
        this.respHeaders = responseHeaders == null ? Map.of() : responseHeaders;
    }

    // ------------------------------------------------------------------
    // Request accessors
    // ------------------------------------------------------------------

    /** The intercepted request URL. */
    public String url() { return url; }

    /** The intercepted request HTTP method (uppercase). */
    public String method() { return method; }

    /** The intercepted request headers (lower-cased keys, immutable). */
    public Map<String, String> headers() { return headers; }

    /** The intercepted request body, or {@code null} when absent. */
    public String body() { return body; }

    // ------------------------------------------------------------------
    // Fetch the real backend response
    // ------------------------------------------------------------------

    /**
     * Fetches the real backend response (status, headers, body).
     *
     * @throws io.testfly.exceptions.NetworkMockException if the body cannot be read;
     *         the request is never left hanging.
     */
    public OriginalResponse fetchOriginal() {
        return owner.fetchResponseBody(requestId, respStatus, respHeaders);
    }

    // ------------------------------------------------------------------
    // Terminal actions (exactly one expected)
    // ------------------------------------------------------------------

    /** Complete the request with a caller-supplied response. */
    public void fulfill(Response response) {
        if (!claim()) return;
        Response r = (response == null) ? Response.passthrough() : response;
        if (r.delayMs() > 0) sleep(r.delayMs());
        switch (r.kind()) {
            case ABORT:       owner.failRequest(requestId, r.abortReason()); break;
            case PASSTHROUGH: owner.continueResponse(requestId);             break;
            case FULFILL:
            default:          owner.fulfill(requestId, r);                   break;
        }
    }

    /** Let the real response proceed unmodified. */
    public void passthrough() {
        if (!claim()) return;
        owner.continueResponse(requestId);
    }

    /** Abort the request with {@link AbortReason#FAILED}. */
    public void abort() {
        abort(AbortReason.FAILED);
    }

    /** Abort the request with the given {@link AbortReason}. */
    public void abort(AbortReason reason) {
        if (!claim()) return;
        owner.failRequest(requestId, reason);
    }

    // ------------------------------------------------------------------
    // Internal
    // ------------------------------------------------------------------

    /** Returns true if this call may proceed (first terminal wins). */
    private boolean claim() {
        if (terminated) {
            LOG.warning("[TestFly] Route for " + url
                    + " was already terminated; ignoring extra terminal call.");
            return false;
        }
        terminated = true;
        return true;
    }

    boolean isTerminated() { return terminated; }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
