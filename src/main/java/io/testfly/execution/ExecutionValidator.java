package io.testfly.execution;

import io.testfly.config.TestFlyConfig;
import org.testng.xml.XmlSuite;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class ExecutionValidator {
    private ExecutionValidator() {}

    public static void validate(TestFlyConfig.Execution execution) {
        if (execution == null) {
            throw new IllegalStateException("Execution configuration missing");
        }

        String parallel = execution.getParallel();
        if (parallel == null || parallel.trim().isEmpty()) {
            throw new IllegalStateException("Parallel execution configuration missing");
        }
        try {
            // Delegate to TestNG's own enum rather than a hand-written allowlist — the
            // value is passed straight to XmlSuite.setParallel() downstream, so anything
            // TestNG accepts, TestFly accepts.
            XmlSuite.ParallelMode.valueOf(parallel.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "Invalid execution.parallel value '" + parallel +
                            "' — valid values are " + validParallelModes()
            );
        }

        int threads = execution.getThreadCount();
        if (threads < 1) {
            throw new IllegalStateException("Thread count must be >= 1");
        }
        int maxAllowed = Runtime.getRuntime().availableProcessors() * 2;
        if (threads > maxAllowed) {
            throw new IllegalStateException(
                    "Thread count " + threads +
                            " exceeds safe limit (" + maxAllowed + ")"
            );
        }

        if (execution.getSessionWaitSeconds() < 0) {
            throw new IllegalStateException(
                    "execution.sessionWaitSeconds must be >= 0 (was " + execution.getSessionWaitSeconds() + ")");
        }

        for (String warning : crossCheckWarnings(execution)) {
            System.err.println("[TestFly] WARNING: " + warning);
        }
    }

    /**
     * Cross-field checks that are suspicious but not invalid, so they warn instead
     * of failing the run. Currently: more parallel threads than
     * {@code maxActiveSessions} means the surplus threads queue for a browser slot
     * for up to {@code sessionWaitSeconds} each.
     */
    public static List<String> crossCheckWarnings(TestFlyConfig.Execution execution) {
        List<String> warnings = new ArrayList<>();
        if (execution == null || execution.getParallel() == null
                || "none".equalsIgnoreCase(execution.getParallel().trim())) {
            return warnings;
        }
        int threads = execution.getThreadCount();
        int maxSessions = execution.getMaxActiveSessions();
        if (threads > maxSessions) {
            warnings.add("execution.threadCount (" + threads + ") is greater than execution.maxActiveSessions ("
                    + maxSessions + "); " + (threads - Math.max(maxSessions, 0))
                    + " thread(s) will queue for a browser slot for up to execution.sessionWaitSeconds ("
                    + execution.getSessionWaitSeconds() + "s) each. Raise maxActiveSessions to at least "
                    + threads + " or lower threadCount.");
        }
        return warnings;
    }

    private static String validParallelModes() {
        return Arrays.stream(XmlSuite.ParallelMode.values())
                .map(mode -> mode.name().toLowerCase(Locale.ROOT))
                .sorted()
                .collect(Collectors.joining(", "));
    }
}
