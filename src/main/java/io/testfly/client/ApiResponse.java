package io.testfly.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.testfly.api.TestFlyApi;
import io.testfly.steps.StepLogger;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Rich wrapper around an HTTP response.
 *
 * <pre>
 * ApiResponse res = apiClient().get("/api/users/1").send();
 * res.assertStatus(200);
 * String name = res.json("$.user.name");
 * User user   = res.asObject(User.class);
 * </pre>
 */
@TestFlyApi(since = "1.0.0")
public class ApiResponse {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final int statusCode;
    private final String responseBody;
    private final java.net.http.HttpHeaders responseHeaders;
    private final ApiRequest request;
    private final boolean synthetic;
    private final long durationMs;
    private final String requestMethod;
    private final String requestUrl;
    private JsonNode parsedBody;

    ApiResponse(HttpResponse<String> response, long durationMs) {
        this(response, durationMs, null, null);
    }

    ApiResponse(HttpResponse<String> response, long durationMs, String requestMethod, String requestUrl) {
        this.statusCode = response.statusCode();
        this.responseBody = response.body();
        this.responseHeaders = response.headers();
        this.request = null;
        this.synthetic = false;
        this.durationMs    = durationMs;
        this.requestMethod = requestMethod;
        this.requestUrl    = requestUrl;
    }


    private ApiResponse(Builder b) {
        request = java.util.Objects.requireNonNull(b.request, "request");
        if (b.status < 100 || b.status > 599) throw new IllegalArgumentException("HTTP status must be 100..599");
        if (b.duration < 0) throw new IllegalArgumentException("duration must be non-negative");
        statusCode = b.status;
        responseBody = java.util.Objects.requireNonNull(b.body, "body");
        responseHeaders = java.net.http.HttpHeaders.of(b.headers, (k, v) -> true);
        durationMs = b.duration;
        requestMethod = request.method();
        requestUrl = request.uri().toString();
        synthetic = b.synthetic;
    }

    @TestFlyApi(since = "1.1.0")
    public static Builder builder() { return new Builder(); }

    @TestFlyApi(since = "1.1.0")
    public Builder newBuilder() {
        Builder b = new Builder().status(status()).body(body()).durationMs(durationMs);
        b.request = request;
        b.synthetic = synthetic;
        responseHeaders.map().forEach((k, values) -> b.headers.put(k, new java.util.ArrayList<>(values)));
        return b;
    }

    @TestFlyApi(since = "1.1.0")
    public ApiRequest request() { return request; }

    @TestFlyApi(since = "1.1.0")
    public boolean isSynthetic() { return synthetic; }

    /** Attach the actually sent request without changing legacy constructors. */
    ApiResponse withRequest(ApiRequest value) {
        return newBuilder().request(value).build();
    }

    @TestFlyApi(since = "1.1.0")
    public static final class Builder {
        private ApiRequest request;
        private int status;
        private String body = "";
        private long duration;
        private boolean synthetic = true;
        private final java.util.Map<String, List<String>> headers = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        private Builder() {}
        public Builder request(ApiRequest value) { request = java.util.Objects.requireNonNull(value); return this; }
        public Builder status(int value) { status = value; return this; }
        public Builder body(String value) { body = java.util.Objects.requireNonNull(value); return this; }
        public Builder durationMs(long value) { duration = value; return this; }
        public Builder header(String name, String value) {
            headers.put(java.util.Objects.requireNonNull(name), new java.util.ArrayList<>(List.of(value))); return this;
        }
        public Builder addHeader(String name, String value) {
            headers.computeIfAbsent(java.util.Objects.requireNonNull(name), k -> new java.util.ArrayList<>()).add(java.util.Objects.requireNonNull(value)); return this;
        }
        public Builder removeHeader(String name) { headers.remove(name); return this; }
        public ApiResponse build() { return new ApiResponse(this); }
    }

    /** HTTP status code. */
    public int status() {
        return statusCode;
    }

