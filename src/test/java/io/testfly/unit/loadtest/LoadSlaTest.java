package io.testfly.unit.loadtest;

import io.testfly.loadtest.LoadScenario;
import io.testfly.loadtest.LoadTestAssert;
import io.testfly.loadtest.LoadTestMetrics;
import io.testfly.loadtest.Workload;
import io.testfly.metrics.ExecutionMetrics;
import org.testng.annotations.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static io.testfly.unit.loadtest.WorkloadApi.atOnce;
import static io.testfly.unit.loadtest.WorkloadApi.open;
import static org.testng.Assert.*;

/**
 * Load-test SLAs of ADR 0004 decision 5 (Phase 3), below the engines: the {@code LoadSla} builder, TestFly's own
 * evaluation ({@code SlaEvaluator.evaluate(LoadSla, LoadTestMetrics)}), {@code LoadTestAssert.assertSlaPassed()},
 * the {@code "sla"} entry of {@link ExecutionMetrics#loadTestMetricsToMap}, simulation step-name validation and
 * Gatling's exit code 2.
 *
 * <p>Written before the API existed (red first): every SLA call goes through {@link SlaApi}. No test here runs a
 * load test, opens a socket or writes a report file; the tests share no mutable state and are parallel-safe.
 */
public class LoadSlaTest {

    private static final String BASE = "http://127.0.0.1:9";

    // ── builder ──────────────────────────────────────────────────────────

    @Test
    public void builder_keepsDeclarationOrder_acrossGlobalAndStepScopes() {
        Object sla = SlaApi.sla();
        sla = SlaApi.p95Below(sla, 500);
        sla = SlaApi.stepP99Below(SlaApi.step(sla, "login"), 800);
        sla = SlaApi.errorRateBelow(sla, 0.01);
        sla = SlaApi.stepErrorRateBelow(SlaApi.step(sla, "pay"), 0.05);
        sla = SlaApi.throughputAbove(sla, 20);

        List<?> rules = SlaApi.rules(sla);

        assertEquals(rules, List.of(
                SlaApi.rule("global", "P95", "BELOW", 500),
                SlaApi.rule("login", "P99", "BELOW", 800),
                SlaApi.rule("global", "ERROR_RATE", "BELOW", 0.01),
                SlaApi.rule("pay", "ERROR_RATE", "BELOW", 0.05),
                SlaApi.rule("global", "THROUGHPUT", "ABOVE", 20)),
                "rules in declaration order; step rules scoped by the step name, the others 'global'");
    }

    @Test
    public void builder_everyMethod_mapsToItsMetricAndComparison() {
        Object sla = SlaApi.sla();
        sla = SlaApi.p50Below(sla, 1);
        sla = SlaApi.p75Below(sla, 2);
        sla = SlaApi.p90Below(sla, 3);
        sla = SlaApi.p95Below(sla, 4);
        sla = SlaApi.p99Below(sla, 5);
        sla = SlaApi.meanBelow(sla, 6);
        sla = SlaApi.maxBelow(sla, 7);
        sla = SlaApi.errorRateBelow(sla, 0.5);
        sla = SlaApi.throughputAbove(sla, 9);
        sla = SlaApi.stepP95Below(SlaApi.step(sla, "s"), 10);

        List<String> shape = new ArrayList<>();
        for (Object r : SlaApi.rules(sla)) {
            shape.add(SlaApi.scope(r) + " " + SlaApi.metric(r) + " " + SlaApi.comparison(r) + " "
                    + SlaApi.threshold(r));
        }
        assertEquals(shape, List.of(
                "global P50 BELOW 1.0", "global P75 BELOW 2.0", "global P90 BELOW 3.0", "global P95 BELOW 4.0",
                "global P99 BELOW 5.0", "global MEAN BELOW 6.0", "global MAX BELOW 7.0",
                "global ERROR_RATE BELOW 0.5", "global THROUGHPUT ABOVE 9.0", "s P95 BELOW 10.0"));
    }

    @Test
    public void rules_isAnUnmodifiableView() {
        Object sla = SlaApi.p95Below(SlaApi.sla(), 100);
        List<?> rules = SlaApi.rules(sla);
        @SuppressWarnings("unchecked")
        List<Object> raw = (List<Object>) rules;
        Object extra = SlaApi.rule("global", "P50", "BELOW", 1);

        expectThrows(UnsupportedOperationException.class, () -> raw.add(extra));
        expectThrows(UnsupportedOperationException.class, () -> raw.remove(0));
        expectThrows(UnsupportedOperationException.class, raw::clear);
        assertEquals(SlaApi.rules(sla), List.of(SlaApi.rule("global", "P95", "BELOW", 100)),
                "a caller cannot change the declared rules through rules()");
    }

