package io.testfly.unit.loadtest;

import io.testfly.loadtest.LoadTestAssert;
import io.testfly.loadtest.LoadTestMetrics;
import io.testfly.loadtest.internal.GatlingResultsParser;
import io.testfly.loadtest.internal.JdkLoadEngine;
import io.testfly.loadtest.internal.PercentileCalculator;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.testng.Assert.*;

/**
 * ADR 0004 decision 1 (Phase 1a): {@link GatlingResultsParser} builds {@link LoadTestMetrics} from
 * TestFly's own sidecar {@code testfly-metrics.json} only, without Gatling's {@code js/stats.js}.
 *
 * <p>Every fixture is laid out like a real run: {@code <run>/testfly-metrics.json} next to a Gatling
 * {@code <sim-dir>/} that holds only {@code simulation.log} (no {@code js/}). The spec places the sidecar
 * "next to the Gatling run dir" but also says {@code parse} "reads the sidecar inside the newest run dir",
 * so the same sidecar is also copied into {@code <sim-dir>/}; either reading passes.
 *
 * <p>Expected percentiles follow {@link PercentileCalculator#linearInterpolation} on the expanded
 * histogram; the hand-computed values are double-checked against it.
 */
@Test(singleThreaded = true)
public class GatlingOwnedMetricsParserTest {

    private static final String SIDECAR = "testfly-metrics.json";

    /**
     * Three steps plus one redirect hop.
     * <ul>
     *   <li>login: 6 attempts, 5 OK, 1 KO, statuses 200x5 + 500x1, latencies 10,10,20,20,20,40</li>
     *   <li>search: 4 attempts, 2 OK, 2 KO, statuses 200x2 (2 requests got no response), latencies 15,15</li>
     *   <li>checkout: 3 attempts, 3 OK, statuses 302x3, latencies 30,30,30</li>
     *   <li>"checkout Redirect 1": hop only (statuses 200x3, latency 500x3), not a step, not a latency sample</li>
     * </ul>
     * Merged step samples (11): 10,10,15,15,20,20,20,30,30,30,40.
     */
    private static final String FULL_FIXTURE = """
            {"schemaVersion":1,"startMs":1000000,"endMs":1004000,
             "requests":{
               "login":{"attempts":6,"ok":5,"ko":1,"status":{"200":5,"500":1},"latencyMs":{"10":2,"20":3,"40":1}},
               "search":{"attempts":4,"ok":2,"ko":2,"status":{"200":2},"latencyMs":{"15":2}},
               "checkout":{"attempts":3,"ok":3,"ko":0,"status":{"302":3},"latencyMs":{"30":3}},
               "checkout Redirect 1":{"status":{"200":3},"latencyMs":{"500":3}}
             }}
            """;

    private static final double[] MERGED_SAMPLES = {10, 10, 15, 15, 20, 20, 20, 30, 30, 30, 40};

    @Test
    public void sidecarOnly_totalsRatesAndDuration() throws IOException {
        withRun(FULL_FIXTURE, runDir -> {
            LoadTestMetrics m = GatlingResultsParser.parse(runDir.toString(), "Owned", 7);

            assertEquals(m.engine(), "gatling", "sidecar-only run dir must parse as a Gatling result, got " + m);
            assertEquals(m.scenarioName(), "Owned");
            assertEquals(m.users(), 7);
            assertEquals(m.totalRequests(), 13, "total = sum(ok+ko) over steps (6+4+3)");
            assertEquals(m.successfulRequests(), 10, "successful = sum(ok) (5+2+3)");
            assertEquals(m.failedRequests(), 3, "failed = sum(ko) (1+2+0)");
            assertEquals(m.errorRate(), 3.0 / 13.0, 1e-9);
            assertEquals(m.durationMs(), 4000L, "durationMs = endMs - startMs");
            assertEquals(m.throughputRps(), 13.0 / 4.0, 1e-9, "throughput = total / (durationMs/1000)");
        });
    }