    public String requestMethod() {
        return requestMethod;
    }

    public String requestUrl() {
        return requestUrl;
    }

    /** Raw response body as String. */
    public String body() {
        return responseBody;
    }

    /** Duration of the request in milliseconds. */
    public long durationMs() {
        return durationMs;
    }

    /** Response header value, or null if absent. */
    public String header(String name) {
        return responseHeaders.firstValue(name).orElse(null);
    }

    /**
     * JSONPath-style extraction (supports {@code $.field}, {@code $.a.b}, {@code $.items[0].name}).
     * Returns null if the path is not found.
     */
    public String json(String path) {
        JsonNode node = jsonNode(path);
        return node == null || node.isMissingNode() ? null : node.asText();
    }

    /** JSONPath extraction with type conversion. */
    @SuppressWarnings("unchecked")
    public <T> T json(String path, Class<T> type) {
        JsonNode node = jsonNode(path);
        if (node == null || node.isMissingNode()) return null;
        try {
            return MAPPER.treeToValue(node, type);
        } catch (Exception e) {
            throw new RuntimeException("[ApiResponse] Cannot convert '" + path + "' to " + type.getSimpleName(), e);
        }
    }

    /** JSONPath extraction with TypeReference (generic types). */
    public <T> T json(String path, TypeReference<T> typeRef) {
        JsonNode node = jsonNode(path);
        if (node == null || node.isMissingNode()) return null;
        try {
            return MAPPER.convertValue(node, typeRef);
        } catch (Exception e) {
            throw new RuntimeException("[ApiResponse] Cannot convert '" + path + "' to " + typeRef.getType(), e);
        }
    }

    /** Extract JSON array at path as List. */
    public <T> List<T> jsonList(String path, Class<T> elementType) {
        JsonNode node = jsonNode(path);
        if (node == null || !node.isArray()) return List.of();
        try {
            return MAPPER.convertValue(node,
                    MAPPER.getTypeFactory().constructCollectionType(List.class, elementType));
        } catch (Exception e) {
            throw new RuntimeException("[ApiResponse] Cannot convert array '" + path + "' to List<" + elementType.getSimpleName() + ">", e);
        }
    }

    /** Deserialise entire response body to a POJO. */
    public <T> T asObject(Class<T> type) {
        try {
            return MAPPER.readValue(body(), type);
        } catch (Exception e) {
            throw new RuntimeException("[ApiResponse] Cannot deserialise body to " + type.getSimpleName(), e);
        }
    }

    /** Deserialise entire response body using TypeReference (generic types). */
    public <T> T asObject(TypeReference<T> typeRef) {
        try {
            return MAPPER.readValue(body(), typeRef);
        } catch (Exception e) {
            throw new RuntimeException("[ApiResponse] Cannot deserialise body to " + typeRef.getType(), e);
        }
    }

    /** Deserialise entire response body to List. */
    public <T> List<T> asList(Class<T> elementType) {
        try {
            return MAPPER.readValue(body(),
                    MAPPER.getTypeFactory().constructCollectionType(List.class, elementType));
        } catch (Exception e) {
            throw new RuntimeException("[ApiResponse] Cannot deserialise body to List<" + elementType.getSimpleName() + ">", e);
        }
    }

    // ── Fluent assertions ────────────────────────────────────────────────────

    /** Fails the test if status does not match. */
    public ApiResponse assertStatus(int expected) {
        StepLogger.step("Assert API status " + expected);
        if (status() != expected) {
            throw new ApiException(requestMethod, requestUrl, status(), body(),
                    "[ApiResponse] Expected status " + expected + " but got " + status());
        }
        return this;
    }

    /** Fails the test if the response body does not contain the given substring. */
    public ApiResponse assertBodyContains(String substring) {
        StepLogger.step("Assert API body contains '" + substring + "'");
        if (body() == null || !body().contains(substring)) {
            throw new ApiException(requestMethod, requestUrl, status(), body(),
                    "[ApiResponse] Body does not contain: '" + substring + "'");
        }
        return this;
    }

