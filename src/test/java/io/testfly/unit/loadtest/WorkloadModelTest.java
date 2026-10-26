package io.testfly.unit.loadtest;

import io.testfly.loadtest.LoadScenario;
import io.testfly.loadtest.internal.LoadScenarioValidator;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.UUID;
import java.util.function.Supplier;

import static io.testfly.unit.loadtest.WorkloadApi.atOnce;
import static io.testfly.unit.loadtest.WorkloadApi.closed;
import static io.testfly.unit.loadtest.WorkloadApi.constantConcurrent;
import static io.testfly.unit.loadtest.WorkloadApi.constantRate;
import static io.testfly.unit.loadtest.WorkloadApi.nothingFor;
import static io.testfly.unit.loadtest.WorkloadApi.open;
import static io.testfly.unit.loadtest.WorkloadApi.ramp;
import static io.testfly.unit.loadtest.WorkloadApi.rampConcurrent;
import static io.testfly.unit.loadtest.WorkloadApi.rampRate;
import static io.testfly.unit.loadtest.WorkloadApi.stressPeak;
import static org.testng.Assert.*;

/**
 * Workload model API (ADR 0004 decision 3, ADR 0003 decision 4): factory validation, {@code Workload}
 * peak and duration math, and the {@link LoadScenarioValidator} rules for a scenario with a workload.
 *
 * <p>Written before the API existed (red first); the calls go through the {@link WorkloadApi} helper.
 *
 * <p>{@code maxUsers} assumption: {@code LoadScenarioValidator.validate(scenario, engine)} applies the
 * {@code loadtest.maxUsers} cap of the loaded configuration. The test classpath sets no
 * {@code loadtest.maxUsers}, so the cap is the default 1000 ({@code TestFlyConfig.LoadTest}).
 *
 * <p>Stateless (no server, no shared fixtures): parallel-safe under surefire's method parallelism.
 */
public class WorkloadModelTest {

    private static final int DEFAULT_MAX_USERS = 1000;
    private static final String COMBINED = "workload(...) cannot be combined with users/rampUp/hold/cooldown";
    private static final String NEEDS_GATLING = "workload(...) requires the Gatling engine";

    private static Duration s(long seconds) {
        return Duration.ofSeconds(seconds);
    }

    /** Asserts that the factory throws {@link IllegalArgumentException} instead of returning a step. */
    private static IllegalArgumentException rejects(String what, Supplier<?> factory) {
        return expectThrows(IllegalArgumentException.class, () -> {
            Object made = factory.get();
            fail(what + " must throw IllegalArgumentException, but returned " + made);
        });
    }

    private static LoadScenario scenario(Object workload) {
        LoadScenario s = LoadScenario.named("Workload_" + UUID.randomUUID()).baseUrl("http://127.0.0.1:1");
        s.get("/a");
        return WorkloadApi.workload(s, workload);
    }

    private static IllegalArgumentException validatorRejects(LoadScenario s, String engine) {
        return expectThrows(IllegalArgumentException.class, () -> LoadScenarioValidator.validate(s, engine));
    }

    // ── factory validation: open steps ───────────────────────────────────

    @Test
    public void atOnce_rejectsUserCountBelowOne() {
        rejects("atOnce(0)", () -> atOnce(0));
        rejects("atOnce(-1)", () -> atOnce(-1));
    }

    @Test
    public void ramp_rejectsBadUsersAndDuration() {
        rejects("ramp(0, 1s)", () -> ramp(0, s(1)));
        rejects("ramp(5, 0s)", () -> ramp(5, Duration.ZERO));
        rejects("ramp(5, -1s)", () -> ramp(5, s(-1)));
    }

    @Test
    public void constantRate_rejectsNegativeOrNonFiniteRateAndBadDuration() {
        rejects("constantRate(-1, 1s)", () -> constantRate(-1, s(1)));
        rejects("constantRate(NaN, 1s)", () -> constantRate(Double.NaN, s(1)));
        rejects("constantRate(+Inf, 1s)", () -> constantRate(Double.POSITIVE_INFINITY, s(1)));
        rejects("constantRate(10, 0s)", () -> constantRate(10, Duration.ZERO));
        rejects("constantRate(10, -1s)", () -> constantRate(10, s(-1)));
    }

    @Test
    public void rampRate_rejectsNegativeOrNonFiniteRatesAndBadDuration() {
        rejects("rampRate(-1, 5, 1s)", () -> rampRate(-1, 5, s(1)));
        rejects("rampRate(1, -5, 1s)", () -> rampRate(1, -5, s(1)));
        rejects("rampRate(NaN, 5, 1s)", () -> rampRate(Double.NaN, 5, s(1)));
        rejects("rampRate(1, +Inf, 1s)", () -> rampRate(1, Double.POSITIVE_INFINITY, s(1)));
        rejects("rampRate(1, 5, 0s)", () -> rampRate(1, 5, Duration.ZERO));
    }

