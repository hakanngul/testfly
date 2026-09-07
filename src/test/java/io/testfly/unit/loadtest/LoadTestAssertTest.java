package io.testfly.unit.loadtest;

import io.testfly.loadtest.LoadTestAssert;
import io.testfly.loadtest.LoadTestMetrics;
import org.testng.annotations.Test;

import java.util.Map;

import static org.testng.Assert.*;

/**
 * Tests the {@link LoadTestAssert} fluent assertion API.
 */
@Test(singleThreaded = true)
public class LoadTestAssertTest {

    private LoadTestMetrics metrics(double throughput, double p50, double p90, double p95, double p99,
                                     double mean, double max, double errorRate,
                                     Map<Integer, Long> statusCodes) {
        return new LoadTestMetrics("TestScenario", 1000, (int) (1000 * (1 - errorRate)),
                (int) (1000 * errorRate), throughput, mean, p50, p90, p95, p99,
                10, max, errorRate, statusCodes, 60000, 100, "jdk", Map.of());
    }

    private LoadTestMetrics goodMetrics() {
        return metrics(500, 50, 80, 120, 200, 60, 300, 0.005, Map.of(200, 995L, 404, 5L));
    }

    // ── Throughput ───────────────────────────────────────────────────────

    @Test
    public void testThroughputAbovePasses() {
        new LoadTestAssert(goodMetrics()).assertThroughputAbove(400);
    }

    @Test(expectedExceptions = AssertionError.class)
    public void testThroughputAboveFails() {
        new LoadTestAssert(goodMetrics()).assertThroughputAbove(600);
    }

    @Test
    public void testThroughputBelowPasses() {
        new LoadTestAssert(goodMetrics()).assertThroughputBelow(600);
    }

    @Test(expectedExceptions = AssertionError.class)
    public void testThroughputBelowFails() {
        new LoadTestAssert(goodMetrics()).assertThroughputBelow(400);
    }

    // ── Latency percentiles ──────────────────────────────────────────────

    @Test
    public void testP95BelowPasses() {
        new LoadTestAssert(goodMetrics()).assertP95Below(200);
    }

    @Test(expectedExceptions = AssertionError.class)
    public void testP95BelowFails() {
        new LoadTestAssert(goodMetrics()).assertP95Below(100);
    }

    @Test
    public void testP50BelowPasses() {
        new LoadTestAssert(goodMetrics()).assertP50Below(100);
    }

    @Test
    public void testP99BelowPasses() {
        new LoadTestAssert(goodMetrics()).assertP99Below(300);
    }

    @Test
    public void testMeanLatencyBelowPasses() {
        new LoadTestAssert(goodMetrics()).assertMeanLatencyBelow(100);
    }

    @Test
    public void testMaxLatencyBelowPasses() {
        new LoadTestAssert(goodMetrics()).assertMaxLatencyBelow(500);
    }

    // ── Error rate ───────────────────────────────────────────────────────

    @Test
    public void testErrorRateBelowPasses() {
        new LoadTestAssert(goodMetrics()).assertErrorRateBelow(0.01);
    }

    @Test(expectedExceptions = AssertionError.class)
    public void testErrorRateBelowFails() {
        new LoadTestAssert(goodMetrics()).assertErrorRateBelow(0.001);
    }

    @Test
    public void testSuccessRateAbovePasses() {
        new LoadTestAssert(goodMetrics()).assertSuccessRateAbove(0.99);
    }

    @Test(expectedExceptions = AssertionError.class)
    public void testSuccessRateAboveFails() {
        new LoadTestAssert(goodMetrics()).assertSuccessRateAbove(0.999);
    }

    // ── Status codes ─────────────────────────────────────────────────────

    @Test
    public void assertStatusCodeCountPasses() {
        new LoadTestAssert(goodMetrics()).assertStatusCodeCount(200, 900);
    }

    @Test(expectedExceptions = AssertionError.class)
    public void assertStatusCodeCountFails() {
        new LoadTestAssert(goodMetrics()).assertStatusCodeCount(200, 1000);
    }

    @Test
    public void assertNoStatusPasses() {
        new LoadTestAssert(goodMetrics()).assertNoStatus(500);
    }

    @Test(expectedExceptions = AssertionError.class)
    public void assertNoStatusFails() {
        new LoadTestAssert(goodMetrics()).assertNoStatus(404);
    }

    // ── Chaining ─────────────────────────────────────────────────────────

    @Test
    public void testFluentChaining() {
        new LoadTestAssert(goodMetrics())
                .assertThroughputAbove(100)
                .assertP95Below(200)
                .assertErrorRateBelow(0.01)
                .assertNoStatus(500)
                .assertSuccessRateAbove(0.99);
    }

    // ── Raw access ───────────────────────────────────────────────────────

    @Test
    public void testMetricsAccess() {
        LoadTestMetrics m = goodMetrics();
        LoadTestAssert a = new LoadTestAssert(m);

        assertSame(a.metrics(), m);
        assertEquals(a.metrics().scenarioName(), "TestScenario");
        assertEquals(a.metrics().totalRequests(), 1000);
    }

    // ── Per-step assertions ──────────────────────────────────────────────

    @Test
    public void testStepP95BelowPasses() {
        LoadTestMetrics m = new LoadTestMetrics("Test", 100, 99, 1, 500, 50,
                40, 60, 80, 100, 10, 150, 0.01, Map.of(200, 99L),
                30000, 50, "jdk",
                Map.of("Login", new LoadTestMetrics.StepMetrics("Login", 50, 49, 1, 75, 0.02)));

        new LoadTestAssert(m).assertStepP95Below("Login", 100);
    }

    @Test(expectedExceptions = AssertionError.class)
    public void testStepP95BelowFails() {
        LoadTestMetrics m = new LoadTestMetrics("Test", 100, 99, 1, 500, 50,
                40, 60, 80, 100, 10, 150, 0.01, Map.of(200, 99L),
                30000, 50, "jdk",
                Map.of("Login", new LoadTestMetrics.StepMetrics("Login", 50, 49, 1, 75, 0.02)));

        new LoadTestAssert(m).assertStepP95Below("Login", 50);
    }

    @Test(expectedExceptions = AssertionError.class)
    public void testStepNotFound() {
        LoadTestMetrics m = new LoadTestMetrics("Test", 100, 99, 1, 500, 50,
                40, 60, 80, 100, 10, 150, 0.01, Map.of(),
                30000, 50, "jdk", Map.of());

        new LoadTestAssert(m).assertStepP95Below("NonExistent", 100);
    }

    // ── Empty metrics ────────────────────────────────────────────────────

    @Test
    public void testEmptyMetrics() {
        LoadTestMetrics empty = LoadTestMetrics.empty("Empty");

        assertEquals(empty.totalRequests(), 0);
        assertEquals(empty.throughputRps(), 0.0);
        assertEquals(empty.engine(), "none");
        assertTrue(empty.steps().isEmpty());
    }
}