    /** Fails the test if the JSONPath value does not equal expected. */
    public ApiResponse assertJson(String path, Object expected) {
        StepLogger.step("Assert API JSON '" + path + "' = " + expected);
        String actual = json(path);
        String expectedStr = String.valueOf(expected);
        if (!expectedStr.equals(actual)) {
            throw new ApiException(requestMethod, requestUrl, status(), body(),
                    "[ApiResponse] JSON path '" + path + "': expected '" + expectedStr + "' but got '" + actual + "'");
        }
        return this;
    }

    /**
     * Validates the response body against a JSON Schema file on the classpath.
     * Schema files should be placed under {@code src/test/resources/schemas/}.
     *
     * <pre>
     * res.assertStatus(200).assertSchema("schemas/user.json");
     * </pre>
     *
     * @param schemaPath classpath-relative path, e.g. {@code "schemas/user.json"}
     */
    public ApiResponse assertSchema(String schemaPath) {
        StepLogger.step("Assert API schema: " + schemaPath);
        SchemaValidator.validate(body(), schemaPath);
        return this;
    }

    /**
     * Validates this response against an OpenAPI 3.x specification file.
     * Requires the optional swagger-request-validator-core dependency.
     */
    @TestFlyApi(since = "1.2.0")
    public ApiResponse assertOpenApi(String specPath) {
        StepLogger.step("Assert OpenAPI spec: " + specPath);
        OpenApiValidator.validate(this, specPath);
        return this;
    }

    // ── Duration assertions ────────────────────────────────────────────────────

    /** Fails the test if the request took longer than {@code maxMs} milliseconds. */
    public ApiResponse assertDurationLessThan(long maxMs) {
        StepLogger.step("Assert API duration < " + maxMs + "ms");
        if (durationMs > maxMs) {
            throw new ApiException(requestMethod, requestUrl, status(), body(),
                    "[ApiResponse] Request took " + durationMs + "ms, expected < " + maxMs + "ms");
        }
        return this;
    }

    /** Fails the test if the request took longer than the given duration. */
    public ApiResponse assertDurationLessThan(long max, TimeUnit unit) {
        return assertDurationLessThan(unit.toMillis(max));
    }

    // ── Header assertions ──────────────────────────────────────────────────────

    /** Fails the test if the response header does not match the expected value. */
    public ApiResponse assertHeader(String name, String expectedValue) {
        StepLogger.step("Assert API header '" + name + "' = '" + expectedValue + "'");
        String actual = header(name);
        if (!expectedValue.equals(actual)) {
            throw new ApiException(requestMethod, requestUrl, status(), body(),
                    "[ApiResponse] Header '" + name + "': expected '" + expectedValue + "' but got '" + actual + "'");
        }
        return this;
    }

    /** Fails the test if the response header is not present. */
    public ApiResponse assertHeaderPresent(String name) {
        StepLogger.step("Assert API header '" + name + "' is present");
        if (header(name) == null) {
            throw new ApiException(requestMethod, requestUrl, status(), body(),
                    "[ApiResponse] Expected header '" + name + "' to be present");
        }
        return this;
    }

    // ── Body regex assertion ───────────────────────────────────────────────────

    /** Fails the test if the response body does not match the given regex (dotall mode). */
    public ApiResponse assertBodyMatches(String regex) {
        StepLogger.step("Assert API body matches regex '" + regex + "'");
        if (body() == null || !body().matches("(?s).*" + regex + ".*")) {
            throw new ApiException(requestMethod, requestUrl, status(), body(),
                    "[ApiResponse] Body does not match regex: '" + regex + "'");
        }
        return this;
    }

    // ── JSON structure assertions ──────────────────────────────────────────────

