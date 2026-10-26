package io.testfly.unit.loadtest;

import io.testfly.loadtest.LoadBaseline;
import io.testfly.loadtest.LoadTestAssert;
import io.testfly.loadtest.LoadTestMetrics;
import io.testfly.loadtest.RegressionResult;
import io.testfly.loadtest.Tolerance;
import org.testng.annotations.Test;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import static org.testng.Assert.*;

/**
 * Baseline behaviour fixed after the Phase 4 review (ADR 0004 decision 6): exact decimal limits, empty and zero
 * baselines, partial metrics, hardened {@link LoadBaseline#load}/{@link LoadBaseline#save}, and the run-directory and
 * file-name rules of the per-run artefact (internal {@code RunArtefacts}, reached by reflection).
 */
public class LoadBaselineReviewTest {

    private static final String RUN_ARTEFACTS = "io.testfly.loadtest.internal.RunArtefacts";
    private static final double NaN = Double.NaN;

    // ── 1: exact limits ──────────────────────────────────────────────────

    @Test
    public void percent15_overBaseline100_allowsExactly115() {
        RegressionResult r = p95(compare(100, 115, Tolerance.percent(15)));
        assertFalse(r.regressed(), "100 x 1.15 is exactly 115 (not 114.99999999999999): " + r);
        assertTrue(p95(compare(100, Math.nextUp(115.0), Tolerance.percent(15))).regressed());

        AssertionError e = expectThrows(AssertionError.class,
                () -> new LoadTestAssert(metrics("Exact", 116, 10, 10, 0.0, 10, false))
                        .assertNoRegression(baseline(100, 10, 10, 0.0, 10), Tolerance.percent(15)));
        assertTrue(e.getMessage().contains("limit 115") && !e.getMessage().contains("114.99"),
                "the message prints the exact limit: " + e.getMessage());
    }

    @Test
    public void errorRate07_plusDelta01_allowsExactly08() {
        LoadBaseline b = baseline(10, 10, 10, 0.7, 10);
        List<RegressionResult> ok = new LoadTestAssert(metrics("Exact", 10, 10, 10, 0.8, 10, false))
                .regressionResults(b, Tolerance.percent(0).errorRateDelta(0.1));
        assertFalse(ok.get(3).regressed(), "0.7 + 0.1 is exactly 0.8 (not 0.7999999999999999): " + ok.get(3));
        List<RegressionResult> worse = new LoadTestAssert(metrics("Exact", 10, 10, 10, Math.nextUp(0.8), 10, false))
                .regressionResults(b, Tolerance.percent(0).errorRateDelta(0.1));
        assertTrue(worse.get(3).regressed(), worse.get(3).toString());
    }

    // ── 2: empty and zero baselines ──────────────────────────────────────

    @Test
    public void nothingCompared_fails_listingTheMetrics() {
        LoadBaseline empty = LoadBaseline.of(new LoadTestMetrics("None", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, NaN,
                Map.of(), 0, 1, "jdk", Map.of(), 0));
        LoadTestAssert a = new LoadTestAssert(metrics("None", 40, 50, 15, 0.0, 100, false));

        AssertionError e = expectThrows(AssertionError.class, () -> a.assertNoRegression(empty, Tolerance.percent(10)));

        assertTrue(e.getMessage().contains("nothing compared"), e.getMessage());
        for (String m : List.of("p95", "p99", "mean", "errorRate", "throughput")) {
            assertTrue(e.getMessage().contains(m), "lists " + m + ": " + e.getMessage());
        }
    }

    @Test
    public void someNotCompared_stillPasses() {
        LoadTestAssert a = new LoadTestAssert(metrics("Some", 40, NaN, 15, 0.0, 100, false));
        assertSame(a.assertNoRegression(baseline(40, 50, 15, 0.0, 100), Tolerance.percent(0)), a,
                "p99 is not compared (a WARN lists it); the rest passes");
    }

    @Test
    public void of_aRunWithoutRequests_storesLatencyAndThroughputAsUnmeasured() {
        LoadBaseline b = LoadBaseline.of(new LoadTestMetrics("Zero", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.0,
                Map.of(), 1000, 1, "jdk", Map.of(), 0));
        assertEquals(b.p95(), OptionalDouble.empty());
        assertEquals(b.p99(), OptionalDouble.empty());
        assertEquals(b.mean(), OptionalDouble.empty());
        assertEquals(b.throughput(), OptionalDouble.empty());
        assertEquals(b.errorRate(), OptionalDouble.of(0.0), "the error rate is kept");
    }

