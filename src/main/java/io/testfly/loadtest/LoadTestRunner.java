package io.testfly.loadtest;

import io.testfly.api.TestFlyApi;
import io.testfly.loadtest.internal.GatlingBridge;
import io.testfly.loadtest.internal.GatlingEngine;
import io.testfly.loadtest.internal.GatlingResultsParser;
import io.testfly.loadtest.internal.JdkLoadEngine;
import io.testfly.loadtest.internal.LoadScenarioValidator;
import io.testfly.loadtest.internal.SlaEvaluator;
import io.testfly.loadtest.internal.LoadTestEngine;
import io.testfly.steps.StepLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Orchestrates load-test execution — selects the engine, runs the scenario,
 * and collects metrics.
 *
 * <p>
 * Analogous to {@link io.testfly.driver.DriverManager} for WebDriver lifecycle:
 * a static facade that hides engine selection and execution details from the
 * user.
 *
 * <p>
 * Engine selection ({@code loadtest.engine} config):
 * <ul>
 * <li>{@code auto} — Gatling if on classpath, else JDK fallback</li>
 * <li>{@code gatling} — require Gatling; throws if absent</li>
 * <li>{@code jdk} — always use JDK HttpClient + ExecutorService</li>
 * </ul>
 */
@TestFlyApi(since = "1.1.0")
public final class LoadTestRunner {

    private static final ThreadLocal<LoadTestMetrics> LAST_METRICS = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> EXECUTING = ThreadLocal.withInitial(() -> false);
    private static final JdkLoadEngine JDK_ENGINE = new JdkLoadEngine();
    private static final Logger LOGGER = LoggerFactory.getLogger(LoadTestRunner.class);
    private static final AtomicBoolean AUTO_FALLBACK_WARNED = new AtomicBoolean();
    private static final AtomicBoolean COOLDOWN_WARNED = new AtomicBoolean();

    private LoadTestRunner() {
    }

    /**
     * Returns true if a load test is currently actively running on this thread.
     */
    public static boolean isExecuting() {
        return Boolean.TRUE.equals(EXECUTING.get());
    }

    /**
     * Executes the given load-test scenario and returns a fluent assertion handle.
     *
     * @param scenario the scenario to execute
     * @return assertions wrapping the collected metrics
     * @throws IllegalStateException if no engine is available
     */
    public static LoadTestAssert run(LoadScenario scenario) {
        EXECUTING.set(true);
        try {
            // snapshot the SLA once: registration, verdict and evaluation all use the same rules
            List<SlaRule> slaRules = scenario.sla() == null ? null : scenario.sla().rules();
            Class<?> testClass = io.testfly.internal.TestFlyContext.getCurrentTestClass();
            java.lang.reflect.Method testMethod = io.testfly.internal.TestFlyContext.getCurrentTestMethod();
            LoadTestConfig config = LoadTestConfig.resolveFor(scenario, testClass, testMethod);

            LoadTestEngine engine = selectEngine(config);
            String resolved = engine.name();
            if ("auto".equals(config.getEngine())) {
                resolved += "jdk".equals(engine.name())
                        ? " (auto: Gatling not on classpath)"
                        : " (auto: Gatling on classpath)";
                if ("jdk".equals(engine.name()) && AUTO_FALLBACK_WARNED.compareAndSet(false, true)) {
                    LOGGER.warn("[LoadTest] loadtest.engine=auto: Gatling is not on the classpath, "
                            + "falling back to the JDK engine");
                }
            }
            Workload workload = scenario.workload();
            if (workload == null && config.getCooldownSeconds() > 0 && COOLDOWN_WARNED.compareAndSet(false, true)) {
                LOGGER.warn("loadtest cooldown is accepted but not applied by any engine yet (ADR-0003)");
            }

            // Fail fast before any request is sent
            LoadScenarioValidator.validate(scenario, engine.name(), config.getMaxUsers());

            if (workload != null) {
                LOGGER.info("[LoadTest] {}: workload set; users/rampUp/hold/cooldown from @LoadTest or "
                        + "testfly.yml are ignored", scenario.name());
            }
            StepLogger.step("Load Test: " + scenario.name()
                    + (workload != null
                            ? " (workload " + workload + ", engine=" + resolved + ")"
                            : " (" + config.getUsers() + " users, engine=" + resolved + ")"));

            LoadTestMetrics raw = engine instanceof GatlingEngine gatling
                    ? gatling.execute(scenario, config, slaRules == null ? List.of() : slaRules)
                    : engine.execute(scenario, config);
            LoadTestMetrics metrics = withSla(raw, slaRules);

            // record first, so the report holds the run even when its SLA fails
            LAST_METRICS.set(metrics);
            io.testfly.metrics.ExecutionMetrics.recordLoadTest(metrics);

            LoadTestAssert result = new LoadTestAssert(metrics);
            return slaRules != null ? result.assertSlaPassed() : result;
        } finally {
            EXECUTING.remove();
        }
    }