    /**
     * Retrieves the {@link JsonNode} at the given path.
     * Useful for custom assertions and inspection.
     */
    public JsonNode jsonNode(String path) {
        try {
            if (parsedBody == null) {
                parsedBody = MAPPER.readTree(body());
            }
            String pointer = toPointer(path);
            return parsedBody.at(pointer);
        } catch (Exception e) {
            throw new RuntimeException("[ApiResponse] Failed to parse JSON body. Body: " + truncate(body(), 200), e);
        }
    }

    /**
     * Asserts that the JSON value at {@code path} satisfies the given {@link java.util.function.Predicate}.
     *
     * <pre>
     * res.assertJson("$.status", node -> node.asText().startsWith("ACT"), "Status should start with ACT");
     * </pre>
     */
    public ApiResponse assertJson(String path, java.util.function.Predicate<JsonNode> predicate, String description) {
        StepLogger.step("Assert API JSON '" + path + "': " + description);
        JsonNode node = jsonNode(path);
        if (node == null || node.isMissingNode() || !predicate.test(node)) {
            throw new ApiException(requestMethod, requestUrl, status(), body(),
                    "[ApiResponse] JSON path '" + path + "' failed predicate check: " + description
                            + " (actual value: " + (node == null ? "null" : node.toString()) + ")");
        }
        return this;
    }

    /** Fails the test if the JSON numeric value is not greater than {@code threshold}. */
    public ApiResponse assertJsonGreaterThan(String path, double threshold) {
        StepLogger.step("Assert API JSON '" + path + "' > " + threshold);
        JsonNode node = jsonNode(path);
        if (node == null || !node.isNumber() || node.asDouble() <= threshold) {
            throw new ApiException(requestMethod, requestUrl, status(), body(),
                    "[ApiResponse] JSON path '" + path + "': expected > " + threshold + " but got "
                            + (node == null ? "null" : node.asText()));
        }
        return this;
    }

    /** Fails the test if the JSON numeric value is not less than {@code threshold}. */
    public ApiResponse assertJsonLessThan(String path, double threshold) {
        StepLogger.step("Assert API JSON '" + path + "' < " + threshold);
        JsonNode node = jsonNode(path);
        if (node == null || !node.isNumber() || node.asDouble() >= threshold) {
            throw new ApiException(requestMethod, requestUrl, status(), body(),
                    "[ApiResponse] JSON path '" + path + "': expected < " + threshold + " but got "
                            + (node == null ? "null" : node.asText()));
        }
        return this;
    }

    /** Fails the test if the string representation of JSON value at {@code path} does not contain {@code fragment}. */
    public ApiResponse assertJsonContains(String path, String fragment) {
        StepLogger.step("Assert API JSON '" + path + "' contains '" + fragment + "'");
        String actual = json(path);
        if (actual == null || !actual.contains(fragment)) {
            throw new ApiException(requestMethod, requestUrl, status(), body(),
                    "[ApiResponse] JSON path '" + path + "': expected to contain '" + fragment + "' but got '" + actual + "'");
        }
        return this;
    }

    /** Fails the test if the boolean value at {@code path} is not true. */
    public ApiResponse assertJsonTrue(String path) {
        StepLogger.step("Assert API JSON '" + path + "' is true");
        JsonNode node = jsonNode(path);
        if (node == null || !node.isBoolean() || !node.asBoolean()) {
            throw new ApiException(requestMethod, requestUrl, status(), body(),
                    "[ApiResponse] JSON path '" + path + "': expected true but got "
                            + (node == null ? "null" : node.asText()));
        }
        return this;
    }

    /** Fails the test if the boolean value at {@code path} is not false. */
    public ApiResponse assertJsonFalse(String path) {
        StepLogger.step("Assert API JSON '" + path + "' is false");
        JsonNode node = jsonNode(path);
        if (node == null || !node.isBoolean() || node.asBoolean()) {
            throw new ApiException(requestMethod, requestUrl, status(), body(),
                    "[ApiResponse] JSON path '" + path + "': expected false but got "
                            + (node == null ? "null" : node.asText()));
        }
        return this;
    }

