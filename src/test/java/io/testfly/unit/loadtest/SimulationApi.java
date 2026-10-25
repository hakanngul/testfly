package io.testfly.unit.loadtest;

import io.testfly.loadtest.LoadScenario;
import io.testfly.loadtest.LoadSimulation;
import io.testfly.loadtest.LoadSimulationResult;
import io.testfly.loadtest.LoadTestAssert;
import io.testfly.loadtest.LoadTestMetrics;
import io.testfly.loadtest.internal.GatlingResultsParser;
import io.testfly.loadtest.internal.LoadScenarioValidator;
import io.testfly.test.support.LoadTestSupport;

import java.util.List;

/**
 * Test helper for the multi-scenario simulation API of ADR 0004 decision 4 (Phase 2): {@link LoadSimulation},
 * {@link LoadSimulationResult}, {@link LoadTestSupport#loadSimulation(String)}, plus the internal entry points
 * {@link LoadScenarioValidator#validateSimulation} and {@link GatlingResultsParser#parseSimulation(String, String)}.
 *
 * <p>The red tests were written before that API existed and reached it through reflection. Every method is now a
 * direct call; the {@code Object}-typed signatures are kept so the tests stay unchanged.
 */
public final class SimulationApi {

    private SimulationApi() {
    }

    // ── LoadSimulation ───────────────────────────────────────────────────

    /** {@code LoadSimulation.named(name)}. */
    public static Object named(String name) {
        return LoadSimulation.named(name);
    }

    /** {@code support.loadSimulation(name)} (default method of {@link LoadTestSupport}). */
    public static Object loadSimulation(LoadTestSupport support, String name) {
        return support.loadSimulation(name);
    }

    /** {@code true} if {@code LoadTestSupport.loadSimulation(String)} exists and is a default method. */
    public static boolean loadSimulationIsDefaultMethod() {
        try {
            return LoadTestSupport.class.getMethod("loadSimulation", String.class).isDefault();
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    /** {@code simulation.scenario(scenario)}; returns the simulation. */
    public static Object scenario(Object simulation, LoadScenario scenario) {
        return sim(simulation).scenario(scenario);
    }

    /** {@code simulation.baseUrl(baseUrl)}; returns the simulation. */
    public static Object baseUrl(Object simulation, String baseUrl) {
        return sim(simulation).baseUrl(baseUrl);
    }

    /** {@code simulation.engine(engine)}; returns the simulation. */
    public static Object engine(Object simulation, String engine) {
        return sim(simulation).engine(engine);
    }

    /** {@code simulation.run()}; returns the {@code LoadSimulationResult}. */
    public static Object run(Object simulation) {
        return sim(simulation).run();
    }

    public static String name(Object simulation) {
        return sim(simulation).name();
    }

    public static List<LoadScenario> scenarios(Object simulation) {
        return sim(simulation).scenarios();
    }

    public static String baseUrlOf(Object simulation) {
        return sim(simulation).baseUrl();
    }

    public static String engineOf(Object simulation) {
        return sim(simulation).engine();
    }

    // ── LoadSimulationResult ─────────────────────────────────────────────

    public static LoadTestAssert global(Object result) {
        return result(result).global();
    }

    public static LoadTestAssert scenarioAssert(Object result, String name) {
        return result(result).scenario(name);
    }

    public static List<String> scenarioNames(Object result) {
        return result(result).scenarioNames();
    }

    public static LoadTestMetrics globalMetrics(Object result) {
        return result(result).globalMetrics();
    }

    public static LoadTestMetrics scenarioMetrics(Object result, String name) {
        return result(result).scenarioMetrics(name);
    }

    // ── internal entry points ────────────────────────────────────────────

    /** {@code LoadScenarioValidator.validateSimulation(simulation, engineName, maxUsers)}. */
    public static void validate(Object simulation, String engineName, int maxUsers) {
        LoadScenarioValidator.validateSimulation(sim(simulation), engineName, maxUsers);
    }

    /** {@code GatlingResultsParser.parseSimulation(runDir, simulationName)}; returns the parsed result. */
    public static Object parseSimulation(String runDir, String simulationName) {
        return GatlingResultsParser.parseSimulation(runDir, simulationName);
    }

    /** {@code parsed.global()} of the object returned by {@link #parseSimulation}. */
    public static LoadTestMetrics parsedGlobal(Object parsed) {
        return ((GatlingResultsParser.SimulationMetrics) parsed).global();
    }

    /** {@code parsed.scenario(name)} of the object returned by {@link #parseSimulation}. */
    public static LoadTestMetrics parsedScenario(Object parsed, String name) {
        return ((GatlingResultsParser.SimulationMetrics) parsed).scenario(name);
    }

    private static LoadSimulation sim(Object simulation) {
        if (simulation == null) {
            throw new NullPointerException("simulation is null");
        }
        return (LoadSimulation) simulation;
    }

    private static LoadSimulationResult result(Object result) {
        if (result == null) {
            throw new NullPointerException("result is null");
        }
        return (LoadSimulationResult) result;
    }
}
