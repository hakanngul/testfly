package io.testfly.loadtest;

import io.testfly.api.TestFlyApi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Several {@link LoadScenario}s run together as one Gatling simulation (ADR 0004 decision 4), each with its
 * own workload or legacy {@code users/rampUp/hold} shape, steps, feeder and think time.
 *
 * <pre>
 * LoadSimulation.named("Shop")
 *         .baseUrl("https://shop.example")
 *         .scenario(loadScenario("Browse").step("home").get("/").and()
 *                 .workload(Workload.closed(ClosedStep.constantConcurrent(50, Duration.ofMinutes(2)))))
 *         .scenario(loadScenario("Buy").step("pay").post("/pay").and()
 *                 .workload(Workload.open(OpenStep.constantRate(5, Duration.ofMinutes(2)))))
 *         .run()
 *         .scenario("Buy").assertP95Below(300);
 * </pre>
 *
 * <ul>
 *   <li>Requires the Gatling engine; the JDK engine (also {@code auto} without Gatling) is rejected before any
 *       request.</li>
 *   <li>A scenario's own {@code baseUrl} wins over {@link #baseUrl(String)}, which wins over the configured one.</li>
 *   <li>Scenario names must be unique and non-blank, and cannot contain {@code #{}, {@code ${} or
 *       {@code " :: "}: with more than one scenario, request names are {@code "<scenario> :: <step>"}.</li>
 *   <li>{@code loadtest.maxUsers} caps the sum of the closed peaks (legacy scenarios count their users) and,
 *       separately, the sum of the open peak arrival rates.</li>
 * </ul>
 *
 * <p>A mutable builder: configure it on one thread, then call {@link #run()}.
 */
@TestFlyApi(since = "1.1.0")
public final class LoadSimulation {

    private final String name;
    private final List<LoadScenario> scenarios = new ArrayList<>();
    private String baseUrl;
    private String engine;
    private LoadSla sla;

    private LoadSimulation(String name) {
        this.name = name;
    }

    /** Creates an empty simulation; add scenarios with {@link #scenario(LoadScenario)}. */
    @TestFlyApi(since = "1.1.0")
    public static LoadSimulation named(String name) {
        return new LoadSimulation(name);
    }

    /**
     * Adds a scenario; scenarios keep their insertion order.
     *
     * @throws IllegalArgumentException if {@code scenario} is {@code null}
     */
    @TestFlyApi(since = "1.1.0")
    public LoadSimulation scenario(LoadScenario scenario) {
        if (scenario == null) {
            throw new IllegalArgumentException("[LoadTest] Simulation '" + name + "': scenario cannot be null");
        }
        scenarios.add(scenario);
        return this;
    }

    /** Base URL for the scenarios that do not set their own. */
    @TestFlyApi(since = "1.1.0")
    public LoadSimulation baseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
        return this;
    }

    /** Engine for the whole simulation ({@code "gatling"} or {@code "auto"}); the configured one if unset. */
    @TestFlyApi(since = "1.1.0")
    public LoadSimulation engine(String engine) {
        this.engine = engine;
        return this;
    }

    /**
     * Attaches an SLA evaluated against the simulation's global metrics. A step rule names
     * {@code "<scenario> :: <step>"}, or a bare step name that is unique across the scenarios (an ambiguous one is
     * rejected before the run). SLAs set on the scenarios apply to each scenario's own metrics.
     */
    @TestFlyApi(since = "1.1.0")
    public LoadSimulation sla(LoadSla sla) {
        this.sla = sla;
        return this;
    }

    /** The simulation-level SLA, or {@code null}. */
    @TestFlyApi(since = "1.1.0")
    public LoadSla sla() {
        return sla;
    }

    /** Validates, runs all scenarios in one Gatling simulation and returns per-scenario and global results. */
    @TestFlyApi(since = "1.1.0")
    public LoadSimulationResult run() {
        return LoadTestRunner.runSimulation(this);
    }

    @TestFlyApi(since = "1.1.0")
    public String name() {
        return name;
    }

    /** The scenarios in insertion order (unmodifiable). */
    @TestFlyApi(since = "1.1.0")
    public List<LoadScenario> scenarios() {
        return Collections.unmodifiableList(scenarios);
    }

    @TestFlyApi(since = "1.1.0")
    public String baseUrl() {
        return baseUrl;
    }

    @TestFlyApi(since = "1.1.0")
    public String engine() {
        return engine;
    }
}