    /** Fails the test if the JSON array at the given path does not have the expected size. */
    public ApiResponse assertJsonArraySize(String path, int expectedSize) {
        StepLogger.step("Assert API JSON array '" + path + "' size = " + expectedSize);
        JsonNode node = jsonNode(path);
        if (node == null || !node.isArray()) {
            throw new ApiException(requestMethod, requestUrl, status(), body(),
                    "[ApiResponse] '" + path + "' is not an array or does not exist");
        }
        if (node.size() != expectedSize) {
            throw new ApiException(requestMethod, requestUrl, status(), body(),
                    "[ApiResponse] Array '" + path + "': expected size " + expectedSize + " but got " + node.size());
        }
        return this;
    }

    /** Fails the test if the JSON path does not exist in the response. */
    public ApiResponse assertJsonExists(String path) {
        StepLogger.step("Assert API JSON path '" + path + "' exists");
        JsonNode node = jsonNode(path);
        if (node == null || node.isMissingNode()) {
            throw new ApiException(requestMethod, requestUrl, status(), body(),
                    "[ApiResponse] JSON path '" + path + "' does not exist in response body");
        }
        return this;
    }

    /** Fails the test if the JSON path exists but is not null. */
    public ApiResponse assertJsonNull(String path) {
        StepLogger.step("Assert API JSON path '" + path + "' is null");
        JsonNode node = jsonNode(path);
        if (node != null && !node.isNull() && !node.isMissingNode()) {
            throw new ApiException(requestMethod, requestUrl, status(), body(),
                    "[ApiResponse] JSON path '" + path + "': expected null but got '" + node.asText() + "'");
        }
        return this;
    }

    // ── Soft assertions (collect, don't throw immediately) ─────────────────────

    private void softCollect(boolean condition, String message) {
        if (!condition) {
            try {
                Class<?> cls = Class.forName("io.testfly.assertion.SoftAssertions");
                Object collector = cls.getMethod("get").invoke(null);
                collector.getClass().getMethod("that", boolean.class, String.class).invoke(collector, false, message);
            } catch (Exception e) {
                throw new ApiException(requestMethod, requestUrl, status(), body(), message);
            }
        }
    }

    public ApiResponse assertStatusSoft(int expected) {
        softCollect(status() == expected, "[ApiResponse] Expected status " + expected + " but got " + status());
        return this;
    }
    public ApiResponse assertJsonSoft(String path, Object expected) {
        String actual = json(path);
        softCollect(String.valueOf(expected).equals(actual),
                "[ApiResponse] JSON path '" + path + "': expected '" + expected + "' but got '" + actual + "'");
        return this;
    }
    public ApiResponse assertHeaderSoft(String name, String expectedValue) {
        softCollect(expectedValue.equals(header(name)),
                "[ApiResponse] Header '" + name + "': expected '" + expectedValue + "' but got '" + header(name) + "'");
        return this;
    }

    // ── ResponseSpec ───────────────────────────────────────────────────────────

    /** Validate against a reusable response spec. */
    public ApiResponse expect(ApiResponseSpec spec) {
        spec.validate(this);
        return this;
    }



    /**
     * Converts JSONPath notation to Jackson JsonPointer.
     * {@code $.user.id}       → {@code /user/id}
     * {@code $.items[0].name} → {@code /items/0/name}
     */
    private String toPointer(String path) {
        return path.replaceFirst("^\\$", "")
                   .replace(".", "/")
                   .replaceAll("\\[(\\d+)]", "/$1")
                   .replaceFirst("^([^/])", "/$1");
    }

    private String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) + "..." : s;
    }

    @Override
    public String toString() {
        return "ApiResponse{status=" + status() + ", durationMs=" + durationMs + "}";
    }
}
