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
    /** Raw response bytes; {@code null} only when a legacy response carried a null body. */
    private final byte[] rawBody;
    private final java.nio.charset.Charset bodyCharset;
    /** Lazily decoded {@link #rawBody}; pre-filled when the body arrived as text. */
    private volatile String decodedBody;
    private final java.net.http.HttpHeaders responseHeaders;
    private final ApiRequest request;
    private final boolean synthetic;
    private final long durationMs;
    private final String requestMethod;
    private final String requestUrl;
    private volatile JsonNode parsedBody;

    ApiResponse(HttpResponse<String> response, long durationMs) {
        this(response, durationMs, null, null);
    }

    ApiResponse(HttpResponse<String> response, long durationMs, String requestMethod, String requestUrl) {
        this.statusCode = response.statusCode();
        this.responseHeaders = response.headers();
        this.bodyCharset = charsetOf(responseHeaders);
        String text = response.body();
        this.rawBody = text == null ? null : text.getBytes(bodyCharset);
        this.decodedBody = text;
        this.request = null;
        this.synthetic = false;
        this.durationMs    = durationMs;
        this.requestMethod = requestMethod;
        this.requestUrl    = requestUrl;
    }

    private ApiResponse(int status, byte[] body, java.net.http.HttpHeaders headers, long durationMs,
                        String requestMethod, String requestUrl) {
        this.statusCode = status;
        this.responseHeaders = headers;
        this.bodyCharset = charsetOf(headers);
        this.rawBody = body == null ? new byte[0] : body;
        this.request = null;
        this.synthetic = false;
        this.durationMs = durationMs;
        this.requestMethod = requestMethod;
        this.requestUrl = requestUrl;
    }

    /** Wraps a byte-oriented transport response; the body is decoded lazily with the response charset. */
    static ApiResponse fromBytes(HttpResponse<byte[]> response, long durationMs, String requestMethod, String requestUrl) {
        return new ApiResponse(response.statusCode(), response.body(), response.headers(), durationMs,
                requestMethod, requestUrl);
    }

    private ApiResponse(Builder b) {
        request = java.util.Objects.requireNonNull(b.request, "request");
        if (b.status < 100 || b.status > 599) throw new IllegalArgumentException("HTTP status must be 100..599");
        if (b.duration < 0) throw new IllegalArgumentException("duration must be non-negative");
        statusCode = b.status;
        responseHeaders = java.net.http.HttpHeaders.of(b.headers, (k, v) -> true);
        bodyCharset = charsetOf(responseHeaders);
        if (b.bodyBytes != null) {
            rawBody = b.bodyBytes.clone();
            decodedBody = b.bodyText;
        } else {
            rawBody = java.util.Objects.requireNonNull(b.bodyText, "body").getBytes(bodyCharset);
            decodedBody = b.bodyText;
        }
        durationMs = b.duration;
        requestMethod = request.method();
        requestUrl = request.uri().toString();
        synthetic = b.synthetic;
    }

    @TestFlyApi(since = "1.1.0")
    public static Builder builder() { return new Builder(); }

    @TestFlyApi(since = "1.1.0")
    public Builder newBuilder() {
        Builder b = new Builder().status(status()).durationMs(durationMs);
        // Copy the raw bytes, never a re-encoded string: binary bodies must survive withRequest()/asSynthetic().
        b.bodyBytes = rawBody == null ? new byte[0] : rawBody.clone();
        b.bodyText = rawBody == null ? "" : decodedBody;
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

    ApiResponse asSynthetic(ApiRequest request) {
        Builder builder = newBuilder(); builder.synthetic = true; return builder.request(request).build();
    }

    @TestFlyApi(since = "1.1.0")
    public static final class Builder {
        private ApiRequest request;
        private int status;
        private String bodyText = "";
        private byte[] bodyBytes;
        private long duration;
        private boolean synthetic = true;
        private final java.util.Map<String, List<String>> headers = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        private Builder() {}
        public Builder request(ApiRequest value) { request = java.util.Objects.requireNonNull(value); return this; }
        public Builder status(int value) { status = value; return this; }
        /** Text body, encoded with the charset of the {@code Content-Type} header (UTF-8 by default). */
        public Builder body(String value) { bodyText = java.util.Objects.requireNonNull(value); bodyBytes = null; return this; }
        /** Raw body bytes, kept as-is. */
        @TestFlyApi(since = "1.1.0")
        public Builder body(byte[] value) { bodyBytes = java.util.Objects.requireNonNull(value).clone(); bodyText = null; return this; }
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

    /** Response body decoded with the response charset ({@code Content-Type; charset=...}, UTF-8 by default). */
    public String body() {
        String text = decodedBody;
        if (text == null && rawBody != null) {
            text = new String(rawBody, bodyCharset);
            decodedBody = text;
        }
        return text;
    }

    /** Raw response body bytes (a copy). Use for binary payloads such as files or images. */
    @TestFlyApi(since = "1.1.0")
    public byte[] asBytes() {
        return rawBody == null ? new byte[0] : rawBody.clone();
    }

    /** Saves the raw response body to {@code target}, creating parent directories. Returns {@code target}. */
    @TestFlyApi(since = "1.1.0")
    public java.nio.file.Path saveTo(java.nio.file.Path target) {
        try {
            java.nio.file.Path parent = target.toAbsolutePath().getParent();
            if (parent != null) java.nio.file.Files.createDirectories(parent);
            return java.nio.file.Files.write(target, asBytes());
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException("[ApiResponse] Cannot save body to " + target, e);
        }
    }

    /** All response headers (case-insensitive names, all values). */
    @TestFlyApi(since = "1.1.0")
    public java.util.Map<String, List<String>> headers() {
        return responseHeaders.map();
    }

    /** All values of a response header, or an empty list. */
    @TestFlyApi(since = "1.1.0")
    public List<String> headerValues(String name) {
        return responseHeaders.allValues(name);
    }

    /** The {@code Content-Type} header, or {@code null}. */
    @TestFlyApi(since = "1.1.0")
    public String contentType() {
        return header("Content-Type");
    }

    /** Charset used to decode {@link #body()}. */
    @TestFlyApi(since = "1.1.0")
    public java.nio.charset.Charset charset() {
        return bodyCharset;
    }

    /** True for 2xx statuses. */
    @TestFlyApi(since = "1.1.0")
    public boolean isSuccessful() {
        return statusCode >= 200 && statusCode < 300;
    }

    /** Pretty-printed JSON body, or the plain body when it is not JSON. */
    @TestFlyApi(since = "1.1.0")
    public String prettyBody() {
        String text = body();
        if (text == null || text.isBlank()) return text;
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(MAPPER.readTree(text));
        } catch (Exception e) {
            return text;
        }
    }

    private static java.nio.charset.Charset charsetOf(java.net.http.HttpHeaders headers) {
        if (headers == null) return java.nio.charset.StandardCharsets.UTF_8;
        String type = headers.firstValue("Content-Type").orElse(null);
        if (type == null) return java.nio.charset.StandardCharsets.UTF_8;
        for (String part : type.split(";")) {
            String p = part.trim();
            if (p.regionMatches(true, 0, "charset=", 0, 8)) {
                String name = p.substring(8).trim().replace("\"", "").replace("'", "");
                try {
                    return java.nio.charset.Charset.forName(name);
                } catch (Exception ignored) {
                    return java.nio.charset.StandardCharsets.UTF_8;
                }
            }
        }
        return java.nio.charset.StandardCharsets.UTF_8;
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
        SchemaValidator.validate(this, schemaPath);
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
     *
     * <p>Path syntax: {@code $} (root), {@code $.a.b}, {@code $.items[0]}, {@code $['key.with.dots']},
     * {@code $.items[*].id} / {@code $.map.*}. A wildcard path returns an array of all matches.
     * Filter expressions are not supported. A missing path returns a missing node.
     *
     * @throws IllegalArgumentException if the path syntax is invalid or unsupported
     */
    public JsonNode jsonNode(String path) {
        JsonNode root = parsedBody;
        if (root == null) {
            try {
                root = MAPPER.readTree(body());
            } catch (Exception e) {
                throw new RuntimeException("[ApiResponse] Failed to parse JSON body. Body: " + truncate(body(), 200), e);
            }
            parsedBody = root;
        }
        return JsonPathResolver.resolve(root, path);
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

    // ── Status family assertions ───────────────────────────────────────────────

    /** Fails the test unless the status is one of {@code expected}. */
    @TestFlyApi(since = "1.1.0")
    public ApiResponse assertStatusIn(int... expected) {
        String list = java.util.Arrays.toString(expected);
        StepLogger.step("Assert API status in " + list);
        for (int code : expected) if (code == statusCode) return this;
        throw new ApiException(requestMethod, requestUrl, status(), body(),
                "[ApiResponse] Expected status in " + list + " but got " + status());
    }

    /** Fails the test unless {@code fromInclusive <= status <= toInclusive}. */
    @TestFlyApi(since = "1.1.0")
    public ApiResponse assertStatusRange(int fromInclusive, int toInclusive) {
        StepLogger.step("Assert API status in " + fromInclusive + ".." + toInclusive);
        if (statusCode < fromInclusive || statusCode > toInclusive) {
            throw new ApiException(requestMethod, requestUrl, status(), body(),
                    "[ApiResponse] Expected status in " + fromInclusive + ".." + toInclusive + " but got " + status());
        }
        return this;
    }

    /** Fails the test unless the status is 2xx. */
    @TestFlyApi(since = "1.1.0")
    public ApiResponse assertSuccessful() {
        return assertStatusRange(200, 299);
    }

    // ── Semantic JSON assertions ───────────────────────────────────────────────

    /**
     * Fails the test unless the whole body is semantically equal to {@code expectedJson}:
     * object key order and number formatting ({@code 1} vs {@code 1.0}) are ignored, array order is not.
     */
    @TestFlyApi(since = "1.1.0")
    public ApiResponse assertJsonEquals(String expectedJson) {
        return assertJsonEquals(expectedJson, false);
    }

    /** Like {@link #assertJsonEquals(String)}; with {@code ignoreArrayOrder} arrays are compared as multisets. */
    @TestFlyApi(since = "1.1.0")
    public ApiResponse assertJsonEquals(String expectedJson, boolean ignoreArrayOrder) {
        return compareJson("$", jsonNode("$"), expectedJson, ignoreArrayOrder);
    }

    /** Fails the test unless the value at {@code path} is semantically equal to {@code expectedJson}. */
    @TestFlyApi(since = "1.1.0")
    public ApiResponse assertJsonEquals(String path, String expectedJson) {
        return assertJsonEquals(path, expectedJson, false);
    }

    /** Path variant of {@link #assertJsonEquals(String, boolean)}. */
    @TestFlyApi(since = "1.1.0")
    public ApiResponse assertJsonEquals(String path, String expectedJson, boolean ignoreArrayOrder) {
        return compareJson(path, jsonNode(path), expectedJson, ignoreArrayOrder);
    }

    /**
     * Fails the test unless the array at {@code path} contains {@code expected}. Scalars are matched by
     * text ({@code 42} matches {@code "42"} and {@code 42}); a {@link JsonNode} or JSON object/array string
     * is matched semantically.
     */
    @TestFlyApi(since = "1.1.0")
    public ApiResponse assertJsonArrayContains(String path, Object expected) {
        StepLogger.step("Assert API JSON array '" + path + "' contains " + expected);
        JsonNode node = jsonNode(path);
        if (node == null || !node.isArray()) {
            throw new ApiException(requestMethod, requestUrl, status(), body(),
                    "[ApiResponse] '" + path + "' is not an array or does not exist");
        }
        JsonNode expectedNode = expected instanceof JsonNode n ? n : structuredOrNull(expected);
        for (JsonNode element : node) {
            if (expectedNode != null ? jsonDifference(expectedNode, element, "", false) == null
                    : element.isValueNode() && element.asText().equals(String.valueOf(expected))) {
                return this;
            }
        }
        throw new ApiException(requestMethod, requestUrl, status(), body(),
                "[ApiResponse] Array '" + path + "' does not contain " + expected + " (actual: " + truncate(node.toString(), 200) + ")");
    }

    private static JsonNode structuredOrNull(Object expected) {
        if (expected instanceof String s) {
            String t = s.trim();
            if (t.startsWith("{") || t.startsWith("[")) {
                try { return MAPPER.readTree(t); } catch (Exception ignored) { return null; }
            }
            return null;
        }
        if (expected == null || expected instanceof Number || expected instanceof Boolean || expected instanceof CharSequence) return null;
        return MAPPER.valueToTree(expected);
    }

    private ApiResponse compareJson(String path, JsonNode actual, String expectedJson, boolean ignoreArrayOrder) {
        StepLogger.step("Assert API JSON '" + path + "' equals expected document"
                + (ignoreArrayOrder ? " (ignoring array order)" : ""));
        JsonNode expected;
        try {
            expected = MAPPER.readTree(expectedJson);
        } catch (Exception e) {
            throw new IllegalArgumentException("[ApiResponse] Expected value is not valid JSON: " + truncate(expectedJson, 200), e);
        }
        if (actual == null || actual.isMissingNode()) {
            throw new ApiException(requestMethod, requestUrl, status(), body(),
                    "[ApiResponse] JSON path '" + path + "' does not exist in response body");
        }
        String difference = jsonDifference(expected, actual, "", ignoreArrayOrder);
        if (difference != null) {
            throw new ApiException(requestMethod, requestUrl, status(), body(),
                    "[ApiResponse] JSON at '" + path + "' differs: " + difference);
        }
        return this;
    }

    /** Returns a description of the first difference, or {@code null} when equal. */
    private static String jsonDifference(JsonNode expected, JsonNode actual, String at, boolean ignoreArrayOrder) {
        String where = at.isEmpty() ? "/" : at;
        if (expected.isNumber() && actual.isNumber()) {
            return expected.decimalValue().compareTo(actual.decimalValue()) == 0 ? null
                    : "at " + where + ": expected " + expected + " but got " + actual;
        }
        if (expected.getNodeType() != actual.getNodeType()) {
            return "at " + where + ": expected " + expected.getNodeType().name().toLowerCase() + " " + abbreviate(expected)
                    + " but got " + actual.getNodeType().name().toLowerCase() + " " + abbreviate(actual);
        }
        if (expected.isObject()) {
            java.util.Iterator<String> names = expected.fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                if (!actual.has(name)) return "at " + where + ": missing field '" + name + "'";
                String d = jsonDifference(expected.get(name), actual.get(name), at + "/" + name, ignoreArrayOrder);
                if (d != null) return d;
            }
            java.util.Iterator<String> actualNames = actual.fieldNames();
            while (actualNames.hasNext()) {
                String name = actualNames.next();
                if (!expected.has(name)) return "at " + where + ": unexpected field '" + name + "'";
            }
            return null;
        }
        if (expected.isArray()) {
            if (expected.size() != actual.size()) {
                return "at " + where + ": expected array of size " + expected.size() + " but got " + actual.size();
            }
            if (!ignoreArrayOrder) {
                for (int i = 0; i < expected.size(); i++) {
                    String d = jsonDifference(expected.get(i), actual.get(i), at + "/" + i, false);
                    if (d != null) return d;
                }
                return null;
            }
            boolean[] used = new boolean[actual.size()];
            outer:
            for (int i = 0; i < expected.size(); i++) {
                for (int j = 0; j < actual.size(); j++) {
                    if (!used[j] && jsonDifference(expected.get(i), actual.get(j), at + "/" + j, true) == null) {
                        used[j] = true;
                        continue outer;
                    }
                }
                return "at " + where + ": no element matches expected " + abbreviate(expected.get(i));
            }
            return null;
        }
        return expected.equals(actual) ? null : "at " + where + ": expected " + abbreviate(expected) + " but got " + abbreviate(actual);
    }

    private static String abbreviate(JsonNode node) {
        String s = node.toString();
        return s.length() > 120 ? s.substring(0, 120) + "..." : s;
    }

    // ── Soft assertions (collect, don't throw immediately) ─────────────────────

    private void softCollect(boolean condition, String message) {
        if (!condition) {
            io.testfly.assertion.SoftAssertions.get().that(false, message);
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




    private String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) + "..." : s;
    }

    @Override
    public String toString() {
        return "ApiResponse{status=" + status() + ", durationMs=" + durationMs + "}";
    }
}
