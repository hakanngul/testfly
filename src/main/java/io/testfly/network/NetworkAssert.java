package io.testfly.network;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.testfly.api.TestFlyApi;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Fluent assertions over the requests recorded by {@link NetworkMock}.
 *
 * <pre>
 * assertThatNetwork().request("**&#47;api/checkout")
 *     .hasCount(1)
 *     .hasMethod("POST")
 *     .hasHeader("authorization")
 *     .hasJsonBody("/currency", "USD");
 * </pre>
 *
 * <p>Header names are matched case-insensitively. JSON body paths use a Jackson
 * {@link JsonPointer} (e.g. {@code /user/id}, {@code /items/0/sku}) — no JSONPath
 * dependency is required.
 */
@TestFlyApi(since = "1.6.0")
public final class NetworkAssert {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final NetworkMock          mock;
    private final List<RecordedRequest> all;
    private List<RecordedRequest>       scope;   // narrowed by request(pattern)
    private String                      scopePattern = "*";

    NetworkAssert(NetworkMock mock) {
        this.mock  = mock;
        this.all   = mock.recordedRequests();
        this.scope = new ArrayList<>(all);
    }

    /** Narrows subsequent assertions to recorded requests whose URL matches {@code pattern}. */
    public NetworkAssert request(String pattern) {
        this.scopePattern = pattern;
        List<RecordedRequest> narrowed = new ArrayList<>();
        for (RecordedRequest r : all) {
            if (NetworkMock.matches(pattern, r.url())) narrowed.add(r);
        }
        this.scope = narrowed;
        return this;
    }

    /** Asserts exactly {@code n} matching requests were recorded. */
    public NetworkAssert hasCount(int n) {
        if (scope.size() != n) {
            fail("expected " + n + " request(s) but found " + scope.size());
        }
        return this;
    }

    /** Asserts at least one matching request used the given method (case-insensitive). */
    public NetworkAssert hasMethod(String method) {
        boolean ok = scope.stream().anyMatch(r -> r.method().equalsIgnoreCase(method));
        if (!ok) {
            fail("expected a request with method '" + method + "' but found methods "
                    + methodsSeen());
        }
        return this;
    }

    /** Asserts at least one matching request carried the header (presence, case-insensitive). */
    public NetworkAssert hasHeader(String name) {
        boolean ok = scope.stream().anyMatch(r -> r.header(name).isPresent());
        if (!ok) fail("expected a request carrying header '" + name + "' but none did");
        return this;
    }

    /** Asserts at least one matching request carried the header with the exact value. */
    public NetworkAssert hasHeader(String name, String expectedValue) {
        boolean ok = scope.stream()
                .anyMatch(r -> r.header(name).map(v -> Objects.equals(v, expectedValue)).orElse(false));
        if (!ok) {
            fail("expected header '" + name + "' == '" + expectedValue + "' but no request matched");
        }
        return this;
    }

    /** Asserts at least one matching request carried the header satisfying the predicate. */
    public NetworkAssert hasHeader(String name, Predicate<String> valueMatcher) {
        boolean ok = scope.stream()
                .anyMatch(r -> r.header(name).map(valueMatcher::test).orElse(false));
        if (!ok) {
            fail("expected header '" + name + "' to satisfy the given matcher but no request matched");
        }
        return this;
    }

    /** Asserts at least one matching request's JSON body has {@code expected} at {@code jsonPointer}. */
    public NetworkAssert hasJsonBody(String jsonPointer, Object expected) {
        return hasJsonBody(jsonPointer, node -> jsonNodeEquals(node, expected));
    }

    /** Asserts at least one matching request's JSON body satisfies {@code matcher} at {@code jsonPointer}. */
    public NetworkAssert hasJsonBody(String jsonPointer, Predicate<JsonNode> matcher) {
        JsonPointer ptr = JsonPointer.compile(jsonPointer);
        boolean anyJson = false;
        for (RecordedRequest r : scope) {
            String body = r.body();
            if (body == null || body.isBlank()) continue;
            JsonNode root;
            try {
                root = MAPPER.readTree(body);
            } catch (Exception e) {
                continue; // non-JSON body — skip; reported below if none match
            }
            anyJson = true;
            JsonNode node = root.at(ptr);
            if (!node.isMissingNode() && matcher.test(node)) {
                return this;
            }
        }
        if (!anyJson) {
            fail("hasJsonBody('" + jsonPointer + "'): no matching request had a valid JSON body");
        }
        fail("hasJsonBody('" + jsonPointer + "'): no matching request satisfied the expected value");
        return this;
    }

    // ------------------------------------------------------------------
    // Internal
    // ------------------------------------------------------------------

    private static boolean jsonNodeEquals(JsonNode node, Object expected) {
        if (expected == null) return node.isNull();
        if (expected instanceof Number) {
            return node.isNumber() && node.asDouble() == ((Number) expected).doubleValue();
        }
        if (expected instanceof Boolean) {
            return node.isBoolean() && node.asBoolean() == (Boolean) expected;
        }
        return node.asText().equals(expected.toString());
    }

    private List<String> methodsSeen() {
        List<String> m = new ArrayList<>();
        for (RecordedRequest r : scope) m.add(r.method());
        return m;
    }

    private void fail(String detail) {
        String base = "[TestFly] Network assertion for pattern '" + scopePattern + "': " + detail + ".";
        if (!mock.isInterceptionActive()) {
            base += " Network interception is unavailable on " + mock.browserName()
                    + " (Chromium-only); recorded traffic is empty.";
        }
        throw new AssertionError(base);
    }
}
