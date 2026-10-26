package io.testfly.unit.loadtest;

import io.testfly.loadtest.LoadTestMetrics;
import io.testfly.loadtest.internal.GatlingResultsParser;
import io.testfly.loadtest.internal.JdkLoadEngine;
import io.testfly.loadtest.internal.PercentileCalculator;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.testng.Assert.*;

/**
 * ADR 0004 decision 4 (Phase 2): the metrics sidecar {@code schemaVersion 2} adds a top-level
 * {@code "scenarios": {"<scenario>": ["<request name>", ...]}} map; request names are
 * {@code "<scenario> :: <step>"} when a simulation has more than one scenario. From one sidecar the parser
 * builds per-scenario metrics (that scenario's step entries, its redirect hops matched by the
 * {@code "<scenario> :: "} prefix, step metrics keyed by the unprefixed step name) and global metrics (all
 * step entries). Throughput per scenario is that scenario's total / run duration.
 *
 * <p>The multi-scenario entry point does not exist yet. It is reached through {@link SimulationApi#parseSimulation},
 * which assumes an internal static {@code GatlingResultsParser.parseSimulation(String runDir, String
 * simulationName)} returning an object with {@code global()} and {@code scenario(String)} (both
 * {@link LoadTestMetrics}). The single-scenario {@link GatlingResultsParser#parse} is called directly: it must
 * accept schema 1 and 2.
 *
 * <p>Expected percentiles follow {@link PercentileCalculator#linearInterpolation} (rank = p/100 * (n-1)) on the
 * expanded histograms; each hand-computed value is double-checked against it.
 */
@Test(singleThreaded = true)
public class GatlingSimulationSidecarParserTest {

    private static final String SIDECAR = GatlingResultsParser.METRICS_SIDECAR_FILE;
    private static final int NO_RESPONSE = JdkLoadEngine.TRANSPORT_ERROR_STATUS;

    /**
     * Two scenarios over 4 s; both have a step called {@code home}.
     * <ul>
     *   <li>Browse :: home: 5 attempts, 4 OK, 1 KO, statuses 200x4 + 500x1, latencies 10,10,20,20,50</li>
     *   <li>Browse :: login: 3 OK, statuses 302x3, latencies 30,30,30</li>
     *   <li>"Browse :: login Redirect 1": hop only, statuses 200x3, latency 400x3 (not a step, not a sample)</li>
     *   <li>Buy :: pay: 4 attempts, 2 OK, 2 KO, statuses 201x2 (2 requests got no response), latencies 15,25</li>
     *   <li>Buy :: home: 2 OK, statuses 200x2, latencies 5,5</li>
     * </ul>
     */
    private static final String TWO_SCENARIOS = """
            {"schemaVersion":2,"startMs":1000000,"endMs":1004000,
             "scenarios":{
               "Browse":["Browse :: home","Browse :: login"],
               "Buy":["Buy :: pay","Buy :: home"]
             },
             "requests":{
               "Browse :: home":{"attempts":5,"ok":4,"ko":1,"status":{"200":4,"500":1},"latencyMs":{"10":2,"20":2,"50":1}},
               "Browse :: login":{"attempts":3,"ok":3,"ko":0,"status":{"302":3},"latencyMs":{"30":3}},
               "Browse :: login Redirect 1":{"status":{"200":3},"latencyMs":{"400":3}},
               "Buy :: pay":{"attempts":4,"ok":2,"ko":2,"status":{"201":2},"latencyMs":{"15":1,"25":1}},
               "Buy :: home":{"attempts":2,"ok":2,"ko":0,"status":{"200":2},"latencyMs":{"5":2}}
             }}
            """;

    /** Browse step samples: 10,10,20,20,30,30,30,50. */
    private static final double[] BROWSE = {10, 10, 20, 20, 30, 30, 30, 50};
    /** Buy step samples: 5,5,15,25. */
    private static final double[] BUY = {5, 5, 15, 25};
    /** All step samples: 5,5,10,10,15,20,20,25,30,30,30,50. */
    private static final double[] ALL = {5, 5, 10, 10, 15, 20, 20, 25, 30, 30, 30, 50};