    @Test
    public void stepSla_returnsTheParentLoadSla() {
        Object sla = SlaApi.sla();
        Object stepSla = SlaApi.step(sla, "checkout");
        assertEquals(stepSla.getClass().getName(), SlaApi.STEP_SLA, "step(..) returns a StepSla");

        Object back = SlaApi.stepP95Below(stepSla, 300);

        assertEquals(back.getClass().getName(), SlaApi.LOAD_SLA, "StepSla methods return the LoadSla");
        assertEquals(SlaApi.rules(back), List.of(SlaApi.rule("checkout", "P95", "BELOW", 300)));
    }

    @Test
    public void slaRule_isAValueRecord_withTheSpecifiedEnums() {
        assertTrue(SlaApi.type(SlaApi.SLA_RULE).isRecord(), "SlaRule is a record");
        assertTrue(SlaApi.type(SlaApi.SLA_RESULT).isRecord(), "SlaResult is a record");
        assertEquals(SlaApi.metricNames(),
                List.of("P50", "P75", "P90", "P95", "P99", "MEAN", "MAX", "ERROR_RATE", "THROUGHPUT"));
        assertEquals(SlaApi.comparisonNames(), List.of("BELOW", "ABOVE"));
        assertEquals(SlaApi.rule("a", "P95", "BELOW", 1), SlaApi.rule("a", "P95", "BELOW", 1));
        assertNotEquals(SlaApi.rule("a", "P95", "BELOW", 1), SlaApi.rule("b", "P95", "BELOW", 1));
    }

    @Test
    public void loadTestSupport_sla_isADefaultMethod() {
        assertTrue(SlaApi.slaIsDefaultMethodOnSupport(),
                "LoadTestSupport.sla() must be a default method (binary compatibility)");
        Object sla = SlaApi.sla(new io.testfly.test.support.LoadTestSupport() { });
        assertEquals(sla.getClass().getName(), SlaApi.LOAD_SLA);
        assertTrue(SlaApi.rules(sla).isEmpty(), "a new SLA has no rules");
    }

    @Test
    public void loadScenario_sla_isFluent_andReadable() {
        LoadScenario scenario = LoadScenario.named("Attach").baseUrl(BASE).step("s").get("/s").and();
        assertNull(SlaApi.scenarioSlaOf(scenario), "no SLA until one is attached");
        Object sla = SlaApi.p95Below(SlaApi.sla(), 100);

        assertSame(SlaApi.scenarioSla(scenario, sla), scenario, "LoadScenario.sla(LoadSla) is fluent");
        assertSame(SlaApi.scenarioSlaOf(scenario), sla);
    }

    // ── evaluation: BELOW / ABOVE strictness ─────────────────────────────

    @Test
    public void below_passesOnlyStrictlyBelowTheThreshold() {
        LoadTestMetrics m = metrics(false); // p95 = 40

        List<?> results = SlaApi.evaluate(slaOf(s -> {
            s = SlaApi.p95Below(s, 40);       // equal: fails
            s = SlaApi.p95Below(s, 40.0001);  // just above: passes
            s = SlaApi.p95Below(s, 39.9999);  // just below: fails
            return SlaApi.errorRateBelow(s, 0.02); // equal to errorRate 0.02: fails
        }), m);

        assertEquals(results.size(), 4, "one result per rule");
        assertResult(results.get(0), false, 40);
        assertResult(results.get(1), true, 40);
        assertResult(results.get(2), false, 40);
        assertResult(results.get(3), false, 0.02);
        assertNull(SlaApi.reason(results.get(1)), "a passed rule has no reason");
        for (int i : new int[] {0, 2, 3}) {
            String reason = SlaApi.reason(results.get(i));
            assertNotNull(reason, "a failed rule has a reason");
            assertNotEquals(reason, "not measured");
            assertNotEquals(reason, "metrics are partial");
        }
    }

    @Test
    public void above_passesOnlyStrictlyAboveTheThreshold() {
        LoadTestMetrics m = metrics(false); // throughput = 100

        List<?> results = SlaApi.evaluate(slaOf(s -> {
            s = SlaApi.throughputAbove(s, 100);      // equal: fails
            s = SlaApi.throughputAbove(s, 99.9999);  // just below: passes
            return SlaApi.throughputAbove(s, 100.0001); // just above: fails
        }), m);

        assertResult(results.get(0), false, 100);
        assertResult(results.get(1), true, 100);
        assertResult(results.get(2), false, 100);
    }

