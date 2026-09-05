package io.testfly.network;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.testfly.api.TestFlyApi;
import io.testfly.exceptions.NetworkMockException;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable description of how a matched network route should respond.
 *
 * <p>Created via the static factories and passed to
 * {@code mockRoute(pattern, response)} or {@code route.fulfill(response)}.
 * Every instance is immutable: {@link #withHeader(String, String)},
 * {@link #withContentType(String)} and {@link #delay(Duration, Response)} all
 * return <em>new</em> instances, so a single {@code Response} may be declared
 * {@code static final} and shared across parallel test threads.
 *
 * <pre>
 * mockRoute("**&#47;api/me", Response.json(200, "{\"role\":\"ADMIN\"}"));
 * mockRoute("**&#47;analytics/**", Response.abort());
 * mockRoute("**&#47;api/slow", Response.delay(Duration.ofSeconds(2), Response.json(200, "[]")));
 * </pre>
 */
@TestFlyApi(since = "1.6.0")
public final class Response {

    /** What the framework should do with the matched request. Package-private. */
    enum Kind { FULFILL, ABORT, PASSTHROUGH }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Kind                kind;
    private final int                 status;
    private final String              body;
    private final String              contentType;
    private final Map<String, String> headers;      // extra headers, insertion order
    private final AbortReason         abortReason;
    private final long                delayMs;

    private Response(Kind kind, int status, String body, String contentType,
                     Map<String, String> headers, AbortReason abortReason, long delayMs) {
        this.kind        = kind;
        this.status      = status;
        this.body        = body;
        this.contentType = contentType;
        this.headers     = headers == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(headers));
        this.abortReason = abortReason;
        this.delayMs     = delayMs;
    }

    // ------------------------------------------------------------------
    // Fulfill factories
    // ------------------------------------------------------------------

    /** Respond with the given JSON body (Content-Type: application/json). */
    public static Response json(int status, String jsonBody) {
        return new Response(Kind.FULFILL, status, jsonBody == null ? "" : jsonBody,
                "application/json", null, null, 0);
    }

    /**
     * Respond with the given object serialized to JSON via Jackson.
     *
     * @throws NetworkMockException if the object cannot be serialized.
     */
    public static Response json(int status, Object pojo) {
        try {
            String jsonBody = MAPPER.writeValueAsString(pojo);
            return json(status, jsonBody);
        } catch (JsonProcessingException e) {
            String type = (pojo == null) ? "null" : pojo.getClass().getName();
            throw new NetworkMockException(
                    "Cannot serialize response body of type " + type + " to JSON", e);
        }
    }

    /** Respond with the given plain-text body (Content-Type: text/plain). */
    public static Response text(int status, String body) {
        return new Response(Kind.FULFILL, status, body == null ? "" : body,
                "text/plain", null, null, 0);
    }

    /** Respond with the given status code and an empty body. */
    public static Response status(int status) {
        return new Response(Kind.FULFILL, status, "", "application/json", null, null, 0);
    }

    // ------------------------------------------------------------------
    // Control factories
    // ------------------------------------------------------------------

    /** Abort the request with {@link AbortReason#FAILED}. */
    public static Response abort() {
        return abort(AbortReason.FAILED);
    }

    /** Abort the request with the given {@link AbortReason}. */
    public static Response abort(AbortReason reason) {
        return new Response(Kind.ABORT, 0, "", null, null,
                reason == null ? AbortReason.FAILED : reason, 0);
    }

    /** Let the real request proceed unmodified (still recorded for assertions). */
    public static Response passthrough() {
        return new Response(Kind.PASSTHROUGH, 0, "", null, null, null, 0);
    }

    // ------------------------------------------------------------------
    // Decoration
    // ------------------------------------------------------------------

    /**
     * Return a copy of {@code delegate} that is delayed by {@code duration}
     * before being applied.
     */
    public static Response delay(Duration duration, Response delegate) {
        long ms = duration == null ? 0 : Math.max(0, duration.toMillis());
        Response d = (delegate == null) ? passthrough() : delegate;
        return new Response(d.kind, d.status, d.body, d.contentType,
                d.headers, d.abortReason, ms);
    }

    /** Return a new copy with an additional response header. */
    public Response withHeader(String name, String value) {
        Map<String, String> h = new LinkedHashMap<>(this.headers);
        if (name != null) h.put(name, value);
        return new Response(kind, status, body, contentType, h, abortReason, delayMs);
    }

    /** Return a new copy with the given Content-Type. */
    public Response withContentType(String contentType) {
        return new Response(kind, status, body, contentType, headers, abortReason, delayMs);
    }

    // ------------------------------------------------------------------
    // Package-private accessors (read by NetworkMock)
    // ------------------------------------------------------------------

    Kind                kind()        { return kind; }
    int                 status()      { return status; }
    String              body()        { return body; }
    String              contentType() { return contentType; }
    Map<String, String> headers()     { return headers; }
    AbortReason         abortReason() { return abortReason; }
    long                delayMs()     { return delayMs; }

    @Override
    public String toString() {
        return "Response{kind=" + kind + ", status=" + status
                + ", contentType=" + contentType + ", delayMs=" + delayMs + "}";
    }
}