    @Test
    public void sidecarOnly_percentilesFromStepHistogramsOnly() throws IOException {
        double[] sorted = MERGED_SAMPLES.clone();
        // hand-computed: rank = p/100 * (n-1), n = 11 → rank = p/10
        assertEquals(PercentileCalculator.linearInterpolation(sorted, 50), 20.0, 1e-9); // s[5]
        assertEquals(PercentileCalculator.linearInterpolation(sorted, 75), 30.0, 1e-9); // s[7.5] = 30..30
        assertEquals(PercentileCalculator.linearInterpolation(sorted, 90), 30.0, 1e-9); // s[9]
        assertEquals(PercentileCalculator.linearInterpolation(sorted, 95), 35.0, 1e-9); // 30 + .5*(40-30)
        assertEquals(PercentileCalculator.linearInterpolation(sorted, 99), 39.0, 1e-9); // 30 + .9*(40-30)

        withRun(FULL_FIXTURE, runDir -> {
            LoadTestMetrics m = GatlingResultsParser.parse(runDir.toString(), "Owned", 1);

            assertEquals(m.p50LatencyMs(), 20.0, 1e-9, "p50");
            assertEquals(m.p75LatencyMs(), 30.0, 1e-9, "p75");
            assertTrue(LoadTestMetrics.isMeasured(m.p90LatencyMs()), "p90 must be measured from the histogram");
            assertEquals(m.p90LatencyMs(), 30.0, 1e-9, "p90");
            assertEquals(m.p95LatencyMs(), 35.0, 1e-9, "p95");
            assertEquals(m.p99LatencyMs(), 39.0, 1e-9, "p99");
            assertEquals(m.minLatencyMs(), 10.0, 1e-9, "min");
            assertEquals(m.maxLatencyMs(), 40.0, 1e-9, "max excludes the 500 ms redirect hop");
            assertEquals(m.meanLatencyMs(), 240.0 / 11.0, 1e-9, "mean over the 11 step samples");
        });
    }

    @Test
    public void sidecarOnly_stepMetricsExcludeRedirectHops() throws IOException {
        withRun(FULL_FIXTURE, runDir -> {
            LoadTestMetrics m = GatlingResultsParser.parse(runDir.toString(), "Owned", 1);

            assertEquals(m.steps().keySet(), Set.of("login", "search", "checkout"),
                    "only entries with attempts are steps; was " + m.steps().keySet());
            assertStep(m, "login", 6, 5, 1);
            assertStep(m, "search", 4, 2, 2);
            assertStep(m, "checkout", 3, 3, 0);
            // login samples 10,10,20,20,20,40: rank .95*5 = 4.75 → 20 + .75*(40-20) = 35
            assertEquals(m.step("login").p95LatencyMs(), 35.0, 1e-9, "per-step p95 from the step's histogram");
            assertEquals(m.step("checkout").p95LatencyMs(), 30.0, 1e-9, "checkout p95 ignores its redirect hop");
        });
    }

    @Test
    public void sidecarOnly_statusCodesIncludeHopsAndNoResponseAsMinusOne() throws IOException {
        withRun(FULL_FIXTURE, runDir -> {
            LoadTestMetrics m = GatlingResultsParser.parse(runDir.toString(), "Owned", 1);

            // 200: login 5 + search 2 + hop 3; noResponse: search (2+2) - 2 = 2
            assertEquals(m.statusCodes(), Map.of(200, 10L, 500, 1L, 302, 3L,
                    JdkLoadEngine.TRANSPORT_ERROR_STATUS, 2L), "statusCodes=" + m.statusCodes());
            assertTrue(m.statusCodesMeasured());
            assertFalse(isPartial(m), "attempts == ok + ko on every step: not partial");
        });
    }

