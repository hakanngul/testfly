package io.testfly.unit.loadtest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.testfly.loadtest.LoadTestAssert;
import io.testfly.loadtest.LoadTestMetrics;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.testng.Assert.*;

/**
 * Load-test baselines and regression comparison of ADR 0004 decision 6 (Phase 4), below the engines:
 * {@code LoadBaseline} save/load (versioned JSON), {@code Tolerance},
 * {@code LoadTestAssert.assertNoRegression(LoadBaseline, Tolerance)} and
 * {@code LoadTestAssert.regressionResults(LoadBaseline, Tolerance)}.
 *
 * <p>Written before the API existed (red first): every baseline call goes through {@link BaselineApi}. No test
 * here runs a load test or opens a socket; files go to a fresh temp directory per test, so the tests share no
 * mutable state and are parallel-safe.
 *
 * <p>Boundary values are chosen so the limits are exact in binary floating point (for example
 * {@code 100 × 1.5 = 150}), so "strictly above/below the limit" is tested with {@link Math#nextUp} /
 * {@link Math#nextDown} of the limit.
 *
 * <p>The engine-mismatch WARN is not captured: the project has no log-capture pattern in its tests. The test
 * only asserts that the comparison continues.
 */
public class LoadBaselineTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final double NaN = Double.NaN;

    /** The compared metrics, in result order. */
    private static final List<String> METRICS = List.of("p95", "p99", "mean", "errorRate", "throughput");

    // ── save / load ──────────────────────────────────────────────────────

    @Test
    public void of_takesEachValueFromItsMetricsComponent_andStampsCreatedAt() {
        Instant before = Instant.now();
        Object baseline = BaselineApi.of(metrics("Checkout", "jdk", 40, 50, 15, 0.02, 100, false));
        Instant after = Instant.now();

        assertEquals(BaselineApi.scenarioName(baseline), "Checkout");
        assertEquals(BaselineApi.engine(baseline), "jdk");
        assertEquals(BaselineApi.p95(baseline), OptionalDouble.of(40));
        assertEquals(BaselineApi.p99(baseline), OptionalDouble.of(50));
        assertEquals(BaselineApi.mean(baseline), OptionalDouble.of(15));
        assertEquals(BaselineApi.errorRate(baseline), OptionalDouble.of(0.02));
        assertEquals(BaselineApi.throughput(baseline), OptionalDouble.of(100));
        Instant created = BaselineApi.createdAt(baseline);
        assertNotNull(created, "createdAt");
        assertFalse(created.isBefore(before.minusMillis(1)) || created.isAfter(after.plusMillis(1)),
                "createdAt is the time of of(..): " + created + " not in [" + before + ", " + after + "]");
    }

    @Test
    public void of_unmeasuredValues_areEmpty() {
        Object baseline = BaselineApi.of(metrics("Unmeasured", "gatling", NaN, NaN, 15, 0.0, NaN, false));

        assertEquals(BaselineApi.p95(baseline), OptionalDouble.empty());
        assertEquals(BaselineApi.p99(baseline), OptionalDouble.empty());
        assertEquals(BaselineApi.throughput(baseline), OptionalDouble.empty());
        assertEquals(BaselineApi.mean(baseline), OptionalDouble.of(15));
        assertEquals(BaselineApi.errorRate(baseline), OptionalDouble.of(0.0), "an error rate of 0 is measured");
    }

    @Test
    public void saveThenLoad_roundTripsEveryValue_includingUnmeasured() throws IOException {
        Path dir = tempDir();
        try {
            Path file = dir.resolve("baseline.json");
            Object original = BaselineApi.of(metrics("Round Trip", "gatling", 40.25, NaN, 15.5, 0.125, NaN, false));

            BaselineApi.save(original, file);
            Object loaded = BaselineApi.load(file);

            assertEquals(BaselineApi.scenarioName(loaded), "Round Trip");
            assertEquals(BaselineApi.engine(loaded), "gatling");
            assertEquals(BaselineApi.createdAt(loaded), BaselineApi.createdAt(original), "createdAt round-trips");
            assertEquals(BaselineApi.p95(loaded), OptionalDouble.of(40.25));
            assertEquals(BaselineApi.p99(loaded), OptionalDouble.empty(), "unmeasured stays unmeasured");
            assertEquals(BaselineApi.mean(loaded), OptionalDouble.of(15.5));
            assertEquals(BaselineApi.errorRate(loaded), OptionalDouble.of(0.125));
            assertEquals(BaselineApi.throughput(loaded), OptionalDouble.empty(), "unmeasured stays unmeasured");
        } finally {
            deleteRecursive(dir);
        }
    }

    @Test
    public void save_writesExactlyTheVersionedKeys_withNullForUnmeasured() throws IOException {
        Path dir = tempDir();
        try {
            Path file = dir.resolve("baseline.json");
            Object baseline = BaselineApi.of(metrics("Keys", "jdk", 40, NaN, 15, 0.02, 100, false));

            BaselineApi.save(baseline, file);

            JsonNode json = MAPPER.readTree(Files.readString(file, StandardCharsets.UTF_8));
            assertTrue(json.isObject(), "the file is one JSON object: " + json);
            Set<String> keys = new TreeSet<>();
            for (Iterator<String> it = json.fieldNames(); it.hasNext(); ) {
                keys.add(it.next());
            }
            assertEquals(keys, new TreeSet<>(Set.of("schemaVersion", "scenarioName", "engine", "createdAt",
                    "p95", "p99", "mean", "errorRate", "throughput")), "exact keys: " + json);
            assertTrue(json.get("schemaVersion").isIntegralNumber(), "schemaVersion is an integer: " + json);
            assertEquals(json.get("schemaVersion").asInt(), 1);
            assertEquals(json.get("scenarioName").asText(), "Keys");
            assertEquals(json.get("engine").asText(), "jdk");
            assertEquals(Instant.parse(json.get("createdAt").asText()), BaselineApi.createdAt(baseline),
                    "createdAt is ISO-8601: " + json.get("createdAt"));
            assertEquals(json.get("p95").asDouble(), 40.0, 0.0);
            assertTrue(json.get("p99").isNull(), "an unmeasured value is written as null: " + json);
            assertEquals(json.get("mean").asDouble(), 15.0, 0.0);
            assertEquals(json.get("errorRate").asDouble(), 0.02, 0.0);
            assertEquals(json.get("throughput").asDouble(), 100.0, 0.0);
        } finally {
            deleteRecursive(dir);
        }
    }

    @Test
    public void load_readsAHandWrittenFile_nullAsUnmeasured() throws IOException {
        Path dir = tempDir();
        try {
            Path file = dir.resolve("committed-baseline.json");
            Files.writeString(file, "{\"schemaVersion\":1,\"scenarioName\":\"Hand\",\"engine\":\"gatling\","
                    + "\"createdAt\":\"2026-10-25T10:15:30Z\",\"p95\":120.5,\"p99\":null,\"mean\":60,"
                    + "\"errorRate\":0.0,\"throughput\":null}", StandardCharsets.UTF_8);

            Object b = BaselineApi.load(file);

            assertEquals(BaselineApi.scenarioName(b), "Hand");
            assertEquals(BaselineApi.engine(b), "gatling");
            assertEquals(BaselineApi.createdAt(b), Instant.parse("2026-10-25T10:15:30Z"));
            assertEquals(BaselineApi.p95(b), OptionalDouble.of(120.5));
            assertEquals(BaselineApi.p99(b), OptionalDouble.empty());
            assertEquals(BaselineApi.mean(b), OptionalDouble.of(60));
            assertEquals(BaselineApi.errorRate(b), OptionalDouble.of(0.0));
            assertEquals(BaselineApi.throughput(b), OptionalDouble.empty());
        } finally {
            deleteRecursive(dir);
        }
    }

    @Test
    public void save_createsParentDirectories_andLeavesNoTempFile() throws IOException {
        Path dir = tempDir();
        try {
            Path file = dir.resolve("a").resolve("b").resolve("baseline.json");
            Object baseline = BaselineApi.of(metrics("Dirs", "jdk", 40, 50, 15, 0.02, 100, false));

            BaselineApi.save(baseline, file);

            assertTrue(Files.isRegularFile(file), "save creates missing parent directories");
            assertEquals(listing(dir), List.of("a", "a/b", "a/b/baseline.json"),
                    "the atomic write (.tmp + move) leaves nothing behind");
            assertEquals(BaselineApi.scenarioName(BaselineApi.load(file)), "Dirs");
        } finally {
            deleteRecursive(dir);
        }
    }

    @Test
    public void save_replacesAnExistingFile_andLeavesNoTempFile() throws IOException {
        Path dir = tempDir();
        try {
            Path file = dir.resolve("baseline.json");
            BaselineApi.save(BaselineApi.of(metrics("First", "jdk", 40, 50, 15, 0.02, 100, false)), file);

            BaselineApi.save(BaselineApi.of(metrics("Second", "gatling", 41, 51, 16, 0.03, 101, false)), file);

            Object loaded = BaselineApi.load(file);
            assertEquals(BaselineApi.scenarioName(loaded), "Second", "the second save replaces the first");
            assertEquals(BaselineApi.p95(loaded), OptionalDouble.of(41));
            assertEquals(listing(dir), List.of("baseline.json"), "no temp file left behind");
        } finally {
            deleteRecursive(dir);
        }
    }

    @Test
    public void load_missingFile_throwsIllegalArgument() throws IOException {
        Path dir = tempDir();
        try {
            Path missing = dir.resolve("nope.json");
            IllegalArgumentException e = expectThrown(IllegalArgumentException.class,
                    () -> BaselineApi.load(missing));
            assertTrue(String.valueOf(e.getMessage()).contains("nope.json"),
                    "the message names the file: " + e.getMessage());
        } finally {
            deleteRecursive(dir);
        }
    }

    @DataProvider(name = "corrupt", parallel = true)
    public Object[][] corrupt() {
        return new Object[][] {
                {"not json at all"},
                {"{\"schemaVersion\":1,\"scenarioName\":\"Cut\",\"engine\":\"jdk\",\"p95\":"},
                {""},
                {"[1,2,3]"},
        };
    }

    @Test(dataProvider = "corrupt")
    public void load_corruptJson_throwsIllegalArgument(String content) throws IOException {
        Path dir = tempDir();
        try {
            Path file = dir.resolve("corrupt.json");
            Files.writeString(file, content, StandardCharsets.UTF_8);
            expectThrown(IllegalArgumentException.class, () -> BaselineApi.load(file));
        } finally {
            deleteRecursive(dir);
        }
    }

    @DataProvider(name = "unsupportedVersions", parallel = true)
    public Object[][] unsupportedVersions() {
        return new Object[][] {{0}, {2}, {99}};
    }

    @Test(dataProvider = "unsupportedVersions")
    public void load_unsupportedSchemaVersion_throwsIllegalArgument(int version) throws IOException {
        Path dir = tempDir();
        try {
            Path file = dir.resolve("future.json");
            Files.writeString(file, "{\"schemaVersion\":" + version + ",\"scenarioName\":\"V\",\"engine\":\"jdk\","
                    + "\"createdAt\":\"2026-10-25T10:15:30Z\",\"p95\":1,\"p99\":1,\"mean\":1,\"errorRate\":0,"
                    + "\"throughput\":1}", StandardCharsets.UTF_8);
            IllegalArgumentException e = expectThrown(IllegalArgumentException.class, () -> BaselineApi.load(file));
            assertTrue(String.valueOf(e.getMessage()).contains(String.valueOf(version)),
                    "the message names the unsupported version " + version + ": " + e.getMessage());
        } finally {
            deleteRecursive(dir);
        }
    }

    // ── Tolerance ────────────────────────────────────────────────────────

    @Test
    public void tolerance_carriesPercent_andDefaultsErrorRateDeltaToOnePoint() {
        Object tol = BaselineApi.percent(20);

        assertEquals(BaselineApi.percentOf(tol), 20.0, 0.0);
        assertEquals(BaselineApi.errorRateDeltaOf(tol), 0.01, 0.0, "default errorRateDelta is 0.01");
        Object custom = BaselineApi.errorRateDelta(tol, 0.05);
        assertEquals(BaselineApi.errorRateDeltaOf(custom), 0.05, 0.0);
        assertEquals(BaselineApi.percentOf(custom), 20.0, 0.0, "errorRateDelta keeps the percent");
        assertEquals(BaselineApi.percentOf(BaselineApi.percent(0)), 0.0, 0.0, "zero tolerance is allowed");
        assertEquals(BaselineApi.errorRateDeltaOf(BaselineApi.errorRateDelta(BaselineApi.percent(0), 0)), 0.0, 0.0,
                "a zero delta is allowed");
    }

    @DataProvider(name = "invalidTolerance", parallel = true)
    public Object[][] invalidTolerance() {
        return new Object[][] {
                {-0.0001}, {-1}, {NaN}, {Double.POSITIVE_INFINITY}, {Double.NEGATIVE_INFINITY},
        };
    }

    @Test(dataProvider = "invalidTolerance")
    public void tolerancePercent_rejectsNegativeOrNonFinite(double pct) {
        expectThrown(IllegalArgumentException.class, () -> BaselineApi.percent(pct));
    }

    @Test(dataProvider = "invalidTolerance")
    public void toleranceErrorRateDelta_rejectsNegativeOrNonFinite(double delta) {
        Object tol = BaselineApi.percent(10);
        expectThrown(IllegalArgumentException.class, () -> BaselineApi.errorRateDelta(tol, delta));
    }

    // ── regression rules at their boundaries ─────────────────────────────

    @DataProvider(name = "latencyMetrics", parallel = true)
    public Object[][] latencyMetrics() {
        return new Object[][] {{"p95"}, {"p99"}, {"mean"}};
    }

    /** Latency regresses iff actual > baseline × (1 + pct/100): baseline 100, percent(50), limit exactly 150. */
    @Test(dataProvider = "latencyMetrics")
    public void latency_regressesOnlyStrictlyAboveBaselineTimesOnePlusPercent(String metric) {
        Object baseline = baseline(with(metric, 100));
        Object tol = BaselineApi.percent(50);

        assertRegressed(compare(with(metric, 150), baseline, tol), metric, false, 100, 150);
        assertRegressed(compare(with(metric, Math.nextUp(150.0)), baseline, tol), metric, true, 100,
                Math.nextUp(150.0));
        assertRegressed(compare(with(metric, 149), baseline, tol), metric, false, 100, 149);
        assertRegressed(compare(with(metric, 1), baseline, tol), metric, false, 100, 1);
    }

    /** Throughput regresses iff actual < baseline × (1 − pct/100): baseline 100, percent(50), limit exactly 50. */
    @Test
    public void throughput_regressesOnlyStrictlyBelowBaselineTimesOneMinusPercent() {
        Object baseline = baseline(with("throughput", 100));
        Object tol = BaselineApi.percent(50);

        assertRegressed(compare(with("throughput", 50), baseline, tol), "throughput", false, 100, 50);
        assertRegressed(compare(with("throughput", Math.nextDown(50.0)), baseline, tol), "throughput", true, 100,
                Math.nextDown(50.0));
        assertRegressed(compare(with("throughput", 51), baseline, tol), "throughput", false, 100, 51);
        assertRegressed(compare(with("throughput", 10_000), baseline, tol), "throughput", false, 100, 10_000);
    }

    /** errorRate regresses iff actual > baseline + errorRateDelta: 0.25 + 0.25 = 0.5 exactly. */
    @Test
    public void errorRate_regressesOnlyStrictlyAboveBaselinePlusDelta() {
        Object baseline = baseline(with("errorRate", 0.25));
        Object tol = BaselineApi.errorRateDelta(BaselineApi.percent(50), 0.25);

        assertRegressed(compare(with("errorRate", 0.5), baseline, tol), "errorRate", false, 0.25, 0.5);
        assertRegressed(compare(with("errorRate", Math.nextUp(0.5)), baseline, tol), "errorRate", true, 0.25,
                Math.nextUp(0.5));
        assertRegressed(compare(with("errorRate", 0.0), baseline, tol), "errorRate", false, 0.25, 0.0);
    }

    @Test
    public void errorRate_defaultDeltaIsOnePoint_andThePercentDoesNotApply() {
        Object baseline = baseline(with("errorRate", 0.0));

        // percent(1000) would allow anything if it applied to the error rate; only the 0.01 delta does
        Object generous = BaselineApi.percent(1000);
        assertRegressed(compare(with("errorRate", 0.01), baseline, generous), "errorRate", false, 0.0, 0.01);
        assertRegressed(compare(with("errorRate", Math.nextUp(0.01)), baseline, generous), "errorRate", true, 0.0,
                Math.nextUp(0.01));
        assertRegressed(compare(with("errorRate", 0.05), baseline, generous), "errorRate", true, 0.0, 0.05);
    }

    @Test
    public void zeroTolerance_equalValuesDoNotRegress() {
        double[] values = {40, 50, 15, 0.02, 100};
        Object baseline = baseline(values);
        Object exact = BaselineApi.errorRateDelta(BaselineApi.percent(0), 0);

        List<?> results = compare(values, baseline, exact);

        for (Object r : results) {
            assertFalse(BaselineApi.regressed(r), "equal to the baseline is not a regression: " + r);
        }
    }

    // ── result shape ─────────────────────────────────────────────────────

    @Test
    public void regressionResults_oneResultPerMetric_inOrder_withBaselineAndActual() {
        Object baseline = baseline(new double[] {40, 50, 15, 0.02, 100});
        double[] actual = {41, 51, 16, 0.025, 99};

        List<?> results = compare(actual, baseline, BaselineApi.percent(10));

        List<String> names = new ArrayList<>();
        for (Object r : results) {
            names.add(BaselineApi.metric(r));
        }
        assertEquals(names, METRICS, "one result per compared metric, in this order");
        double[] base = {40, 50, 15, 0.02, 100};
        for (int i = 0; i < METRICS.size(); i++) {
            Object r = results.get(i);
            assertEquals(BaselineApi.baseline(r), base[i], 0.0, "baseline: " + r);
            assertEquals(BaselineApi.actual(r), actual[i], 0.0, "actual: " + r);
            assertFalse(BaselineApi.regressed(r), "within tolerance: " + r);
            assertNull(BaselineApi.reason(r), "a compared, non-regressed metric has no reason: " + r);
        }
    }

    @Test
    public void regressionResult_isARecord_withTheSpecifiedComponents() {
        Class<?> type = BaselineApi.type(BaselineApi.REGRESSION_RESULT);
        assertTrue(type.isRecord(), "RegressionResult is a record");
        List<String> components = new ArrayList<>();
        for (var c : type.getRecordComponents()) {
            components.add(c.getType().getSimpleName() + " " + c.getName());
        }
        assertEquals(components, List.of("String metric", "double baseline", "double actual", "boolean regressed",
                "String reason"));
        assertEquals(BaselineApi.regressionResult("p95", 1, 2, true, "x"),
                BaselineApi.regressionResult("p95", 1, 2, true, "x"));
    }

    @Test
    public void regressedMetric_hasAReason_otherThanNotCompared() {
        Object baseline = baseline(with("p95", 100));

        Object r = BaselineApi.resultFor(compare(with("p95", 400), baseline, BaselineApi.percent(50)), "p95");

        assertTrue(BaselineApi.regressed(r), r.toString());
        assertNotNull(BaselineApi.reason(r), "a regression has a reason: " + r);
        assertNotEquals(BaselineApi.reason(r), BaselineApi.NOT_COMPARED);
    }

    @Test
    public void regressionResults_doesNotThrow_whenMetricsRegressed() {
        Object baseline = baseline(new double[] {40, 50, 15, 0.0, 100});
        double[] worse = {400, 500, 150, 0.5, 1};

        List<?> results = compare(worse, baseline, BaselineApi.percent(10));

        for (Object r : results) {
            assertTrue(BaselineApi.regressed(r), "every metric regressed: " + r);
        }
    }

    // ── unmeasured on either side ────────────────────────────────────────

    @Test(dataProvider = "latencyAndOtherMetrics")
    public void unmeasuredInBaseline_isNotCompared_evenWhenActualIsHuge(String metric) {
        Object baseline = baseline(with(metric, NaN));
        double huge = "throughput".equals(metric) ? 0.0001 : ("errorRate".equals(metric) ? 1.0 : 1e9);

        Object r = BaselineApi.resultFor(compare(with(metric, huge), baseline, BaselineApi.percent(0)), metric);

        assertFalse(BaselineApi.regressed(r), "not compared, so not regressed: " + r);
        assertEquals(BaselineApi.reason(r), BaselineApi.NOT_COMPARED);
        assertTrue(Double.isNaN(BaselineApi.baseline(r)), "the unmeasured side stays NaN: " + r);
        assertEquals(BaselineApi.actual(r), huge, 0.0, "the measured side is carried: " + r);
    }

    @Test(dataProvider = "latencyAndOtherMetrics")
    public void unmeasuredInActual_isNotCompared(String metric) {
        Object baseline = baseline(with(metric, 10));

        Object r = BaselineApi.resultFor(compare(with(metric, NaN), baseline, BaselineApi.percent(0)), metric);

        assertFalse(BaselineApi.regressed(r), "not compared, so not regressed: " + r);
        assertEquals(BaselineApi.reason(r), BaselineApi.NOT_COMPARED);
        assertEquals(BaselineApi.baseline(r), 10.0, 0.0, "the measured side is carried: " + r);
        assertTrue(Double.isNaN(BaselineApi.actual(r)), "the unmeasured side stays NaN: " + r);
    }

    @DataProvider(name = "latencyAndOtherMetrics", parallel = true)
    public Object[][] latencyAndOtherMetrics() {
        return METRICS.stream().map(m -> new Object[] {m}).toArray(Object[][]::new);
    }

    @Test
    public void unmeasuredInALoadedBaselineFile_isNotCompared() throws IOException {
        Path dir = tempDir();
        try {
            Path file = dir.resolve("baseline.json");
            Files.writeString(file, "{\"schemaVersion\":1,\"scenarioName\":\"S\",\"engine\":\"jdk\","
                    + "\"createdAt\":\"2026-10-25T10:15:30Z\",\"p95\":40,\"p99\":null,\"mean\":15,"
                    + "\"errorRate\":0.02,\"throughput\":100}", StandardCharsets.UTF_8);

            List<?> results = compare(new double[] {40, 1e9, 15, 0.02, 100}, BaselineApi.load(file),
                    BaselineApi.percent(0));

            Object p99 = BaselineApi.resultFor(results, "p99");
            assertFalse(BaselineApi.regressed(p99), p99.toString());
            assertEquals(BaselineApi.reason(p99), BaselineApi.NOT_COMPARED);
        } finally {
            deleteRecursive(dir);
        }
    }

    @Test
    public void assertNoRegression_withOnlyUnmeasuredDifferences_returnsThis() {
        Object baseline = baseline(new double[] {40, NaN, 15, 0.02, NaN});
        LoadTestAssert a = new LoadTestAssert(metrics("Same", "jdk", 40, 1e9, 15, 0.02, 0.0001, false));

        Object returned = BaselineApi.assertNoRegression(a, baseline, BaselineApi.percent(0));

        assertSame(returned, a, "nothing regressed: assertNoRegression returns this");
    }

    // ── assertNoRegression outcome ───────────────────────────────────────

    @Test
    public void assertNoRegression_nothingRegressed_returnsThis() {
        Object baseline = baseline(new double[] {40, 50, 15, 0.02, 100});
        LoadTestAssert a = new LoadTestAssert(metrics("Ok", "jdk", 44, 55, 16, 0.025, 95, false));

        Object returned = BaselineApi.assertNoRegression(a, baseline, BaselineApi.percent(10));

        assertSame(returned, a, "assertNoRegression is fluent");
    }

    @Test
    public void assertNoRegression_listsEveryRegressedMetric_withBaselineActualAndLimit() {
        // percent(50): p95 102 -> limit 153; mean 22 -> limit 33; throughput 82 -> limit 41;
        // errorRateDelta(0.25): errorRate 0.25 -> limit 0.5. p99 is within tolerance.
        Object baseline = baseline(new double[] {102, 50, 22, 0.25, 82});
        LoadTestAssert a = new LoadTestAssert(metrics("Regressed", "jdk", 404, 51, 97, 0.75, 11, false));
        Object tol = BaselineApi.errorRateDelta(BaselineApi.percent(50), 0.25);

        AssertionError e = expectThrown(AssertionError.class, () -> BaselineApi.assertNoRegression(a, baseline, tol));

        String msg = String.valueOf(e.getMessage());
        assertTrue(msg.contains("Regressed"), "names the scenario: " + msg);
        for (String expected : List.of(
                "p95", "102", "404", "153",
                "mean", "22", "97", "33",
                "throughput", "82", "11", "41",
                "errorRate", "0.25", "0.75", "0.5")) {
            assertTrue(msg.contains(expected), "the message must contain '" + expected + "': " + msg);
        }
    }

    @Test
    public void assertNoRegression_listsNotComparedMetrics_andNeverAsPassed() {
        Object baseline = baseline(new double[] {100, NaN, 15, 0.02, 100});
        LoadTestAssert a = new LoadTestAssert(metrics("Mixed", "jdk", 400, 50, 15, 0.02, 100, false));

        AssertionError e = expectThrown(AssertionError.class,
                () -> BaselineApi.assertNoRegression(a, baseline, BaselineApi.percent(50)));

        String msg = String.valueOf(e.getMessage());
        assertTrue(msg.contains("p95"), "the regression is listed: " + msg);
        assertTrue(msg.contains("p99"), "the unmeasured metric is listed: " + msg);
        assertTrue(msg.contains("not compared"), "the unmeasured metric is listed as not compared: " + msg);
    }

    @Test
    public void assertNoRegression_onPartialMetrics_failsAsPartial_evenWithAGenerousTolerance() {
        Object baseline = baseline(new double[] {40, 50, 15, 0.02, 100});
        LoadTestAssert a = new LoadTestAssert(metrics("Partial", "gatling", 40, 50, 15, 0.02, 100, true));

        AssertionError e = expectThrown(AssertionError.class,
                () -> BaselineApi.assertNoRegression(a, baseline, BaselineApi.percent(1000)));

        assertTrue(String.valueOf(e.getMessage()).contains("metrics are partial"),
                "partial metrics fail the regression check: " + e.getMessage());
    }

    // ── engine mismatch ──────────────────────────────────────────────────

    @Test
    public void engineMismatch_comparisonContinues() {
        Object gatlingBaseline = BaselineApi.of(metrics("Engines", "gatling", 100, 100, 100, 0.0, 100, false));

        LoadTestAssert within = new LoadTestAssert(metrics("Engines", "jdk", 110, 110, 110, 0.0, 90, false));
        assertSame(BaselineApi.assertNoRegression(within, gatlingBaseline, BaselineApi.percent(50)), within,
                "an engine mismatch is a warning, not a failure");

        LoadTestAssert worse = new LoadTestAssert(metrics("Engines", "jdk", 400, 110, 110, 0.0, 90, false));
        List<?> results = BaselineApi.regressionResults(worse, gatlingBaseline, BaselineApi.percent(50));
        assertEquals(results.size(), METRICS.size(), "every metric is still compared: " + results);
        assertTrue(BaselineApi.regressed(BaselineApi.resultFor(results, "p95")), results.toString());
        assertFalse(BaselineApi.regressed(BaselineApi.resultFor(results, "p99")), results.toString());
        AssertionError e = expectThrown(AssertionError.class,
                () -> BaselineApi.assertNoRegression(worse, gatlingBaseline, BaselineApi.percent(50)));
        assertTrue(String.valueOf(e.getMessage()).contains("p95"), "fails on the regression: " + e.getMessage());
    }

    // ── helpers ──────────────────────────────────────────────────────────

    /**
     * Runs {@code call} and returns the {@code expected} throwable it throws. Unlike TestNG's {@code expectThrows},
     * any other throwable (notably the {@link UnsupportedOperationException} of a missing API) propagates
     * unchanged, so a red test fails because the API is absent, not with an {@link AssertionError}.
     */
    private static <T extends Throwable> T expectThrown(Class<T> expected, Runnable call) {
        try {
            call.run();
        } catch (Throwable t) {
            if (expected.isInstance(t)) {
                return expected.cast(t);
            }
            throw t;
        }
        throw new AssertionError("expected " + expected.getSimpleName() + " to be thrown, but nothing was thrown");
    }

    /** {@code {p95, p99, mean, errorRate, throughput}} with every value 10 (errorRate 0.1) except {@code metric}. */
    private static double[] with(String metric, double value) {
        double[] v = {10, 10, 10, 0.1, 10};
        v[METRICS.indexOf(metric)] = value;
        return v;
    }

    private static Object baseline(double[] v) {
        return BaselineApi.of(metrics("Baseline", "jdk", v[0], v[1], v[2], v[3], v[4], false));
    }

    private static List<?> compare(double[] actual, Object baseline, Object tolerance) {
        LoadTestAssert a = new LoadTestAssert(
                metrics("Actual", "jdk", actual[0], actual[1], actual[2], actual[3], actual[4], false));
        return BaselineApi.regressionResults(a, baseline, tolerance);
    }

    private static void assertRegressed(List<?> results, String metric, boolean regressed, double baseline,
            double actual) {
        Object r = BaselineApi.resultFor(results, metric);
        assertEquals(BaselineApi.regressed(r), regressed, "regressed: " + r);
        assertEquals(BaselineApi.baseline(r), baseline, 0.0, "baseline: " + r);
        assertEquals(BaselineApi.actual(r), actual, 0.0, "actual: " + r);
        for (Object other : results) {
            if (!metric.equals(BaselineApi.metric(other))) {
                assertFalse(BaselineApi.regressed(other), "only " + metric + " differs: " + other);
            }
        }
    }

    /** Complete or partial metrics with the given compared values; the other components are fixed. */
    private static LoadTestMetrics metrics(String name, String engine, double p95, double p99, double mean,
            double errorRate, double throughput, boolean partial) {
        return new LoadTestMetrics(name, 100, 98, 2, throughput, mean, 10, 30, p95, p99, 1, 1e9, errorRate,
                Map.of(200, 98L, 500, 2L), 1000, 5, engine, Map.of(), 20, partial);
    }

    private static Path tempDir() throws IOException {
        return Files.createTempDirectory("testfly-baseline-unit-");
    }

    /** Relative paths under {@code dir}, sorted, with '/' separators. */
    private static List<String> listing(Path dir) throws IOException {
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(p -> !p.equals(dir))
                    .map(p -> dir.relativize(p).toString().replace('\\', '/'))
                    .sorted()
                    .toList();
        }
    }

    private static void deleteRecursive(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> files = Files.walk(dir)) {
            files.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
