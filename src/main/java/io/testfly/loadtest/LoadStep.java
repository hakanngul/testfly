package io.testfly.loadtest;

import io.testfly.api.TestFlyApi;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A single HTTP request step within a {@link LoadScenario}.
 *
 * <p>
 * Created via {@link LoadScenario#step(String)} or the shortcut methods
 * ({@link LoadScenario#get(String)}, {@link LoadScenario#post(String)}, etc.).
 *
 * <pre>
 * .step("Login")
 *     .post("/api/auth/login")
 *     .body(Map.of("user", "${username}", "pass", "${password}"))
 *     .extract("token", "$.accessToken")
 *     .check(status().is(200))
 * </pre>
 */
@TestFlyApi(since = "1.1.0")
public final class LoadStep {

    private final String name;
    private String method = "GET";
    private String path;
    private final Map<String, String> headers = new LinkedHashMap<>();
    private final Map<String, Object> queryParams = new LinkedHashMap<>();
    private final Map<String, String> formParams = new LinkedHashMap<>();
    private Object body;
    private final List<CheckCondition> checks = new ArrayList<>();
    private final Map<String, String> extractions = new LinkedHashMap<>();

    public LoadStep(String name) {
        this.name = name;
    }

    // ── HTTP Methods ─────────────────────────────────────────────────────

    public LoadStep get(String path) {
        this.method = "GET";
        this.path = path;
        return this;
    }

    public LoadStep post(String path) {
        this.method = "POST";
        this.path = path;
        return this;
    }

    public LoadStep put(String path) {
        this.method = "PUT";
        this.path = path;
        return this;
    }

    public LoadStep delete(String path) {
        this.method = "DELETE";
        this.path = path;
        return this;
    }

    public LoadStep patch(String path) {
        this.method = "PATCH";
        this.path = path;
        return this;
    }

    // ── Request Configuration ────────────────────────────────────────────

    public LoadStep header(String name, String value) {
        headers.put(name, value);
        return this;
    }

    public LoadStep body(Object body) {
        this.body = body;
        return this;
    }

    public LoadStep body(String json) {
        this.body = json;
        return this;
    }

    public LoadStep queryParam(String name, Object value) {
        queryParams.put(name, value);
        return this;
    }

    public LoadStep formParam(String name, String value) {
        formParams.put(name, value);
        return this;
    }

    // ── Checks & Extractions ─────────────────────────────────────────────

    /** Adds a response check (e.g. {@code status().is(200)}). */
    public LoadStep check(CheckCondition condition) {
        checks.add(condition);
        return this;
    }

    /**
     * Extracts a value from the response JSON via JSONPath and stores it
     * as a variable for subsequent steps (e.g.
     * {@code extract("token", "$.accessToken")}).
     */
    public LoadStep extract(String variable, String jsonPath) {
        extractions.put(variable, jsonPath);
        return this;
    }

    // ── Navigation ───────────────────────────────────────────────────────

    /**
     * Returns to the parent scenario to add another step or configure execution.
     */
    public LoadScenario and() {
        return parentScenario;
    }

    /** Executes the entire scenario (shortcut for {@code and().run()}). */
    public LoadTestAssert run() {
        return parentScenario.run();
    }

    // ── Parent link (set by LoadScenario.step()) ─────────────────────────

    private LoadScenario parentScenario;

    void setParent(LoadScenario parent) {
        this.parentScenario = parent;
    }

    // ── Accessors (used by engine and tests) ─────────────────────────────

    public String name() {
        return name;
    }

    public String method() {
        return method;
    }

    public String path() {
        return path;
    }

    public Map<String, String> headers() {
        return headers;
    }

    public Map<String, Object> queryParams() {
        return queryParams;
    }

    public Map<String, String> formParams() {
        return formParams;
    }

    public Object body() {
        return body;
    }

    public List<CheckCondition> checks() {
        return checks;
    }

    public Map<String, String> extractions() {
        return extractions;
    }

    // ── Check condition helpers ──────────────────────────────────────────

    /** Creates a status-code check builder: {@code status().is(200)}. */
    public static CheckCondition.Builder status() {
        return CheckCondition.statusBuilder();
    }

    /** Creates a JSONPath existence check: {@code jsonPath("$.id").exists()}. */
    public static CheckCondition.Builder jsonPath(String path) {
        return CheckCondition.jsonPathBuilder(path);
    }
}