    @Test
    public void evaluate_readsEachMetricFromItsOwnComponent_inDeclarationOrder() {
        LoadTestMetrics m = metrics(false);

        List<?> results = SlaApi.evaluate(slaOf(s -> {
            s = SlaApi.throughputAbove(s, 1);
            s = SlaApi.maxBelow(s, 1000);
            s = SlaApi.meanBelow(s, 1000);
            s = SlaApi.p99Below(s, 1000);
            s = SlaApi.p95Below(s, 1000);
            s = SlaApi.p90Below(s, 1000);
            s = SlaApi.p75Below(s, 1000);
            s = SlaApi.p50Below(s, 1000);
            s = SlaApi.errorRateBelow(s, 0.5);
            s = SlaApi.stepP95Below(SlaApi.step(s, "login"), 1000);
            return SlaApi.stepErrorRateBelow(SlaApi.step(s, "login"), 0.5);
        }), m);

        double[] expected = {100, 60, 15, 50, 40, 30, 20, 10, 0.02, 70, 0.05};
        assertEquals(results.size(), expected.length);
        for (int i = 0; i < expected.length; i++) {
            assertResult(results.get(i), true, expected[i]);
        }
        assertEquals(SlaApi.scope(SlaApi.resultRule(results.get(9))), "login");
        assertEquals(SlaApi.metric(SlaApi.resultRule(results.get(9))), "P95");
    }

    // ── evaluation: failures other than the threshold ────────────────────

    @Test
    public void unmeasuredValue_failsAsNotMeasured() {
        LoadTestMetrics m = new LoadTestMetrics("NaN", 10, 10, 0, Double.NaN, 15, 10, Double.NaN, 40, 50, 1, 60, 0.0,
                Map.of(200, 10L), 1000, 1, "jdk",
                Map.of("s", new LoadTestMetrics.StepMetrics("s", 10, 10, 0, Double.NaN, 0.0)), 20);

        List<?> results = SlaApi.evaluate(slaOf(s -> {
            s = SlaApi.p90Below(s, 1_000_000);
            s = SlaApi.throughputAbove(s, 0);
            return SlaApi.stepP95Below(SlaApi.step(s, "s"), 1_000_000);
        }), m);

        for (Object r : results) {
            assertFalse(SlaApi.passed(r), "an unmeasured value never passes: " + r);
            assertTrue(Double.isNaN(SlaApi.actual(r)), "actual stays NaN: " + r);
            assertEquals(SlaApi.reason(r), "not measured");
        }
    }

    @Test
    public void partialMetrics_failEveryRule() {
        LoadTestMetrics m = metrics(true);

        List<?> results = SlaApi.evaluate(slaOf(s -> {
            s = SlaApi.p95Below(s, 1_000_000);
            s = SlaApi.errorRateBelow(s, 1.0);
            s = SlaApi.throughputAbove(s, 0);
            s = SlaApi.stepP95Below(SlaApi.step(s, "login"), 1_000_000);
            return SlaApi.stepErrorRateBelow(SlaApi.step(s, "absent"), 1.0);
        }), m);

        assertEquals(results.size(), 5);
        for (Object r : results) {
            assertFalse(SlaApi.passed(r), "partial metrics fail every rule, even a generous one: " + r);
            assertEquals(SlaApi.reason(r), "metrics are partial");
        }
    }

    @Test
    public void missingStep_failsAsStepNotFound() {
        LoadTestMetrics m = metrics(false);

        List<?> results = SlaApi.evaluate(slaOf(s -> {
            s = SlaApi.stepP95Below(SlaApi.step(s, "nope"), 1_000_000);
            return SlaApi.stepErrorRateBelow(SlaApi.step(s, "nope"), 1.0);
        }), m);

        assertEquals(results.size(), 2, "one result per rule: " + results);
        for (Object r : results) {
            assertFalse(SlaApi.passed(r), r.toString());
            assertTrue(Double.isNaN(SlaApi.actual(r)), "no actual for a missing step: " + r);
            assertEquals(SlaApi.reason(r), "step not found");
        }
    }

    @Test
    public void emptySla_evaluatesToNoResult() {
        assertEquals(SlaApi.evaluate(SlaApi.sla(), metrics(false)), List.of());
    }