    // ── per-scenario metrics ─────────────────────────────────────────────

    @Test
    public void v2_browse_totalsStatusesPercentilesAndSteps() throws IOException {
        // n = 8, rank = p/100 * 7
        checkHandComputed(BROWSE, 25.0, 30.0, 36.0, 43.0, 48.6);
        withRun(TWO_SCENARIOS, runDir -> {
            LoadTestMetrics m = SimulationApi.parsedScenario(SimulationApi.parseSimulation(runDir.toString(), "Shop"),
                    "Browse");

            assertEquals(m.engine(), "gatling", "result=" + m);
            assertEquals(m.totalRequests(), 8, "Browse total = 5 + 3");
            assertEquals(m.successfulRequests(), 7);
            assertEquals(m.failedRequests(), 1);
            assertEquals(m.errorRate(), 1.0 / 8.0, 1e-9);
            assertEquals(m.durationMs(), 4000L);
            assertEquals(m.throughputRps(), 8.0 / 4.0, 1e-9, "scenario total / run duration");
            assertEquals(m.statusCodes(), Map.of(200, 7L, 500, 1L, 302, 3L),
                    "Browse statuses include its own redirect hop (200x3); statusCodes=" + m.statusCodes());
            assertPercentiles(m, 25.0, 30.0, 36.0, 43.0, 48.6);
            assertEquals(m.minLatencyMs(), 10.0, 1e-9);
            assertEquals(m.maxLatencyMs(), 50.0, 1e-9, "max excludes the 400 ms redirect hop");
            assertEquals(m.meanLatencyMs(), 200.0 / 8.0, 1e-9);
            assertEquals(m.steps().keySet(), Set.of("home", "login"),
                    "step metrics keyed by the unprefixed step name; was " + m.steps().keySet());
            assertStep(m, "home", 5, 4, 1);
            assertStep(m, "login", 3, 3, 0);
            // home samples 10,10,20,20,50: rank .95*4 = 3.8 → 20 + .8*(50-20) = 44
            assertEquals(m.step("home").p95LatencyMs(), 44.0, 1e-9, "Browse home p95 from its own histogram");
            assertEquals(m.step("login").p95LatencyMs(), 30.0, 1e-9, "login p95 ignores its redirect hop");
            assertFalse(m.partial(), "attempts == ok + ko on every Browse step");
        });
    }

    @Test
    public void v2_buy_noResponseIsMinusOne_andHomeIsNotMixedWithBrowse() throws IOException {
        // n = 4, rank = p/100 * 3
        checkHandComputed(BUY, 10.0, 17.5, 22.0, 23.5, 24.7);
        withRun(TWO_SCENARIOS, runDir -> {
            LoadTestMetrics m = SimulationApi.parsedScenario(SimulationApi.parseSimulation(runDir.toString(), "Shop"),
                    "Buy");

            assertEquals(m.totalRequests(), 6, "Buy total = 4 + 2");
            assertEquals(m.successfulRequests(), 4);
            assertEquals(m.failedRequests(), 2);
            assertEquals(m.throughputRps(), 6.0 / 4.0, 1e-9);
            assertEquals(m.statusCodes(), Map.of(201, 2L, 200, 2L, NO_RESPONSE, 2L),
                    "pay: 4 outcomes - 2 responses = 2 without response; statusCodes=" + m.statusCodes());
            assertPercentiles(m, 10.0, 17.5, 22.0, 23.5, 24.7);
            assertEquals(m.minLatencyMs(), 5.0, 1e-9);
            assertEquals(m.maxLatencyMs(), 25.0, 1e-9);
            assertEquals(m.meanLatencyMs(), 50.0 / 4.0, 1e-9);
            assertEquals(m.steps().keySet(), Set.of("pay", "home"), "was " + m.steps().keySet());
            assertStep(m, "pay", 4, 2, 2);
            assertStep(m, "home", 2, 2, 0);
            assertEquals(m.step("home").p95LatencyMs(), 5.0, 1e-9, "Buy home p95 is Buy's own (5 ms)");
            assertFalse(m.partial());
        });
    }

