package io.testfly.unit.loadtest;

import io.testfly.loadtest.LoadScenario;
import io.testfly.loadtest.LoadSimulation;
import io.testfly.loadtest.LoadSla;
import io.testfly.loadtest.LoadTestConfig;
import io.testfly.loadtest.LoadTestMetrics;
import io.testfly.loadtest.SlaResult;
import io.testfly.loadtest.SlaRule;
import io.testfly.loadtest.Workload;
import io.testfly.loadtest.OpenStep;
import io.testfly.loadtest.internal.GatlingEngine;
import io.testfly.loadtest.internal.LoadScenarioValidator;
import io.testfly.loadtest.internal.SlaEvaluator;
import org.testng.annotations.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.*;

/**
 * How SLA step scopes resolve (ADR 0004 decision 5): a scenario's own metrics match step names exactly; a
 * simulation's global metrics strip {@code "<scenario> :: "} when there is one scenario, and otherwise compare a bare
 * name with the step part after the FIRST {@code " :: "} of each key (step names may contain {@code " :: "},
 * scenario names may not). The validator's ambiguity check and the native Gatling assertions use the same rule.
 */
public class SlaScopeResolutionTest {

    private static final String BASE = "http://127.0.0.1:9";

    // ── evaluator ────────────────────────────────────────────────────────

    @Test
    public void oneScenarioSimulation_qualifiedScope_resolvesToTheUnprefixedStep() {
        LoadTestMetrics global = metrics(Map.of("buy", step("buy", 0.0)));
        List<SlaResult> r = SlaEvaluator.evaluateSimulation(
                List.of(rule("Buy :: buy"), rule("buy"), rule("Other :: buy")), global, List.of("Buy"));

        assertTrue(r.get(0).passed(), "'Buy :: buy' in the one-scenario simulation 'Buy': " + r.get(0));
        assertTrue(r.get(1).passed(), "the bare name also works: " + r.get(1));
        assertEquals(r.get(2).reason(), "step not found", "another scenario's prefix is not stripped");
    }

    @Test
    public void multiScenario_bareName_comparesTheStepPartAfterTheFirstSeparator() {
        // Alpha has a step literally named "x :: buy"; Beta has "buy"
        LoadTestMetrics global = metrics(Map.of(
                "Alpha :: x :: buy", step("Alpha :: x :: buy", 0.5),
                "Beta :: buy", step("Beta :: buy", 0.0)));
        List<SlaResult> r = SlaEvaluator.evaluateSimulation(
                List.of(rule("buy"), rule("x :: buy"), rule("Alpha :: x :: buy")), global, List.of("Alpha", "Beta"));

        assertEquals(r.get(0).actual(), 0.0, 1e-9, "'buy' is Beta's step, not Alpha's 'x :: buy': " + r.get(0));
        assertEquals(r.get(1).actual(), 0.5, 1e-9, "'x :: buy' is Alpha's step: " + r.get(1));
        assertEquals(r.get(2).actual(), 0.5, 1e-9, "the qualified key matches exactly: " + r.get(2));
        assertEquals(SlaEvaluator.simulationStepKey(List.of("A :: buy", "B :: buy"), "buy", List.of("A", "B")), null,
                "an ambiguous bare name resolves to nothing (the validator rejects it before the run)");
    }

    @Test
    public void scenarioMetrics_matchStepNamesExactly_withoutSuffixMatching() {
        LoadTestMetrics m = metrics(Map.of("x :: buy", step("x :: buy", 0.0)));
        List<SlaResult> r = SlaEvaluator.evaluate(List.of(rule("buy"), rule("x :: buy")), m);

        assertEquals(r.get(0).reason(), "step not found", "'buy' must not match the step 'x :: buy'");
        assertTrue(r.get(1).passed(), r.get(1).toString());
    }

    // ── validator ────────────────────────────────────────────────────────

    @Test
    public void validator_bareName_isNotAmbiguous_withAStepWhoseNameEndsWithIt() {
        LoadSimulation sim = LoadSimulation.named("NoAmbiguity")
                .scenario(scenario("Alpha", "x :: buy"))
                .scenario(scenario("Beta", "buy"))
                .sla(LoadSla.sla().step("buy").errorRateBelow(0.1));
        LoadScenarioValidator.validateSimulation(sim, "gatling", 1000);

        LoadSimulation ambiguous = LoadSimulation.named("Ambiguous")
                .scenario(scenario("Alpha", "buy"))
                .scenario(scenario("Beta", "buy"))
                .sla(LoadSla.sla().step("buy").errorRateBelow(0.1));
        IllegalArgumentException e = expectThrows(IllegalArgumentException.class,
                () -> LoadScenarioValidator.validateSimulation(ambiguous, "gatling", 1000));
        assertTrue(e.getMessage().contains("ambiguous") && e.getMessage().contains("Alpha :: buy")
                && e.getMessage().contains("Beta :: buy"), e.getMessage());
    }

    // ── native Gatling assertions ────────────────────────────────────────

