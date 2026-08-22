package io.testfly.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.testfly.steps.StepLogger;

import java.net.http.HttpResponse;

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
public class ApiResponse {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpResponse<String> response;
    private final long durationMs;
    private JsonNode parsedBody;

    ApiResponse(HttpResponse<String> response, long durationMs) {
        this.response   = response;
        this.durationMs = durationMs;
    }

    /** HTTP status code. */
    public int status() {
        return response.statusCode();
    }

    /** Raw response body as String. */
    public String body() {
        return response.body();
    }

    /** Duration of the request in milliseconds. */
    public long durationMs() {
        return durationMs;
    }

    /** Response header value, or null if absent. */
    public String header(String name) {
        return response.headers().firstValue(name).orElse(null);
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

    /** Deserialise entire response body to a POJO. */
    public <T> T asObject(Class<T> type) {
        try {
            return MAPPER.readValue(response.body(), type);
        } catch (Exception e) {
            throw new RuntimeException("[ApiResponse] Cannot deserialise body to " + type.getSimpleName(), e);
        }
    }

    // ── Fluent assertions ────────────────────────────────────────────────────

    /** Fails the test if status does not match. */
    public ApiResponse assertStatus(int expected) {
        StepLogger.step("Assert API status " + expected);
        if (response.statusCode() != expected) {
            throw new AssertionError(
                "[ApiResponse] Expected status " + expected +
                " but got " + response.statusCode() +
                ". Body: " + truncate(response.body(), 300));
        }
        return this;
    }

    /** Fails the test if the response body does not contain the given substring. */
    public ApiResponse assertBodyContains(String substring) {
        StepLogger.step("Assert API body contains '" + substring + "'");
        if (!response.body().contains(substring)) {
            throw new AssertionError(
                "[ApiResponse] Body does not contain: '" + substring + "'. " +
                "Body: " + truncate(response.body(), 300));
        }
        return this;
    }

    /** Fails the test if the JSONPath value does not equal expected. */
    public ApiResponse assertJson(String path, Object expected) {
        StepLogger.step("Assert API JSON '" + path + "' = " + expected);
        String actual = json(path);
        String expectedStr = String.valueOf(expected);
        if (!expectedStr.equals(actual)) {
            throw new AssertionError(
                "[ApiResponse] JSON path '" + path + "': expected '" + expectedStr +
                "' but got '" + actual + "'");
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
        SchemaValidator.validate(response.body(), schemaPath);
        return this;
    }

    // ── Duration assertions ────────────────────────────────────────────────────

    /** Fails the test if the request took longer than {@code maxMs} milliseconds. */
    public ApiResponse assertDurationLessThan(long maxMs) {
        StepLogger.step("Assert API duration < " + maxMs + "ms");
        if (durationMs > maxMs) {
            throw new AssertionError(
                "[ApiResponse] Request took " + durationMs + "ms, expected < " + maxMs + "ms");
        }
        return this;
    }

    /** Fails the test if the request took longer than the given duration. */
    public ApiResponse assertDurationLessThan(long max, java.util.concurrent.TimeUnit unit) {
        return assertDurationLessThan(unit.toMillis(max));
    }

    // ── Header assertions ──────────────────────────────────────────────────────

    /** Fails the test if the response header does not match the expected value. */
    public ApiResponse assertHeader(String name, String expectedValue) {
        StepLogger.step("Assert API header '" + name + "' = '" + expectedValue + "'");
        String actual = header(name);
        if (!expectedValue.equals(actual)) {
            throw new AssertionError(
                "[ApiResponse] Header '" + name + "': expected '" + expectedValue
                + "' but got '" + actual + "'");
        }
        return this;
    }

    /** Fails the test if the response header is not present. */
    public ApiResponse assertHeaderPresent(String name) {
        StepLogger.step("Assert API header '" + name + "' is present");
        if (header(name) == null) {
            throw new AssertionError(
                "[ApiResponse] Expected header '" + name + "' to be present");
        }
        return this;
    }

    // ── Body regex assertion ───────────────────────────────────────────────────

    /** Fails the test if the response body does not match the given regex (dotall mode). */
    public ApiResponse assertBodyMatches(String regex) {
        StepLogger.step("Assert API body matches regex '" + regex + "'");
        if (response.body() == null || !response.body().matches("(?s).*" + regex + ".*")) {
            throw new AssertionError(
                "[ApiResponse] Body does not match regex: '" + regex + "'. "
                + "Body: " + truncate(response.body(), 300));
        }
        return this;
    }

    // ── JSON structure assertions ──────────────────────────────────────────────

    /** Fails the test if the JSON array at the given path does not have the expected size. */
    public ApiResponse assertJsonArraySize(String path, int expectedSize) {
        StepLogger.step("Assert API JSON array '" + path + "' size = " + expectedSize);
        JsonNode node = jsonNode(path);
        if (node == null || !node.isArray()) {
            throw new AssertionError(
                "[ApiResponse] '" + path + "' is not an array or does not exist");
        }
        if (node.size() != expectedSize) {
            throw new AssertionError(
                "[ApiResponse] Array '" + path + "': expected size " + expectedSize
                + " but got " + node.size());
        }
        return this;
    }

    /** Fails the test if the JSON path does not exist in the response. */
    public ApiResponse assertJsonExists(String path) {
        StepLogger.step("Assert API JSON path '" + path + "' exists");
        JsonNode node = jsonNode(path);
        if (node == null || node.isMissingNode()) {
            throw new AssertionError(
                "[ApiResponse] JSON path '" + path + "' does not exist in response body");
        }
        return this;
    }

    /** Fails the test if the JSON path exists but is not null. */
    public ApiResponse assertJsonNull(String path) {
        StepLogger.step("Assert API JSON path '" + path + "' is null");
        JsonNode node = jsonNode(path);
        if (node != null && !node.isNull() && !node.isMissingNode()) {
            throw new AssertionError(
                "[ApiResponse] JSON path '" + path + "': expected null but got '" + node.asText() + "'");
        }
        return this;
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private JsonNode jsonNode(String path) {
        try {
            if (parsedBody == null) {
                parsedBody = MAPPER.readTree(response.body());
            }
            String pointer = toPointer(path);
            return parsedBody.at(pointer);
        } catch (Exception e) {
            throw new RuntimeException("[ApiResponse] Failed to parse JSON body. Body: " + truncate(response.body(), 200), e);
        }
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
