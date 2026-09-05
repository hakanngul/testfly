package io.testfly.network;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * Internal unified rule the {@code NetworkMock} listener walks. Both legacy
 * {@link StubBuilder} stubs and new {@code mockRoute(...)} routes are represented
 * as {@code RouteRule}s in a single ordered list, so matching precedence is
 * simply first-match-in-registration-order (with an exact-method preference).
 *
 * <p>Exactly one of {@link #response} or {@link #handler} is non-null.
 */
final class RouteRule {

    enum Source { LEGACY_STUB, ROUTE, BLOCKLIST }

    final String          pattern;   // glob, existing NetworkMock semantics
    final String          method;    // uppercase; "*" = any
    final Response        response;   // non-null XOR handler
    final Consumer<Route> handler;    // non-null XOR response
    final Source          source;

    RouteRule(String pattern, String method, Response response,
              Consumer<Route> handler, Source source) {
        this.pattern  = pattern;
        this.method   = normalizeMethod(method);
        this.response = response;
        this.handler  = handler;
        this.source   = source;
    }

    /** URL glob match AND method match. */
    boolean matches(String url, String requestMethod) {
        return NetworkMock.matches(pattern, url) && methodMatches(requestMethod);
    }

    boolean methodMatches(String requestMethod) {
        if ("*".equals(method)) return true;
        return method.equalsIgnoreCase(requestMethod);
    }

    /** True when this rule targets a specific HTTP method (not the {@code *} wildcard). */
    boolean isExactMethod() {
        return !"*".equals(method);
    }

    /** True when a handler callback must run (needs the RESPONSE stage). */
    boolean isHandler() {
        return handler != null;
    }

    /**
     * Adapt a legacy {@link StubBuilder} into an equivalent fulfill rule so the
     * listener treats it identically to a {@code mockRoute(pattern, Response...)}.
     */
    static RouteRule fromLegacyStub(StubBuilder stub) {
        Response resp = Response.json(stub.statusCode, stub.responseBody)
                .withContentType(stub.contentType);
        if (stub.delayMs > 0) {
            resp = Response.delay(java.time.Duration.ofMillis(stub.delayMs), resp);
        }
        return new RouteRule(stub.pattern, "*", resp, null, Source.LEGACY_STUB);
    }

    private static String normalizeMethod(String m) {
        if (m == null || m.isBlank()) return "*";
        return m.trim().toUpperCase(Locale.ROOT);
    }
}