    // ── LoadTestMetrics / LoadTestAssert ─────────────────────────────────

    @Test
    public void previousConstructors_haveNoSlaResults() {
        LoadTestMetrics withPartial = metrics(false);
        LoadTestMetrics pre11 = new LoadTestMetrics("Old", 1, 1, 0, 1, 1, 1, 1, 1, 1, 1, 1, 0.0, Map.of(200, 1L),
                1, 1, "jdk", Map.of());

        assertEquals(SlaApi.slaResults(withPartial), List.of(), "constructor without slaResults → empty list");
        assertEquals(SlaApi.slaResults(pre11), List.of(), "pre-1.1.0 constructor → empty list");
        assertEquals(SlaApi.slaResults(LoadTestMetrics.empty("E")), List.of(), "empty(..) → empty list");
    }

    @Test
    public void assertSlaPassed_listsEveryFailedRule() {
        Object p95 = SlaApi.rule("global", "P95", "BELOW", 200);
        Object ko = SlaApi.rule("checkout", "ERROR_RATE", "BELOW", 0.01);
        Object ok = SlaApi.rule("search", "P95", "BELOW", 900);
        LoadTestMetrics m = SlaApi.withSlaResults(metrics(false), List.of(
                SlaApi.result(p95, 250, false, "250.0 is not below 200.0"),
                SlaApi.result(ok, 120, true, null),
                SlaApi.result(ko, Double.NaN, false, "not measured")));
        LoadTestAssert a = new LoadTestAssert(m);

        assertEquals(SlaApi.slaResults(a), SlaApi.slaResults(m), "LoadTestAssert.slaResults() exposes them");
        AssertionError e = expectThrows(AssertionError.class, () -> SlaApi.assertSlaPassed(a));

        String msg = e.getMessage();
        assertNotNull(msg);
        String lower = msg.toLowerCase(Locale.ROOT);
        assertTrue(msg.contains("global") && lower.contains("p95") && msg.contains("200") && msg.contains("250"),
                "the p95 failure needs scope, metric, threshold and actual: " + msg);
        assertTrue(msg.contains("checkout") && lower.contains("error") && msg.contains("0.01")
                        && msg.contains("not measured"),
                "the checkout failure needs scope, metric, threshold and reason: " + msg);
    }

    @Test
    public void assertSlaPassed_passesWhenEveryRulePassed_orNoSla() {
        Object ok = SlaApi.rule("global", "P95", "BELOW", 900);
        LoadTestAssert passed = new LoadTestAssert(
                SlaApi.withSlaResults(metrics(false), List.of(SlaApi.result(ok, 40, true, null))));
        LoadTestAssert none = new LoadTestAssert(metrics(false));

        assertSame(SlaApi.assertSlaPassed(passed), passed, "assertSlaPassed() is fluent");
        assertSame(SlaApi.assertSlaPassed(none), none, "no SLA declared: nothing to fail");
    }

    // ── ExecutionMetrics map ─────────────────────────────────────────────

    @Test
    public void executionMetricsMap_hasSlaEntries_withTheSpecifiedShape() {
        Object p95 = SlaApi.rule("global", "P95", "BELOW", 200);
        Object ko = SlaApi.rule("checkout", "ERROR_RATE", "BELOW", 0.01);
        Object tput = SlaApi.rule("global", "THROUGHPUT", "ABOVE", 5);
        LoadTestMetrics m = SlaApi.withSlaResults(metrics(false), List.of(
                SlaApi.result(p95, 150, true, null),
                SlaApi.result(ko, Double.NaN, false, "not measured"),
                SlaApi.result(tput, 3.5, false, "3.5 is not above 5.0")));

        Object sla = ExecutionMetrics.loadTestMetricsToMap(m).get("sla");

        assertTrue(sla instanceof List<?>, "\"sla\" is a list, was: " + sla);
        List<?> entries = (List<?>) sla;
        assertEquals(entries.size(), 3, "one entry per result, in order");
        Map<?, ?> first = (Map<?, ?>) entries.get(0);
        Map<?, ?> second = (Map<?, ?>) entries.get(1);
        Map<?, ?> third = (Map<?, ?>) entries.get(2);
        List<String> keys = List.of("scope", "metric", "comparison", "threshold", "actual", "passed", "reason");
        for (Map<?, ?> e : List.of(first, second, third)) {
            assertEquals(new ArrayList<>(e.keySet()), keys, "keys and their order: " + e);
        }

        assertEquals(first.get("scope"), "global");
        assertEquals(first.get("metric"), "P95", "metric as the enum name");
        assertEquals(first.get("comparison"), "BELOW", "comparison as the enum name");
        assertEquals(((Number) first.get("threshold")).doubleValue(), 200.0, 0.0);
        assertEquals(((Number) first.get("actual")).doubleValue(), 150.0, 0.0);
        assertEquals(first.get("passed"), true);
        assertNull(first.get("reason"), "a passed rule has a null reason");

        assertEquals(second.get("scope"), "checkout");
        assertEquals(second.get("metric"), "ERROR_RATE");
        assertNull(second.get("actual"), "NaN actual → null (never coerced to 0)");
        assertEquals(second.get("passed"), false);
        assertEquals(second.get("reason"), "not measured");

        assertEquals(third.get("comparison"), "ABOVE");
        assertEquals(((Number) third.get("actual")).doubleValue(), 3.5, 0.0);
        assertEquals(third.get("reason"), "3.5 is not above 5.0");
    }