    // ── global metrics ───────────────────────────────────────────────────

    @Test
    public void v2_global_isBuiltFromAllStepEntries() throws IOException {
        // n = 12, rank = p/100 * 11
        checkHandComputed(ALL, 20.0, 30.0, 30.0, 39.0, 47.8);
        withRun(TWO_SCENARIOS, runDir -> {
            LoadTestMetrics g = SimulationApi.parsedGlobal(SimulationApi.parseSimulation(runDir.toString(), "Shop"));

            assertEquals(g.engine(), "gatling", "result=" + g);
            assertEquals(g.totalRequests(), 14, "global total = 8 + 6");
            assertEquals(g.successfulRequests(), 11);
            assertEquals(g.failedRequests(), 3);
            assertEquals(g.errorRate(), 3.0 / 14.0, 1e-9);
            assertEquals(g.durationMs(), 4000L);
            assertEquals(g.throughputRps(), 14.0 / 4.0, 1e-9);
            assertEquals(g.statusCodes(), Map.of(200, 9L, 500, 1L, 302, 3L, 201, 2L, NO_RESPONSE, 2L),
                    "statusCodes=" + g.statusCodes());
            assertPercentiles(g, 20.0, 30.0, 30.0, 39.0, 47.8);
            assertEquals(g.minLatencyMs(), 5.0, 1e-9);
            assertEquals(g.maxLatencyMs(), 50.0, 1e-9, "max excludes the redirect hop");
            assertEquals(g.meanLatencyMs(), 250.0 / 12.0, 1e-9);
            assertFalse(g.partial());
        });
    }

    // ── single-scenario path: schema 1 and 2 ─────────────────────────────

    /** A one-scenario run: unprefixed names, as before Phase 2. */
    private static final String V1_SINGLE = """
            {"schemaVersion":1,"startMs":2000,"endMs":4000,
             "requests":{
               "login":{"attempts":4,"ok":3,"ko":1,"status":{"200":3,"500":1},"latencyMs":{"10":2,"30":2}},
               "login Redirect 1":{"status":{"200":1},"latencyMs":{"900":1}}
             }}
            """;

    private static final String V2_SINGLE = """
            {"schemaVersion":2,"startMs":2000,"endMs":4000,
             "scenarios":{"Solo":["login"]},
             "requests":{
               "login":{"attempts":4,"ok":3,"ko":1,"status":{"200":3,"500":1},"latencyMs":{"10":2,"30":2}},
               "login Redirect 1":{"status":{"200":1},"latencyMs":{"900":1}}
             }}
            """;

    @Test
    public void v1_sidecar_stillParses_throughParse() throws IOException {
        withRun(V1_SINGLE, runDir -> assertSingle(GatlingResultsParser.parse(runDir.toString(), "Solo", 2)));
    }

    @Test
    public void v2_singleScenarioSidecar_parses_throughParse() throws IOException {
        withRun(V2_SINGLE, runDir -> assertSingle(GatlingResultsParser.parse(runDir.toString(), "Solo", 2)));
    }

    @Test
    public void v1_sidecar_parsesThroughParseSimulation_asGlobal() throws IOException {
        withRun(V1_SINGLE, runDir -> {
            LoadTestMetrics g = SimulationApi.parsedGlobal(SimulationApi.parseSimulation(runDir.toString(), "Solo"));
            assertEquals(g.totalRequests(), 4, "result=" + g);
            assertEquals(g.statusCodes(), Map.of(200, 4L, 500, 1L));
            assertEquals(g.p50LatencyMs(), 20.0, 1e-9);
            assertEquals(g.steps().keySet(), Set.of("login"));
        });
    }

