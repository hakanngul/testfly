package io.testfly.unit.loadtest;

import io.testfly.loadtest.ClosedStep;
import io.testfly.loadtest.LoadScenario;
import io.testfly.loadtest.OpenStep;
import io.testfly.loadtest.Workload;

import java.time.Duration;
import java.util.List;

/**
 * Test helper for the workload-model API of ADR 0004 decision 3 / ADR 0003 decision 4
 * ({@link Workload}, {@link OpenStep}, {@link ClosedStep}, {@link LoadScenario#workload(Workload)}).
 *
 * <p>The red tests were written before that API existed and reached it through reflection. The API is now
 * in the tree, so every method here is a direct call; the {@code Object}-typed signatures are kept so the
 * tests stay unchanged.
 */
public final class WorkloadApi {

    private WorkloadApi() {
    }

    // ── OpenStep ─────────────────────────────────────────────────────────

    public static Object atOnce(int users) {
        return OpenStep.atOnce(users);
    }

    public static Object ramp(int users, Duration over) {
        return OpenStep.ramp(users, over);
    }

    public static Object constantRate(double usersPerSec, Duration during) {
        return OpenStep.constantRate(usersPerSec, during);
    }

    public static Object rampRate(double fromPerSec, double toPerSec, Duration during) {
        return OpenStep.rampRate(fromPerSec, toPerSec, during);
    }

    public static Object stressPeak(int users, Duration over) {
        return OpenStep.stressPeak(users, over);
    }

    public static Object nothingFor(Duration d) {
        return OpenStep.nothingFor(d);
    }

    // ── ClosedStep ───────────────────────────────────────────────────────

    public static Object constantConcurrent(int users, Duration during) {
        return ClosedStep.constantConcurrent(users, during);
    }

    public static Object rampConcurrent(int fromUsers, int toUsers, Duration during) {
        return ClosedStep.rampConcurrent(fromUsers, toUsers, during);
    }

    // ── Workload ─────────────────────────────────────────────────────────

    /** {@code Workload.open(first, more...)}. */
    public static Object open(Object first, Object... more) {
        OpenStep[] rest = new OpenStep[more.length];
        for (int i = 0; i < more.length; i++) {
            rest[i] = (OpenStep) more[i];
        }
        return Workload.open((OpenStep) first, rest);
    }

    /** {@code Workload.closed(first, more...)}. */
    public static Object closed(Object first, Object... more) {
        ClosedStep[] rest = new ClosedStep[more.length];
        for (int i = 0; i < more.length; i++) {
            rest[i] = (ClosedStep) more[i];
        }
        return Workload.closed((ClosedStep) first, rest);
    }

    /** {@code workload.model().name()}: {@code "OPEN"} or {@code "CLOSED"}. */
    public static String model(Object workload) {
        return ((Workload) workload).model().name();
    }

    public static List<?> openSteps(Object workload) {
        return ((Workload) workload).openSteps();
    }

    public static List<?> closedSteps(Object workload) {
        return ((Workload) workload).closedSteps();
    }

    public static Duration totalDuration(Object workload) {
        return ((Workload) workload).totalDuration();
    }

    public static double peak(Object workload) {
        return ((Workload) workload).peak();
    }

    // ── LoadScenario ─────────────────────────────────────────────────────

    /** {@code scenario.workload(workload)}. */
    public static LoadScenario workload(LoadScenario scenario, Object workload) {
        return scenario.workload((Workload) workload);
    }

    /** {@code scenario.workload()}. */
    public static Object workloadOf(LoadScenario scenario) {
        return scenario.workload();
    }
}