    @Test
    public void nativeAssertions_multiScenario_prefixesSteps_dropsScenarioGlobals_skipsUnknownAndAmbiguous()
            throws Exception {
        LoadScenario alpha = LoadScenario.named("Alpha").baseUrl(BASE)
                .step("home").get("/h").and().step("pay").get("/p").and()
                .workload(Workload.open(OpenStep.atOnce(1)))
                .sla(LoadSla.sla().errorRateBelow(0.5).step("home").p95Below(100).step("zzz").p95Below(1));
        LoadScenario beta = LoadScenario.named("Beta").baseUrl(BASE)
                .step("home").get("/h").and()
                .workload(Workload.open(OpenStep.atOnce(1)));
        List<SlaRule> simulationRules = LoadSla.sla().p95Below(300)
                .step("Alpha :: pay").p99Below(800)
                .step("pay").errorRateBelow(0.1)        // unique bare name → "Alpha :: pay"
                .step("home").errorRateBelow(0.1)       // ambiguous → skipped
                .step("nope").errorRateBelow(0.1)       // unknown → skipped
                .rules();

        List<String> got = nativeAssertions(List.of(alpha, beta), simulationRules);

        assertEquals(got, List.of(
                "global P95 source=null",
                "Alpha :: pay P99 source=null",
                "Alpha :: pay ERROR_RATE source=null",
                "Alpha :: home P95 source=Alpha"),
                "Alpha's global errorRateBelow has no native counterpart in a multi-scenario run");
    }

    @Test
    public void nativeAssertions_oneScenario_qualifiedNameAndScenarioGlobals_areRegistered() throws Exception {
        LoadScenario buy = LoadScenario.named("Buy").baseUrl(BASE)
                .step("buy").get("/b").and()
                .workload(Workload.open(OpenStep.atOnce(1)))
                .sla(LoadSla.sla().p95Below(200));
        List<SlaRule> simulationRules = LoadSla.sla().step("Buy :: buy").errorRateBelow(0.1).rules();

        assertEquals(nativeAssertions(List.of(buy), simulationRules), List.of(
                "buy ERROR_RATE source=null",
                "global P95 source=Buy"));
    }

    // ── helpers ──────────────────────────────────────────────────────────

    /**
     * {@code GatlingRunConfig.nativeAssertions(forSimulation(..), runs, rules)} rendered as
     * {@code "<requestName|global> <metric> source=<source>"} (package-private types, hence reflection).
     */
    private static List<String> nativeAssertions(List<LoadScenario> scenarios, List<SlaRule> simulationRules)
            throws Exception {
        Class<?> cfgType = Class.forName("io.testfly.loadtest.internal.GatlingRunConfig");
        Class<?> scType = Class.forName("io.testfly.loadtest.internal.GatlingRunConfig$ScenarioConfig");
        Constructor<?> scCtor = scType.getDeclaredConstructor(LoadScenario.class, LoadTestConfig.class, String.class);
        scCtor.setAccessible(true);
        List<Object> configs = new ArrayList<>();
        List<GatlingEngine.ScenarioRun> runs = new ArrayList<>();
        for (LoadScenario s : scenarios) {
            LoadTestConfig c = LoadTestConfig.resolveFor(s, null, null);
            configs.add(scCtor.newInstance(s, c, BASE));
            runs.add(new GatlingEngine.ScenarioRun(s, c, BASE));
        }
        Method forSimulation = cfgType.getDeclaredMethod("forSimulation", String.class, String.class, List.class);
        forSimulation.setAccessible(true);
        Object cfg = forSimulation.invoke(null, "Sim", "target/loadtest", configs);
        Method build = cfgType.getDeclaredMethod("nativeAssertions", cfgType, List.class, List.class);
        build.setAccessible(true);
        List<String> out = new ArrayList<>();
        for (Object a : (List<?>) build.invoke(null, cfg, runs, simulationRules)) {
            Object request = field(a, "requestName");
            out.add((request == null ? "global" : request) + " " + field(a, "metric") + " source="
                    + field(a, "source"));
        }
        return out;
    }

    private static Object field(Object target, String name) throws Exception {
        Field f = target.getClass().getField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    private static SlaRule rule(String scope) {
        return new SlaRule(scope, SlaRule.Metric.ERROR_RATE, SlaRule.Comparison.BELOW, 0.6);
    }

    private static LoadTestMetrics.StepMetrics step(String name, double errorRate) {
        return new LoadTestMetrics.StepMetrics(name, 10, 10, 0, 5, errorRate, 6);
    }

    private static LoadTestMetrics metrics(Map<String, LoadTestMetrics.StepMetrics> steps) {
        return new LoadTestMetrics("Scope", 10, 10, 0, 5, 5, 5, 5, 5, 5, 1, 9, 0.0, Map.of(200, 10L), 1000, 1,
                "gatling", new LinkedHashMap<>(steps), 5);
    }

    private static LoadScenario scenario(String name, String step) {
        return LoadScenario.named(name).baseUrl(BASE).step(step).get("/" + name).and()
                .workload(Workload.open(OpenStep.atOnce(1)));
    }
}