    @Test
    public void executionMetricsMap_withoutSla_hasAnEmptySlaList() {
        assertEquals(ExecutionMetrics.loadTestMetricsToMap(metrics(false)).get("sla"), List.of(),
                "\"sla\" is always present; empty when no SLA was declared");
    }

    // ── simulations: step names ──────────────────────────────────────────

    @Test
    public void simulation_ambiguousBareStepName_isRejectedAtValidation() {
        Object sim = twoScenariosWithStepHome("SlaAmbiguous");
        SlaApi.simulationSla(sim, SlaApi.stepP95Below(SlaApi.step(SlaApi.sla(), "home"), 100));

        IllegalArgumentException e = expectThrows(IllegalArgumentException.class,
                () -> SimulationApi.validate(sim, "gatling", 1000));

        String msg = String.valueOf(e.getMessage());
        assertTrue(msg.contains("'home'") && msg.toLowerCase(Locale.ROOT).contains("ambiguous"),
                "message must name the ambiguous step 'home': " + msg);
        assertTrue(msg.contains("Alpha :: home") && msg.contains("Beta :: home"),
                "message must name the qualified candidates: " + msg);
    }

    @Test
    public void simulation_qualifiedOrUniqueBareStepName_passesValidation() {
        Object qualified = twoScenariosWithStepHome("SlaQualified");
        SlaApi.simulationSla(qualified,
                SlaApi.stepP95Below(SlaApi.step(SlaApi.sla(), "Alpha :: home"), 100));
        SimulationApi.validate(qualified, "gatling", 1000);

        Object unique = SimulationApi.named("SlaUnique");
        SimulationApi.baseUrl(unique, BASE);
        SimulationApi.scenario(unique, LoadScenario.named("Alpha").step("home").get("/a").and()
                .workload((Workload) open(atOnce(1))));
        SimulationApi.scenario(unique, LoadScenario.named("Beta").step("buy").get("/b").and()
                .workload((Workload) open(atOnce(1))));
        SlaApi.simulationSla(unique, SlaApi.stepErrorRateBelow(SlaApi.step(SlaApi.sla(), "buy"), 0.1));
        SimulationApi.validate(unique, "gatling", 1000);
    }

    // ── Gatling exit code 2 ──────────────────────────────────────────────

    @Test
    public void gatlingExitCode2_isAnAssertionFailure_notACrash() throws Exception {
        Path dir = Files.createTempDirectory("sla-exit2");
        try {
            File log = dir.resolve("gatling-subprocess.log").toFile();
            Files.writeString(log.toPath(), "Global: 95th percentile of response time is less than 1.0 : false\n");

            assertTrue(SlaApi.checkExitCode(2, "SlaExit2", log, true),
                    "exit code 2 with native assertions registered → Gatling assertions failed");
            assertFalse(SlaApi.checkExitCode(0, "SlaExit0", log, true), "exit code 0 → nothing failed");
            assertFalse(SlaApi.checkExitCode(0, "SlaExit0", log, false), "exit code 0 → nothing failed");
        } finally {
            deleteRecursive(dir.toFile());
        }
    }

    @Test
    public void gatlingExitCode2_withoutNativeAssertions_isACrash() throws Exception {
        Path dir = Files.createTempDirectory("sla-exit2-none");
        try {
            File log = dir.resolve("gatling-subprocess.log").toFile();
            Files.writeString(log.toPath(), "boom\n");
            // no native assertion was registered, so 2 cannot mean "assertions failed"
            IllegalStateException e = expectThrows(IllegalStateException.class,
                    () -> SlaApi.checkExitCode(2, "SlaExit2NoAssertions", log, false));
            assertTrue(e.getMessage().contains("exit code 2"), e.getMessage());
        } finally {
            deleteRecursive(dir.toFile());
        }
    }