    // ── helpers ──────────────────────────────────────────────────────────

    /** login: 4 outcomes, statuses 200x3 + 500 + hop 200x1, samples 10,10,30,30 (p50 = 20, p95 = 30). */
    private static void assertSingle(LoadTestMetrics m) {
        assertEquals(m.engine(), "gatling", "the sidecar must parse as a Gatling result, got " + m);
        assertEquals(m.totalRequests(), 4);
        assertEquals(m.successfulRequests(), 3);
        assertEquals(m.failedRequests(), 1);
        assertEquals(m.users(), 2);
        assertEquals(m.durationMs(), 2000L);
        assertEquals(m.throughputRps(), 2.0, 1e-9);
        assertEquals(m.statusCodes(), Map.of(200, 4L, 500, 1L), "statusCodes=" + m.statusCodes());
        assertEquals(m.p50LatencyMs(), 20.0, 1e-9);
        assertEquals(m.p95LatencyMs(), 30.0, 1e-9);
        assertEquals(m.maxLatencyMs(), 30.0, 1e-9, "the 900 ms hop is not a sample");
        assertEquals(m.steps().keySet(), Set.of("login"));
        assertFalse(m.partial());
    }

    private static void checkHandComputed(double[] sorted, double p50, double p75, double p90, double p95,
            double p99) {
        assertEquals(PercentileCalculator.linearInterpolation(sorted.clone(), 50), p50, 1e-9, "p50 hand value");
        assertEquals(PercentileCalculator.linearInterpolation(sorted.clone(), 75), p75, 1e-9, "p75 hand value");
        assertEquals(PercentileCalculator.linearInterpolation(sorted.clone(), 90), p90, 1e-9, "p90 hand value");
        assertEquals(PercentileCalculator.linearInterpolation(sorted.clone(), 95), p95, 1e-9, "p95 hand value");
        assertEquals(PercentileCalculator.linearInterpolation(sorted.clone(), 99), p99, 1e-9, "p99 hand value");
    }

    private static void assertPercentiles(LoadTestMetrics m, double p50, double p75, double p90, double p95,
            double p99) {
        assertEquals(m.p50LatencyMs(), p50, 1e-9, "p50; " + m);
        assertEquals(m.p75LatencyMs(), p75, 1e-9, "p75; " + m);
        assertEquals(m.p90LatencyMs(), p90, 1e-9, "p90; " + m);
        assertEquals(m.p95LatencyMs(), p95, 1e-9, "p95; " + m);
        assertEquals(m.p99LatencyMs(), p99, 1e-9, "p99; " + m);
    }

    private static void assertStep(LoadTestMetrics m, String name, int total, int ok, int ko) {
        LoadTestMetrics.StepMetrics s = m.step(name);
        assertNotNull(s, "step " + name + " missing; steps=" + m.steps().keySet());
        assertEquals(s.totalRequests(), total, name + " total");
        assertEquals(s.successfulRequests(), ok, name + " ok");
        assertEquals(s.failedRequests(), ko, name + " ko");
    }

    private interface RunCheck {
        void accept(Path runDir) throws IOException;
    }

    /** Writes {@code <run>/testfly-metrics.json} next to a Gatling sim dir without {@code js/}, runs the check. */
    private static void withRun(String sidecarJson, RunCheck check) throws IOException {
        Path base = Files.createTempDirectory("gatling-sim-sidecar");
        try {
            Path runDir = base.resolve("Shop-1792900000000");
            Path simDir = runDir.resolve("testflygatlingsimulation-20261025120000000");
            Files.createDirectories(simDir);
            Files.writeString(simDir.resolve("simulation.log"), "binary since 3.13, never parsed");
            Files.writeString(runDir.resolve(SIDECAR), sidecarJson);
            check.accept(runDir);
        } finally {
            deleteRecursive(base.toFile());
        }
    }

    private static void deleteRecursive(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursive(child);
            }
        }
        file.delete();
    }
}
