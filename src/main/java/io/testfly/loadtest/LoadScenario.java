package io.testfly.loadtest;

import io.testfly.api.TestFlyApi;
import io.testfly.test.support.LoadTestSupport;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Fluent builder for defining a load-test scenario.
 *
 * <p>
 * A scenario consists of configuration (users, ramp-up, hold, cooldown),
 * one or more {@link LoadStep steps}, optional data feeders, and think-time
 * between requests. Call {@link #run()} to execute and obtain assertions.
 *
 * <pre>
 * loadScenario("Checkout Flow")
 *         .users(500)
 *         .rampUp(Duration.ofSeconds(30))
 *         .hold(Duration.ofMinutes(2))
 *         .step("Login")
 *         .post("/api/auth/login")
 *         .body(Map.of("user", "${username}", "pass", "${password}"))
 *         .extract("token", "$.accessToken")
 *         .step("Add to Cart")
 *         .post("/api/cart")
 *         .header("Authorization", "Bearer ${token}")
 *         .check(status().is(201))
 *         .feed(LoadTestFeeder.csv("users.csv"))
 *         .thinkTime(500, 2000)
 *         .run()
 *         .assertP95Below(500)
 *         .assertErrorRateBelow(0.01);
 * </pre>
 *
 * <p>
 * Instances are created via {@link LoadTestSupport#load(String)} or
 * {@link LoadTestSupport#loadScenario(String)} — not directly.
 */
@TestFlyApi(since = "1.1.0")
public final class LoadScenario {

    private final String name;
    private final List<LoadStep> steps = new ArrayList<>();

    private int users = -1;
    private Duration rampUp;
    private Duration hold;
    private Duration cooldown;
    private String engine;
    private String baseUrl;
    private LoadTestFeeder feeder;
    private long thinkTimeMinMs = -1;
    private long thinkTimeMaxMs = -1;
    private Duration thinkTimeFixed;

    LoadScenario(String name) {
        this.name = name;
    }

    /** Creates a single-step scenario targeting the given path (GET). */
    public static LoadScenario single(String path) {
        LoadScenario s = new LoadScenario(path);
        s.steps.add(new LoadStep(path).get(path));
        return s;
    }

    /** Creates a named, empty scenario — add steps via {@link #step(String)}. */
    public static LoadScenario named(String name) {
        return new LoadScenario(name);
    }

    // ── Configuration ────────────────────────────────────────────────────

    /** Sets the number of concurrent virtual users. */
    public LoadScenario users(int users) {
        this.users = users;
        return this;
    }

    /** Sets the ramp-up duration — time to reach full user count. */
    public LoadScenario rampUp(Duration duration) {
        this.rampUp = duration;
        return this;
    }

    /** Sets the hold duration — how long all users stay active. */
    public LoadScenario hold(Duration duration) {
        this.hold = duration;
        return this;
    }

    /** Sets the cooldown duration — gradual user removal after hold. */
    public LoadScenario cooldown(Duration duration) {
        this.cooldown = duration;
        return this;
    }

    /**
     * Overrides the engine for this scenario ({@code "gatling"}, {@code "jdk"},
     * {@code "auto"}).
     */
    public LoadScenario engine(String engine) {
        this.engine = engine;
        return this;
    }

    /** Overrides the base URL for this scenario. */
    public LoadScenario baseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
        return this;
    }

    // ── Steps ────────────────────────────────────────────────────────────

    /** Begins a new named step in the scenario. */
    public LoadStep step(String name) {
        LoadStep step = new LoadStep(name);
        step.setParent(this);
        steps.add(step);
        return step;
    }

    /** Shortcut: adds a single GET step and returns this scenario for chaining. */
    public LoadScenario get(String path) {
        steps.add(new LoadStep(path).get(path));
        return this;
    }

    /** Shortcut: adds a single POST step and returns this scenario for chaining. */
    public LoadScenario post(String path) {
        steps.add(new LoadStep(path).post(path));
        return this;
    }

    /** Shortcut: adds a single PUT step and returns this scenario for chaining. */
    public LoadScenario put(String path) {
        steps.add(new LoadStep(path).put(path));
        return this;
    }

    /**
     * Shortcut: adds a single DELETE step and returns this scenario for chaining.
     */
    public LoadScenario delete(String path) {
        steps.add(new LoadStep(path).delete(path));
        return this;
    }

    /**
     * Shortcut: adds a single PATCH step and returns this scenario for chaining.
     */
    public LoadScenario patch(String path) {
        steps.add(new LoadStep(path).patch(path));
        return this;
    }

    // ── Data & Timing ────────────────────────────────────────────────────

    /** Attaches a data feeder (CSV, JSON, random, etc.). */
    public LoadScenario feed(LoadTestFeeder feeder) {
        this.feeder = feeder;
        return this;
    }

    /**
     * Attaches a CSV file feeder. Shortcut for
     * {@code feed(LoadTestFeeder.csv(path))}.
     */
    public LoadScenario feedCsv(String path) {
        this.feeder = LoadTestFeeder.csv(path);
        return this;
    }

    /**
     * Attaches a JSON file feeder. Shortcut for
     * {@code feed(LoadTestFeeder.json(path))}.
     */
    public LoadScenario feedJson(String path) {
        this.feeder = LoadTestFeeder.json(path);
        return this;
    }

    /** Sets a random think-time between requests (milliseconds range). */
    public LoadScenario thinkTime(long minMs, long maxMs) {
        this.thinkTimeMinMs = minMs;
        this.thinkTimeMaxMs = maxMs;
        this.thinkTimeFixed = null;
        return this;
    }

    /** Sets a fixed think-time between requests. */
    public LoadScenario thinkTime(Duration fixed) {
        this.thinkTimeFixed = fixed;
        this.thinkTimeMinMs = -1;
        this.thinkTimeMaxMs = -1;
        return this;
    }

    // ── Execution ────────────────────────────────────────────────────────

    /**
     * Executes the load-test scenario and returns a fluent assertion handle.
     *
     * <p>
     * Delegates to {@link LoadTestRunner} which selects the engine
     * (Gatling or JDK fallback), runs the simulation, and collects metrics.
     */
    public LoadTestAssert run() {
        return LoadTestRunner.run(this);
    }

    // ── Shortcut assertions (single-step scenarios) ──────────────────────

    /**
     * Runs the scenario and asserts all responses returned the given status code.
     */
    public LoadTestAssert assertStatus(int expected) {
        return run().assertNoStatus(expected == 200 ? 500 : expected);
    }

    /**
     * Runs the scenario and asserts p95 latency is below the given threshold (ms).
     */
    public LoadTestAssert assertP95Below(double ms) {
        return run().assertP95Below(ms);
    }

    /**
     * Runs the scenario and asserts throughput is above the given value (req/sec).
     */
    public LoadTestAssert assertThroughputAbove(double rps) {
        return run().assertThroughputAbove(rps);
    }

    /**
     * Runs the scenario and asserts error rate is below the given value (0.0–1.0).
     */
    public LoadTestAssert assertErrorRateBelow(double rate) {
        return run().assertErrorRateBelow(rate);
    }

    // ── Accessors (used by runner/engine and tests) ──────────────────────

    public String name() {
        return name;
    }

    public int users() {
        return users;
    }

    public Duration rampUp() {
        return rampUp;
    }

    public Duration hold() {
        return hold;
    }

    public Duration cooldown() {
        return cooldown;
    }

    public String engine() {
        return engine;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public List<LoadStep> steps() {
        return Collections.unmodifiableList(steps);
    }

    public LoadTestFeeder feeder() {
        return feeder;
    }

    public long thinkTimeMinMs() {
        return thinkTimeMinMs;
    }

    public long thinkTimeMaxMs() {
        return thinkTimeMaxMs;
    }

    public Duration thinkTimeFixed() {
        return thinkTimeFixed;
    }
}