    @Test
    public void stepNotFound_takesPrecedenceOverNotMeasured_andPartialOverBoth() {
        // step "s" exists with an unmeasured p95; step "absent" does not exist
        LoadTestMetrics m = new LoadTestMetrics("Prec", 10, 10, 0, 5, 15, 10, 30, 40, 50, 1, 60, 0.0,
                Map.of(200, 10L), 1000, 1, "jdk",
                Map.of("s", new LoadTestMetrics.StepMetrics("s", 10, 10, 0, Double.NaN, 0.0)), 20);
        List<?> results = SlaApi.evaluate(slaOf(s -> {
            s = SlaApi.stepP95Below(SlaApi.step(s, "absent"), 1_000_000);
            return SlaApi.stepP95Below(SlaApi.step(s, "s"), 1_000_000);
        }), m);

        assertEquals(results.size(), 2, results.toString());
        assertEquals(SlaApi.reason(results.get(0)), "step not found", "a missing step is not 'not measured'");
        assertEquals(SlaApi.reason(results.get(1)), "not measured", "a present step with a NaN value");

        LoadTestMetrics partial = SlaApi.withSlaResults(new LoadTestMetrics("Prec", 10, 10, 0, 5, 15, 10, 30, 40, 50,
                1, 60, 0.0, Map.of(200, 10L), 1000, 1, "jdk", m.steps(), 20, true), List.of());
        for (Object r : SlaApi.evaluate(slaOf(s -> {
            s = SlaApi.stepP95Below(SlaApi.step(s, "absent"), 1_000_000);
            return SlaApi.stepP95Below(SlaApi.step(s, "s"), 1_000_000);
        }), partial)) {
            assertEquals(SlaApi.reason(r), "metrics are partial", "partial wins over both: " + r);
        }
    }

    @Test
    public void gatlingOtherNonZeroExitCodes_remainCrashes() throws Exception {
        Path dir = Files.createTempDirectory("sla-exit1");
        try {
            File log = dir.resolve("gatling-subprocess.log").toFile();
            Files.writeString(log.toPath(), "Exception in thread \"main\" boom\n");
            for (int code : new int[] {1, 3, 137}) {
                IllegalStateException e = expectThrows(IllegalStateException.class,
                        () -> SlaApi.checkExitCode(code, "SlaExit" + code, log));
                assertTrue(e.getMessage().contains("exit code " + code),
                        "the crash message keeps the exit code: " + e.getMessage());
            }
        } finally {
            deleteRecursive(dir.toFile());
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────

    /**
     * p50 10, p75 20, p90 30, p95 40, p99 50, mean 15, max 60, error rate 0.02, throughput 100; step "login" with
     * p95 70 and error rate 0.05.
     */
    private static LoadTestMetrics metrics(boolean partial) {
        return new LoadTestMetrics("SlaUnit", 100, 98, 2, 100, 15, 10, 30, 40, 50, 1, 60, 0.02,
                Map.of(200, 98L, 500, 2L), 1000, 5, "jdk",
                Map.of("login", new LoadTestMetrics.StepMetrics("login", 100, 95, 5, 70, 0.05)), 20, partial);
    }

    private static Object slaOf(java.util.function.UnaryOperator<Object> rules) {
        return rules.apply(SlaApi.sla());
    }

    private static void assertResult(Object result, boolean passed, double actual) {
        assertEquals(SlaApi.passed(result), passed, "passed: " + result);
        assertEquals(SlaApi.actual(result), actual, 1e-9, "actual: " + result);
    }

    private static Object twoScenariosWithStepHome(String name) {
        Object sim = SimulationApi.named(name);
        SimulationApi.baseUrl(sim, BASE);
        SimulationApi.scenario(sim, LoadScenario.named("Alpha").step("home").get("/a").and()
                .workload((Workload) open(atOnce(1))));
        SimulationApi.scenario(sim, LoadScenario.named("Beta").step("home").get("/b").and()
                .workload((Workload) open(atOnce(1))));
        return sim;
    }

    private static void deleteRecursive(File f) {
        File[] children = f.listFiles();
        if (children != null) {
            for (File c : children) {
                deleteRecursive(c);
            }
        }
        f.delete();
    }
}