    @Test
    public void sidecarOnly_noResponsesAtAll_latencyNaN_statusMinusOne() throws IOException {
        String fixture = """
                {"schemaVersion":1,"startMs":2000,"endMs":4000,
                 "requests":{"refused":{"attempts":3,"ok":0,"ko":3,"status":{},"latencyMs":{}}}}
                """;
        withRun(fixture, runDir -> {
            LoadTestMetrics m = GatlingResultsParser.parse(runDir.toString(), "Refused", 1);

            assertEquals(m.totalRequests(), 3, "result=" + m);
            assertEquals(m.failedRequests(), 3);
            assertEquals(m.errorRate(), 1.0, 1e-9);
            assertEquals(m.statusCodes(), Map.of(JdkLoadEngine.TRANSPORT_ERROR_STATUS, 3L));
            assertTrue(Double.isNaN(m.p50LatencyMs()), "no samples → NaN, was " + m.p50LatencyMs());
            assertTrue(Double.isNaN(m.p90LatencyMs()), "no samples → NaN, was " + m.p90LatencyMs());
            assertTrue(Double.isNaN(m.p99LatencyMs()), "no samples → NaN, was " + m.p99LatencyMs());
            assertEquals(m.throughputRps(), 1.5, 1e-9);
        });
    }

    @Test
    public void sidecarOnly_attemptsWithoutOutcome_isPartial_andAssertionsFail() throws IOException {
        // 5 attempts but only 4 recorded outcomes
        String fixture = """
                {"schemaVersion":1,"startMs":0,"endMs":2000,
                 "requests":{"cut":{"attempts":5,"ok":3,"ko":1,"status":{"200":4},"latencyMs":{"12":4}}}}
                """;
        withRun(fixture, runDir -> {
            LoadTestMetrics m = GatlingResultsParser.parse(runDir.toString(), "Partial", 1);

            assertEquals(m.totalRequests(), 4, "total counts outcomes (ok+ko), result=" + m);
            assertTrue(isPartial(m), "attempts (5) != ok + ko (4) must mark the result partial");

            LoadTestAssert a = new LoadTestAssert(m);
            assertFailsWithPartial("assertP95Below", () -> a.assertP95Below(1e9));
            assertFailsWithPartial("assertErrorRateBelow", () -> a.assertErrorRateBelow(1.0));
            assertFailsWithPartial("assertStatus", () -> a.assertStatus(200));
        });
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private static boolean isPartial(LoadTestMetrics m) {
        return m.partial();
    }

    private static void assertFailsWithPartial(String what, Runnable assertion) {
        try {
            assertion.run();
        } catch (AssertionError e) {
            assertTrue(String.valueOf(e.getMessage()).toLowerCase(Locale.ROOT).contains("partial"),
                    what + " must fail with \"metrics are partial\", was: " + e.getMessage());
            return;
        }
        fail(what + " passed on partial metrics; it must fail with \"metrics are partial\"");
    }

    private static void assertStep(LoadTestMetrics m, String name, int total, int ok, int ko) {
        LoadTestMetrics.StepMetrics s = m.step(name);
        assertNotNull(s, "step " + name + " missing; steps=" + m.steps().keySet());
        assertEquals(s.totalRequests(), total, name + " total");
        assertEquals(s.successfulRequests(), ok, name + " ok");
        assertEquals(s.failedRequests(), ko, name + " ko");
        assertEquals(s.errorRate(), total > 0 ? (double) ko / total : 0.0, 1e-9, name + " errorRate");
    }

    private interface RunCheck {
        void accept(Path runDir) throws IOException;
    }

    /**
     * Writes a run dir shaped like {@code GatlingEngine}'s output, with no {@code js/} folder, runs the
     * check and deletes the dir.
     */
    private static void withRun(String sidecarJson, RunCheck check) throws IOException {
        Path base = Files.createTempDirectory("gatling-owned");
        try {
            Path runDir = base.resolve("Owned-1792900000000");
            Path simDir = runDir.resolve("testflygatlingsimulation-20261025120000000");
            Files.createDirectories(simDir);
            Files.writeString(simDir.resolve("simulation.log"), "binary since 3.13, never parsed");
            Files.writeString(runDir.resolve(SIDECAR), sidecarJson);
            Files.writeString(simDir.resolve(SIDECAR), sidecarJson);
            assertFalse(Files.exists(simDir.resolve("js")), "fixture must not contain js/");
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
