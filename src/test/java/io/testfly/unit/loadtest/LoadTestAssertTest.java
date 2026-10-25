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

    // ── Additional Assertions & Query Helpers ───────────────────────────

    @Test
    public void testMinLatencyBelowPasses() {
        new LoadTestAssert(goodMetrics()).assertMinLatencyBelow(50);
    }

    @Test(expectedExceptions = AssertionError.class)
    public void testMinLatencyBelowFails() {
        new LoadTestAssert(goodMetrics()).assertMinLatencyBelow(5);
    }

    @Test
    public void testTotalRequestsAbovePasses() {
        new LoadTestAssert(goodMetrics()).assertTotalRequestsAbove(900);
    }

    @Test(expectedExceptions = AssertionError.class)
    public void testTotalRequestsAboveFails() {
        new LoadTestAssert(goodMetrics()).assertTotalRequestsAbove(1000);
    }

    @Test
    public void testSuccessfulRequestsAbovePasses() {
        new LoadTestAssert(goodMetrics()).assertSuccessfulRequestsAbove(900);
    }

    @Test(expectedExceptions = AssertionError.class)
    public void testSuccessfulRequestsAboveFails() {
        new LoadTestAssert(goodMetrics()).assertSuccessfulRequestsAbove(995);
    }

    @Test
    public void testFailedRequestsBelowPasses() {
        new LoadTestAssert(goodMetrics()).assertFailedRequestsBelow(10);
    }

    @Test(expectedExceptions = AssertionError.class)
    public void testFailedRequestsBelowFails() {
        new LoadTestAssert(goodMetrics()).assertFailedRequestsBelow(5);
    }

    @Test
    public void testStepErrorRateBelowPasses() {
        LoadTestMetrics m = new LoadTestMetrics("Test", 100, 99, 1, 500, 50,
                40, 60, 80, 100, 10, 150, 0.01, Map.of(200, 99L),
                30000, 50, "jdk",
                Map.of("Checkout", new LoadTestMetrics.StepMetrics("Checkout", 50, 49, 1, 75, 0.02)));

        new LoadTestAssert(m).assertStepErrorRateBelow("Checkout", 0.05);
    }

    @Test(expectedExceptions = AssertionError.class)
    public void testStepErrorRateBelowFails() {
        LoadTestMetrics m = new LoadTestMetrics("Test", 100, 99, 1, 500, 50,
                40, 60, 80, 100, 10, 150, 0.01, Map.of(200, 99L),
                30000, 50, "jdk",
                Map.of("Checkout", new LoadTestMetrics.StepMetrics("Checkout", 50, 49, 1, 75, 0.05)));

        new LoadTestAssert(m).assertStepErrorRateBelow("Checkout", 0.02);
    }

    @Test(expectedExceptions = AssertionError.class)
    public void testStepErrorRateBelowMissingStep() {
        LoadTestMetrics m = new LoadTestMetrics("Test", 100, 99, 1, 500, 50,
                40, 60, 80, 100, 10, 150, 0.01, Map.of(),
                30000, 50, "jdk", Map.of());

        new LoadTestAssert(m).assertStepErrorRateBelow("NonExistent", 0.05);
    }

    @Test
    public void testMetricsQueryHelpers() {
        LoadTestMetrics m = new LoadTestMetrics("QueryScenario", 100, 95, 5, 250, 40,
                30, 50, 70, 90, 10, 120, 0.05,
                Map.of(200, 95L, 500, 5L), 10000, 10, "gatling",
                Map.of("Login", new LoadTestMetrics.StepMetrics("Login", 50, 48, 2, 60, 0.04)));

        assertEquals(m.successRate(), 0.95, 0.001);
        assertEquals(m.statusCodeCount(200), 95L);
        assertEquals(m.statusCodeCount(404), 0L);
        assertTrue(m.hasStatusCode(200));
        assertTrue(m.hasStatusCode(500));
        assertFalse(m.hasStatusCode(404));

        assertTrue(m.hasStep("Login"));
        assertFalse(m.hasStep("Logout"));
        assertNotNull(m.step("Login"));
        assertNull(m.step("Logout"));
        assertEquals(m.step("Login").totalRequests(), 50);
    }

    // ── Empty metrics ────────────────────────────────────────────────────

    @Test
    public void testEmptyMetrics() {
        LoadTestMetrics empty = LoadTestMetrics.empty("Empty");

        assertEquals(empty.totalRequests(), 0);
        assertEquals(empty.throughputRps(), 0.0);
        assertEquals(empty.engine(), "none");
        assertTrue(empty.steps().isEmpty());
        assertEquals(empty.successRate(), 0.0);
        assertEquals(empty.statusCodeCount(200), 0L);
        assertFalse(empty.hasStatusCode(200));
        assertFalse(empty.hasStep("any"));
    }

    // ── LoadTestMetrics: p75, isMeasured, statusCodesMeasured (1.1.0) ─────

    private static LoadTestMetrics gatlingLike(Map<Integer, Long> statusCodes, int total) {
        // Gatling-style: p90 not measured (NaN), p75 measured
        return new LoadTestMetrics("GatlingScenario", total, total, 0, 100, 40, 30, Double.NaN, 90, 120,
                5, 200, 0, statusCodes, 1000, 2, "gatling",
                Map.of("s1", new LoadTestMetrics.StepMetrics("s1", total, total, 0, Double.NaN, 0)), 55);
    }

    @Test
    public void testLegacy18ArgConstructorYieldsNaNP75() {
        LoadTestMetrics m = goodMetrics();
        assertTrue(Double.isNaN(m.p75LatencyMs()), "18-arg constructor must report p75 as not measured");
        assertFalse(LoadTestMetrics.isMeasured(m.p75LatencyMs()));
        assertEquals(m.p90LatencyMs(), 80.0);
    }

    @Test
    public void testCanonicalConstructorKeepsP75() {
        assertEquals(gatlingLike(Map.of(200, 3L), 3).p75LatencyMs(), 55.0);
    }

    @Test
    public void testEmptyMetricsKeepZeroP75() {
        LoadTestMetrics empty = LoadTestMetrics.empty("x");
        assertEquals(empty.p75LatencyMs(), 0.0);
        assertTrue(empty.statusCodesMeasured(), "no request sent: nothing to measure");
    }

    @Test
    public void testIsMeasured() {
        assertTrue(LoadTestMetrics.isMeasured(0.0));
        assertTrue(LoadTestMetrics.isMeasured(12.5));
        assertFalse(LoadTestMetrics.isMeasured(Double.NaN));
    }

    @Test
    public void testStatusCodesMeasured() {
        assertTrue(gatlingLike(Map.of(201, 3L), 3).statusCodesMeasured());
        assertTrue(gatlingLike(Map.of(-1, 3L), 3).statusCodesMeasured(), "-1 (no response) is a measurement");
        assertFalse(gatlingLike(Map.of(), 3).statusCodesMeasured());
        assertFalse(gatlingLike(null, 3).statusCodesMeasured());
        assertTrue(gatlingLike(Map.of(), 0).statusCodesMeasured());
    }

    // ── LoadTestAssert: p75 and "not measured" errors ────────────────────

    @Test
    public void testP75BelowPasses() {
        new LoadTestAssert(gatlingLike(Map.of(200, 3L), 3)).assertP75Below(60);
    }

    @Test(expectedExceptions = AssertionError.class,
            expectedExceptionsMessageRegExp = ".*p75 latency 55ms is not below 50ms.*")
    public void testP75BelowFails() {
        new LoadTestAssert(gatlingLike(Map.of(200, 3L), 3)).assertP75Below(50);
    }

    @Test(expectedExceptions = AssertionError.class, expectedExceptionsMessageRegExp =
            "\\[LoadTest\\] TestScenario: p75 latency was not measured by engine 'jdk'")
    public void testP75NotMeasuredFails() {
        new LoadTestAssert(goodMetrics()).assertP75Below(1_000_000);
    }

    @Test(expectedExceptions = AssertionError.class, expectedExceptionsMessageRegExp =
            "\\[LoadTest\\] GatlingScenario: p90 latency was not measured by engine 'gatling'")
    public void testP90NotMeasuredFailsInsteadOfPassing() {
        // NaN >= threshold is false: without the explicit check this assertion would silently pass
        new LoadTestAssert(gatlingLike(Map.of(200, 3L), 3)).assertP90Below(1_000_000);
    }

    @Test(expectedExceptions = AssertionError.class,
            expectedExceptionsMessageRegExp = ".*step 's1' p95 latency was not measured by engine 'gatling'")
    public void testStepP95NotMeasuredFails() {
        new LoadTestAssert(gatlingLike(Map.of(200, 3L), 3)).assertStepP95Below("s1", 1_000_000);
    }

    @Test(expectedExceptions = AssertionError.class, expectedExceptionsMessageRegExp =
            "\\[LoadTest\\] GatlingScenario: HTTP status codes were not measured by engine 'gatling'")
    public void testAssertStatusNotMeasuredFails() {
        new LoadTestAssert(gatlingLike(Map.of(), 3)).assertStatus(200);
    }

    @Test(expectedExceptions = AssertionError.class,
            expectedExceptionsMessageRegExp = ".*HTTP status codes were not measured.*")
    public void testAssertStatusCodeCountNotMeasuredFails() {
        new LoadTestAssert(gatlingLike(Map.of(), 3)).assertStatusCodeCount(200, 1);
    }

    @Test(expectedExceptions = AssertionError.class,
            expectedExceptionsMessageRegExp = ".*HTTP status codes were not measured.*")
    public void testAssertNoStatusNotMeasuredFailsInsteadOfPassing() {
        // an empty map would otherwise make assertNoStatus(500) pass on unmeasured data
        new LoadTestAssert(gatlingLike(Map.of(), 3)).assertNoStatus(500);
    }

    @Test
    public void testAssertNoStatusWithMeasuredCodesPasses() {
        new LoadTestAssert(gatlingLike(Map.of(201, 3L), 3)).assertNoStatus(500).assertStatus(201);
    }
}