    /**
     * Runs a multi-scenario simulation as one Gatling run (Gatling only) and returns per-scenario and global
     * results. Records each scenario's metrics and one global entry ({@code "<simulation> (global)"}) in the
     * execution metrics; {@link #lastMetrics()} then returns the global metrics.
     *
     * @throws IllegalArgumentException if the simulation is invalid or the resolved engine is not Gatling; no
     *                                  request is sent
     * @throws IllegalStateException    if Gatling is required but missing, or the run fails
     */
    static LoadSimulationResult runSimulation(LoadSimulation simulation) {
        EXECUTING.set(true);
        try {
            // snapshot every SLA once: registration, verdict and evaluation all use the same rules
            List<SlaRule> simulationRules = simulation.sla() == null ? null : simulation.sla().rules();
            Map<LoadScenario, List<SlaRule>> scenarioRules = new IdentityHashMap<>();
            for (LoadScenario scenario : simulation.scenarios()) {
                scenarioRules.put(scenario, scenario.sla() == null ? null : scenario.sla().rules());
            }
            Class<?> testClass = io.testfly.internal.TestFlyContext.getCurrentTestClass();
            java.lang.reflect.Method testMethod = io.testfly.internal.TestFlyContext.getCurrentTestMethod();
            LoadTestConfig base = LoadTestConfig.resolve(testClass, testMethod);

            String requested = simulation.engine() != null ? simulation.engine() : base.getEngine();
            String resolved = resolveSimulationEngine(requested);

            // Resolve each scenario's configuration first (including @LoadTest), so the combined maxUsers cap
            // counts the users the run will really use; then fail fast before any request is sent
            Map<LoadScenario, LoadTestConfig> configs = new IdentityHashMap<>();
            for (LoadScenario scenario : simulation.scenarios()) {
                configs.put(scenario, LoadTestConfig.resolveFor(scenario, testClass, testMethod));
            }
            LoadScenarioValidator.validateSimulation(simulation, resolved, base.getMaxUsers(),
                    s -> s.users() > 0 ? s.users() : configs.get(s).getUsers());
            if (!GatlingBridge.isAvailable()) {
                throw new IllegalStateException(GatlingBridge.missingDependencyMessage());
            }

            List<GatlingEngine.ScenarioRun> runs = new ArrayList<>();
            for (LoadScenario scenario : simulation.scenarios()) {
                runs.add(new GatlingEngine.ScenarioRun(scenario, configs.get(scenario), simulation.baseUrl(),
                        scenarioRules.get(scenario)));
            }
            StepLogger.step("Load Simulation: " + simulation.name() + " (" + runs.size() + " scenarios: "
                    + simulation.scenarios().stream().map(LoadScenario::name).toList() + ", engine=gatling)");

            GatlingResultsParser.SimulationMetrics metrics =
                    new GatlingEngine().executeSimulation(simulation.name(), runs,
                            simulationRules == null ? List.of() : simulationRules);

            // evaluate every SLA, record the scenarios and the aggregate, then give the verdict
            Map<String, LoadTestMetrics> perScenario = new LinkedHashMap<>();
            for (LoadScenario scenario : simulation.scenarios()) {
                LoadTestMetrics m = withSla(metrics.scenario(scenario.name()), scenarioRules.get(scenario));
                perScenario.put(scenario.name(), m);
                io.testfly.metrics.ExecutionMetrics.recordLoadTest(m);
            }
            LoadTestMetrics global = metrics.global();
            if (simulationRules != null) {
                global = withResults(global, SlaEvaluator.evaluateSimulation(simulationRules, global,
                        simulation.scenarios().stream().map(LoadScenario::name).toList()));
            }
            io.testfly.metrics.ExecutionMetrics.recordLoadTestAggregate(global);
            LAST_METRICS.set(global);
            LoadSimulationResult result = new LoadSimulationResult(global, perScenario);

            List<String> failures = new ArrayList<>();
            String globalFailures = LoadTestAssert.slaFailures(global);
            if (globalFailures != null) {
                failures.add(global.scenarioName() + ": " + globalFailures);
            }
            for (LoadTestMetrics m : perScenario.values()) {
                String f = LoadTestAssert.slaFailures(m);
                if (f != null) {
                    failures.add(m.scenarioName() + ": " + f);
                }
            }
            if (!failures.isEmpty()) {
                throw new AssertionError("[LoadTest] Simulation '" + simulation.name() + "': SLA failed: "
                        + String.join(" | ", failures));
            }
            return result;
        } finally {
            EXECUTING.remove();
        }
    }