    @Test
    public void stressPeak_rejectsBadUsersAndDuration() {
        rejects("stressPeak(0, 1s)", () -> stressPeak(0, s(1)));
        rejects("stressPeak(5, 0s)", () -> stressPeak(5, Duration.ZERO));
    }

    @Test
    public void nothingFor_rejectsZeroAndNegative() {
        rejects("nothingFor(0s)", () -> nothingFor(Duration.ZERO));
        rejects("nothingFor(-1s)", () -> nothingFor(s(-1)));
    }

    @Test
    public void openFactories_acceptBoundaryValues() {
        assertNotNull(atOnce(1));
        assertNotNull(ramp(1, Duration.ofMillis(1)));
        assertNotNull(constantRate(0, s(1)), "rate 0 is allowed (rates must be >= 0)");
        assertNotNull(rampRate(0, 0, s(1)), "rates 0 are allowed");
        assertNotNull(stressPeak(1, s(1)));
        assertNotNull(nothingFor(Duration.ofMillis(1)));
    }

    // ── factory validation: closed steps ─────────────────────────────────

    @Test
    public void constantConcurrent_rejectsBadUsersAndDuration() {
        rejects("constantConcurrent(0, 1s)", () -> constantConcurrent(0, s(1)));
        rejects("constantConcurrent(-1, 1s)", () -> constantConcurrent(-1, s(1)));
        rejects("constantConcurrent(5, 0s)", () -> constantConcurrent(5, Duration.ZERO));
    }

    @Test
    public void rampConcurrent_allowsZeroButRejectsNegativeAndBadDuration() {
        assertNotNull(rampConcurrent(5, 0, s(1)), "ramp-down to 0 is the explicit cooldown and must be allowed");
        assertNotNull(rampConcurrent(0, 5, s(1)), "ramp-up from 0 must be allowed");
        rejects("rampConcurrent(-1, 5, 1s)", () -> rampConcurrent(-1, 5, s(1)));
        rejects("rampConcurrent(5, -1, 1s)", () -> rampConcurrent(5, -1, s(1)));
        rejects("rampConcurrent(5, 0, 0s)", () -> rampConcurrent(5, 0, Duration.ZERO));
    }

    // ── Workload: model, steps, peak, duration ───────────────────────────

    @Test
    public void closed_constantThenRampDown_peakIsPeakConcurrency_durationIsSum() {
        Object w = closed(constantConcurrent(5, s(3)), rampConcurrent(5, 0, s(3)));

        assertEquals(WorkloadApi.model(w), "CLOSED");
        assertEquals(WorkloadApi.closedSteps(w).size(), 2);
        assertTrue(WorkloadApi.openSteps(w).isEmpty(), "openSteps must be empty for CLOSED");
        assertEquals(WorkloadApi.peak(w), 5.0, 1e-9, "closed peak = peak concurrent users");
        assertEquals(WorkloadApi.totalDuration(w), s(6));
    }

    @Test
    public void closed_peakIsTheHighestRampEnd() {
        Object w = closed(rampConcurrent(2, 8, s(2)), constantConcurrent(3, s(1)));

        assertEquals(WorkloadApi.peak(w), 8.0, 1e-9);
        assertEquals(WorkloadApi.totalDuration(w), s(3));
    }

    @Test
    public void open_peakIsTheHighestArrivalRate_durationIsSumWithMillisecondPrecision() {
        Object w = open(constantRate(10, s(4)), rampRate(2, 25, s(2)), nothingFor(Duration.ofMillis(1500)));

        assertEquals(WorkloadApi.model(w), "OPEN");
        assertEquals(WorkloadApi.openSteps(w).size(), 3);
        assertTrue(WorkloadApi.closedSteps(w).isEmpty(), "closedSteps must be empty for OPEN");
        assertEquals(WorkloadApi.peak(w), 25.0, 1e-9, "open peak = peak arrival rate (users/s)");
        assertEquals(WorkloadApi.totalDuration(w), Duration.ofMillis(7500));
    }

    @Test
    public void open_atOnceAddsNoDuration() {
        Object w = open(atOnce(3), constantRate(10, s(4)));

        assertEquals(WorkloadApi.totalDuration(w), s(4), "atOnce lasts 0");
        assertEquals(WorkloadApi.totalDuration(open(atOnce(3))), Duration.ZERO);
    }

