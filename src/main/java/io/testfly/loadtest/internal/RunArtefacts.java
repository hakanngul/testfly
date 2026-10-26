package io.testfly.loadtest.internal;

import io.testfly.loadtest.LoadBaseline;
import io.testfly.loadtest.LoadTestMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Run directories and the per-run artefact {@value LoadBaseline#RESULT_FILE}: the {@link LoadBaseline} JSON of a
 * run, written into the run's directory so it can be copied into a repository as a baseline. Writing it never fails
 * the run, and a partial run writes none.
 */
final class RunArtefacts {

    private static final Logger LOGGER = LoggerFactory.getLogger(RunArtefacts.class);

    private RunArtefacts() {
    }

    /** The run-directory name of {@code name}: the same slug as Gatling run directories. */
    static String slug(String name) {
        return (name != null ? name : "loadtest").replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    /**
     * Atomically creates a new {@code <resultsDir>/<slug>-<millis>/}: if that directory already exists (another run
     * of the same scenario in the same millisecond, in this JVM or another), the next millisecond value is tried.
     *
     * @throws UncheckedIOException if the directory cannot be created
     */
    static File newRunDir(String resultsDir, String name) {
        Path parent = Path.of(resultsDir);
        try {
            Files.createDirectories(parent);
            long millis = System.currentTimeMillis();
            while (true) {
                Path dir = parent.resolve(slug(name) + "-" + millis);
                try {
                    return Files.createDirectory(dir).toFile();
                } catch (FileAlreadyExistsException e) {
                    millis++;
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("[LoadTest] Could not create a run directory in " + resultsDir, e);
        }
    }

    /**
     * The JDK engine's artefact: a new run directory with {@value LoadBaseline#RESULT_FILE}. Any failure, including
     * creating the directory, is logged as a WARN and never thrown.
     */
    static void writeJdkRunResult(String resultsDir, String scenarioName, LoadTestMetrics metrics) {
        if (resultsDir == null || resultsDir.isBlank()) {
            return;
        }
        if (skipPartial(metrics)) {
            return;
        }
        try {
            writeResult(newRunDir(resultsDir, scenarioName).toPath(), metrics);
        } catch (RuntimeException e) {
            LOGGER.warn("[LoadTest] Could not create the run directory for {} in {} ({}); the run itself is not "
                    + "affected", scenarioName, resultsDir, e.toString());
        }
    }

    /**
     * Writes {@code metrics} as {@code dir/fileName}; partial metrics are skipped, and a failure is logged as a WARN,
     * never thrown.
     */
    static void writeResult(Path dir, String fileName, LoadTestMetrics metrics) {
        if (skipPartial(metrics)) {
            return;
        }
        Path file = dir.resolve(fileName);
        try {
            LoadBaseline.of(metrics).save(file);
        } catch (RuntimeException e) {
            LOGGER.warn("[LoadTest] Could not write the run result {} ({}); the run itself is not affected", file,
                    e.toString());
        }
    }

    /** {@link #writeResult(Path, String, LoadTestMetrics)} with {@value LoadBaseline#RESULT_FILE}. */
    static void writeResult(Path dir, LoadTestMetrics metrics) {
        writeResult(dir, LoadBaseline.RESULT_FILE, metrics);
    }

    /**
     * The per-scenario result files of a multi-scenario run, in order: {@code testfly-loadtest-result-<slug>.json}.
     * When two scenario names have the same slug (for example {@code "Checkout A"} and {@code "Checkout_A"}), the
     * later one gets a numeric suffix ({@code -2}, {@code -3}, ...) and a WARN is logged.
     */
    static Map<String, String> scenarioResultFiles(List<String> scenarioNames) {
        Map<String, String> files = new LinkedHashMap<>();
        Set<String> used = new HashSet<>();
        used.add(LoadBaseline.RESULT_FILE);
        for (String name : scenarioNames) {
            String base = "testfly-loadtest-result-" + slug(name);
            String file = base + ".json";
            for (int n = 2; !used.add(file); n++) {
                file = base + "-" + n + ".json";
            }
            if (!file.equals(base + ".json")) {
                LOGGER.warn("[LoadTest] Scenario '{}' has the same file name slug as an earlier scenario; its run "
                        + "result is written to {}", name, file);
            }
            files.put(name, file);
        }
        return files;
    }

    private static boolean skipPartial(LoadTestMetrics metrics) {
        if (metrics != null && metrics.partial()) {
            LOGGER.warn("[LoadTest] {}: metrics are partial, so no {} is written (an incomplete run is never a "
                    + "baseline)", metrics.scenarioName(), LoadBaseline.RESULT_FILE);
            return true;
        }
        return false;
    }
}