    /**
     * The metrics with the SLA rules evaluated (step names matched exactly) and attached; unchanged when
     * {@code rules} is {@code null}.
     */
    private static LoadTestMetrics withSla(LoadTestMetrics m, List<SlaRule> rules) {
        return rules == null ? m : withResults(m, SlaEvaluator.evaluate(rules, m));
    }

    private static LoadTestMetrics withResults(LoadTestMetrics m, List<SlaResult> results) {
        return new LoadTestMetrics(m.scenarioName(), m.totalRequests(), m.successfulRequests(), m.failedRequests(),
                m.throughputRps(), m.meanLatencyMs(), m.p50LatencyMs(), m.p90LatencyMs(), m.p95LatencyMs(),
                m.p99LatencyMs(), m.minLatencyMs(), m.maxLatencyMs(), m.errorRate(), m.statusCodes(), m.durationMs(),
                m.users(), m.engine(), m.steps(), m.p75LatencyMs(), m.partial(), results);
    }

    /**
     * {@code "gatling"} if the requested engine runs on Gatling, {@code "jdk"} otherwise (the validator rejects
     * it); {@code "gatling"} without Gatling on the classpath is reported later as a missing dependency.
     */
    private static String resolveSimulationEngine(String requested) {
        if ("gatling".equals(requested)) {
            return "gatling";
        }
        if ("auto".equals(requested)) {
            return GatlingBridge.isAvailable() ? "gatling" : "jdk";
        }
        if ("jdk".equals(requested)) {
            return "jdk";
        }
        throw new IllegalArgumentException(
                "[LoadTest] Unknown engine: '" + requested + "'. Valid values: auto, gatling, jdk");
    }

    /**
     * Returns the metrics from the most recent load-test execution on this thread.
     */
    public static LoadTestMetrics lastMetrics() {
        return LAST_METRICS.get();
    }

    /**
     * Clears the thread-local metrics (called by the framework after each test).
     */
    public static void clearLastMetrics() {
        LAST_METRICS.remove();
    }

    /**
     * Sets the thread-local metrics (internal / test hook).
     */
    public static void setLastMetrics(LoadTestMetrics metrics) {
        if (metrics == null) {
            LAST_METRICS.remove();
        } else {
            LAST_METRICS.set(metrics);
        }
    }

    private static LoadTestEngine selectEngine(LoadTestConfig config) {
        String engine = config.getEngine();

        if ("jdk".equals(engine)) {
            return JDK_ENGINE;
        } else if ("gatling".equals(engine)) {
            if (!GatlingBridge.isAvailable()) {
                throw new IllegalStateException(GatlingBridge.missingDependencyMessage());
            }
            return new GatlingEngine();
        } else if ("auto".equals(engine)) {
            if (GatlingBridge.isAvailable()) {
                return new GatlingEngine();
            }
            return JDK_ENGINE;
        } else {
            throw new IllegalArgumentException(
                    "[LoadTest] Unknown engine: '" + engine + "'. Valid values: auto, gatling, jdk");
        }
    }
}