    @Test
    public void scenarioWorkload_accessorReturnsTheSetWorkload() {
        Object w = open(atOnce(1));
        LoadScenario s = scenario(w);

        assertSame(WorkloadApi.workloadOf(s), w);
    }

    // ── validator: legacy shape ──────────────────────────────────────────

    @Test
    public void validator_acceptsWorkloadOnGatling() {
        LoadScenarioValidator.validate(scenario(closed(constantConcurrent(5, s(1)))), "gatling");
        LoadScenarioValidator.validate(scenario(open(atOnce(3), constantRate(10, s(2)))), "gatling");
    }

    @Test
    public void validator_rejectsWorkloadCombinedWithUsers() {
        LoadScenario s = scenario(open(atOnce(1))).users(5);
        assertTrue(validatorRejects(s, "gatling").getMessage().contains(COMBINED));
    }

    @Test
    public void validator_rejectsWorkloadCombinedWithRampUp() {
        LoadScenario s = scenario(open(atOnce(1))).rampUp(s(1));
        assertTrue(validatorRejects(s, "gatling").getMessage().contains(COMBINED));
    }

    @Test
    public void validator_rejectsWorkloadCombinedWithHold() {
        LoadScenario s = scenario(closed(constantConcurrent(2, s(1)))).hold(s(1));
        assertTrue(validatorRejects(s, "gatling").getMessage().contains(COMBINED));
    }

    @Test
    public void validator_rejectsWorkloadCombinedWithCooldown() {
        LoadScenario s = scenario(closed(constantConcurrent(2, s(1)))).cooldown(s(1));
        assertTrue(validatorRejects(s, "gatling").getMessage().contains(COMBINED));
    }

    // ── validator: engine ────────────────────────────────────────────────

    @Test
    public void validator_rejectsOpenWorkloadOnJdk_mentioningGatling() {
        String msg = validatorRejects(scenario(open(atOnce(1))), "jdk").getMessage();
        assertTrue(msg.contains(NEEDS_GATLING), msg);
        assertTrue(msg.contains("gatling-charts-highcharts"), "message must say how to add Gatling: " + msg);
    }

    @Test
    public void validator_rejectsClosedWorkloadOnJdk_mentioningGatling() {
        String msg = validatorRejects(scenario(closed(constantConcurrent(1, s(1)))), "jdk").getMessage();
        assertTrue(msg.contains(NEEDS_GATLING), msg);
    }

    // ── validator: maxUsers caps (default 1000, see class doc) ───────────

    @Test
    public void validator_closedPeakAtCap_isAccepted() {
        LoadScenarioValidator.validate(scenario(closed(constantConcurrent(DEFAULT_MAX_USERS, s(1)))), "gatling");
    }

    @Test
    public void validator_closedPeakAboveCap_isRejectedNamingTheCap() {
        assertCapExceeded(scenario(closed(constantConcurrent(DEFAULT_MAX_USERS + 1, s(1)))));
        assertCapExceeded(scenario(closed(constantConcurrent(5, s(1)),
                rampConcurrent(0, DEFAULT_MAX_USERS + 1, s(1)))));
    }

    @Test
    public void validator_openRateAtCap_isAccepted() {
        LoadScenarioValidator.validate(scenario(open(constantRate(DEFAULT_MAX_USERS, s(1)))), "gatling");
        LoadScenarioValidator.validate(scenario(open(atOnce(DEFAULT_MAX_USERS))), "gatling");
    }

    @Test
    public void validator_openPeakRateAboveCap_isRejectedNamingTheCap() {
        assertCapExceeded(scenario(open(constantRate(DEFAULT_MAX_USERS + 1, s(1)))));
        assertCapExceeded(scenario(open(rampRate(1, DEFAULT_MAX_USERS + 1, s(2)))));
    }

    @Test
    public void validator_openUserCountAboveCap_isRejectedEvenAtALowRate() {
        // 1001 users over 100 s is ~10 users/s: below the rate cap, above the user-count cap.
        assertCapExceeded(scenario(open(atOnce(DEFAULT_MAX_USERS + 1))));
        assertCapExceeded(scenario(open(ramp(DEFAULT_MAX_USERS + 1, s(100)))));
        assertCapExceeded(scenario(open(stressPeak(DEFAULT_MAX_USERS + 1, s(100)))));
    }

    private static void assertCapExceeded(LoadScenario s) {
        String msg = validatorRejects(s, "gatling").getMessage();
        assertTrue(msg != null && msg.contains("maxUsers"), "the error must name the maxUsers cap, was: " + msg);
    }
}