    @Test
    public void zeroLatencyBaseline_regressionNamesTheZeroBaseline() {
        LoadTestAssert a = new LoadTestAssert(metrics("ZeroMs", 1, 10, 10, 0.0, 10, false));
        AssertionError e = expectThrows(AssertionError.class,
                () -> a.assertNoRegression(baseline(0, 10, 10, 0.0, 10), Tolerance.percent(1000)));
        assertTrue(e.getMessage().contains("a relative tolerance cannot absorb an increase over a 0 ms baseline"),
                e.getMessage());
    }

    @Test
    public void percent100OrMore_disablesTheThroughputCheck() {
        RegressionResult t = new LoadTestAssert(metrics("Tput", 10, 10, 10, 0.0, 0.0, false))
                .regressionResults(baseline(10, 10, 10, 0.0, 100), Tolerance.percent(100)).get(4);
        assertFalse(t.regressed(), "limit 100 x 0 = 0: " + t);
    }

    // ── 3: run directories ───────────────────────────────────────────────

    @Test
    public void newRunDir_concurrentCallsWithTheSameName_getDistinctDirectories() throws Exception {
        Path base = Files.createTempDirectory("run-dirs-concurrent-");
        Method newRunDir = Class.forName(RUN_ARTEFACTS).getDeclaredMethod("newRunDir", String.class, String.class);
        newRunDir.setAccessible(true);
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<File>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit((Callable<File>) () -> {
                    start.await();
                    return (File) newRunDir.invoke(null, base.toString(), "Same Name");
                }));
            }
            start.countDown();
            Set<File> dirs = new HashSet<>();
            for (Future<File> f : futures) {
                File dir = f.get();
                assertTrue(dir.isDirectory(), dir.toString());
                dirs.add(dir);
            }
            assertEquals(dirs.size(), threads, "every caller got its own directory: " + dirs);
        } finally {
            pool.shutdownNow();
            delete(base);
        }
    }

    // ── 4: partial metrics never become a baseline ───────────────────────

    @Test
    public void of_partialMetrics_throws() {
        IllegalArgumentException e = expectThrows(IllegalArgumentException.class,
                () -> LoadBaseline.of(metrics("Cut", 40, 50, 15, 0.0, 100, true)));
        assertTrue(e.getMessage().contains("metrics are partial"), e.getMessage());
    }

    @Test
    public void runArtefact_skipsPartialMetrics() throws Exception {
        Path dir = Files.createTempDirectory("run-artefact-partial-");
        try {
            Method write = Class.forName(RUN_ARTEFACTS).getDeclaredMethod("writeResult", Path.class,
                    LoadTestMetrics.class);
            write.setAccessible(true);
            write.invoke(null, dir, metrics("Cut", 40, 50, 15, 0.0, 100, true));
            assertFalse(Files.exists(dir.resolve(LoadBaseline.RESULT_FILE)), "no artefact for a partial run");
        } finally {
            delete(dir);
        }
    }

    // ── 5: scenario mismatch ─────────────────────────────────────────────

    @Test
    public void scenarioMismatch_comparisonContinues() {
        LoadBaseline other = LoadBaseline.of(metrics("Other", 100, 100, 100, 0.0, 100, false));
        LoadTestAssert a = new LoadTestAssert(metrics("This", 400, 100, 100, 0.0, 100, false));
        assertEquals(a.regressionResults(other, Tolerance.percent(10)).size(), 5);
        expectThrows(AssertionError.class, () -> a.assertNoRegression(other, Tolerance.percent(10)));
    }

    // ── 6: per-scenario file names ───────────────────────────────────────

    @Test
    public void scenarioResultFiles_suffixSlugCollisions() throws Exception {
        Method files = Class.forName(RUN_ARTEFACTS).getDeclaredMethod("scenarioResultFiles", List.class);
        files.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, String> names = (Map<String, String>) files.invoke(null,
                List.of("Checkout A", "Checkout_A", "Checkout/A", "Browse"));

        assertEquals(new ArrayList<>(names.values()), List.of(
                "testfly-loadtest-result-Checkout_A.json",
                "testfly-loadtest-result-Checkout_A-2.json",
                "testfly-loadtest-result-Checkout_A-3.json",
                "testfly-loadtest-result-Browse.json"));
    }

    // ── 7: load / save hardening ─────────────────────────────────────────

    @Test
    public void load_rejectsBadVersionsValuesAndTypes() throws Exception {
        Path dir = Files.createTempDirectory("baseline-hardening-");
        try {
            String ok = "\"scenarioName\":\"S\",\"engine\":\"jdk\",\"createdAt\":\"2026-10-25T10:15:30Z\"";
            for (String json : List.of(
                    "{\"schemaVersion\":4294967297," + ok + "}",               // not an int
                    "{\"schemaVersion\":1," + ok + ",\"p95\":-1}",             // negative
                    "{\"schemaVersion\":1," + ok + ",\"mean\":1e400}",         // not finite
                    "{\"schemaVersion\":1,\"scenarioName\":42,\"engine\":\"jdk\"}",   // not textual
                    "{\"schemaVersion\":1,\"scenarioName\":\"S\",\"engine\":[\"jdk\"]}",
                    "{\"schemaVersion\":1,\"scenarioName\":\"S\",\"createdAt\":123}")) {
                Path f = dir.resolve("b.json");
                Files.writeString(f, json, StandardCharsets.UTF_8);
                expectThrows(IllegalArgumentException.class, () -> LoadBaseline.load(f));
            }
            Path badUtf8 = dir.resolve("binary.json");
            Files.write(badUtf8, new byte[] {(byte) 0xC3, (byte) 0x28, (byte) 0xFF});
            IllegalArgumentException e = expectThrows(IllegalArgumentException.class, () -> LoadBaseline.load(badUtf8));
            assertTrue(e.getMessage().contains("cannot be read"), e.getMessage());

            expectThrows(IllegalArgumentException.class,
                    () -> LoadBaseline.of(metrics("S", 1, 1, 1, 0.0, 1, false)).save(null));
        } finally {
            delete(dir);
        }
    }

    // ── save: file permissions (POSIX only) ──────────────────────────────

    @Test
    public void save_newFileIsRwRR_andAnOverwrittenFileKeepsItsPermissions() throws Exception {
        Path dir = Files.createTempDirectory("baseline-perms-");
        try {
            if (!dir.getFileSystem().supportedFileAttributeViews().contains("posix")) {
                throw new org.testng.SkipException("POSIX file permissions are not supported here");
            }
            Path file = dir.resolve("baseline.json");
            LoadBaseline b = LoadBaseline.of(metrics("Perms", 40, 50, 15, 0.0, 100, false));

            b.save(file);
            assertEquals(java.nio.file.attribute.PosixFilePermissions.toString(Files.getPosixFilePermissions(file)),
                    "rw-r--r--", "a new baseline is readable by others (not the temp file's rw-------)");

            Files.setPosixFilePermissions(file, java.nio.file.attribute.PosixFilePermissions.fromString("rw-rw----"));
            b.save(file);
            assertEquals(java.nio.file.attribute.PosixFilePermissions.toString(Files.getPosixFilePermissions(file)),
                    "rw-rw----", "an overwritten file keeps its permissions");
        } finally {
            delete(dir);
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private static List<RegressionResult> compare(double baselineP95, double actualP95, Tolerance t) {
        return new LoadTestAssert(metrics("A", actualP95, 10, 10, 0.0, 10, false))
                .regressionResults(baseline(baselineP95, 10, 10, 0.0, 10), t);
    }

    private static RegressionResult p95(List<RegressionResult> results) {
        return results.get(0);
    }

    private static LoadBaseline baseline(double p95, double p99, double mean, double errorRate, double throughput) {
        return LoadBaseline.of(metrics("A", p95, p99, mean, errorRate, throughput, false));
    }

    private static LoadTestMetrics metrics(String name, double p95, double p99, double mean, double errorRate,
            double throughput, boolean partial) {
        return new LoadTestMetrics(name, 100, 98, 2, throughput, mean, 10, 30, p95, p99, 1, 1e9, errorRate,
                Map.of(200, 98L, 500, 2L), 1000, 5, "jdk", Map.of(), 20, partial);
    }

    private static void delete(Path dir) throws Exception {
        try (Stream<Path> s = Files.walk(dir)) {
            s.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }
}
