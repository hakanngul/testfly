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

import static org.testng.Assert.*;

/**
 * Tests {@link GatlingResultsParser} with synthetic run directories: TestFly's metrics sidecar is the only
 * source; Gatling's report files and {@code simulation.log} are never read.
 */
@Test(singleThreaded = true)
public class GatlingResultsParserTest {

    @Test
    public void testParseMissingDirectory() {
        LoadTestMetrics metrics = GatlingResultsParser.parse(
                "target/nonexistent-gatling-results", "Test", 10);

        assertNotNull(metrics);
        assertEquals(metrics.scenarioName(), "Test");
        assertEquals(metrics.totalRequests(), 0);
        assertEquals(metrics.engine(), "none");
    }

    @Test
    public void testParseEmptyDirectory() throws IOException {
        Path tempDir = Files.createTempDirectory("gatling-empty");
        try {
            LoadTestMetrics metrics = GatlingResultsParser.parse(
                    tempDir.toString(), "Empty", 5);

            assertEquals(metrics.totalRequests(), 0);
        } finally {
            deleteRecursive(tempDir.toFile());
        }
    }

    @Test
    public void testSimulationLogIsNeverParsed() throws IOException {
        // simulation.log is a binary, internal format since Gatling 3.13: no text fallback, no fabricated data
        Path tempDir = Files.createTempDirectory("gatling-sim");
        Path runDir = tempDir.resolve("mysimulation-20260907");
        Files.createDirectories(runDir);
        String log = "REQUEST\tmysimulation\t1\t1725724800100\t1725724800250\tOK\t200\n";
        // both in the dir passed to parse and in a Gatling simulation subdirectory
        Files.writeString(tempDir.resolve("simulation.log"), log);
        Files.writeString(runDir.resolve("simulation.log"), log);
        try {
            LoadTestMetrics metrics = GatlingResultsParser.parse(tempDir.toString(), "SimLog Test", 2);

            assertEquals(metrics.totalRequests(), 0);
            assertEquals(metrics.engine(), "none");
            assertTrue(metrics.statusCodes().isEmpty());
        } finally {
            deleteRecursive(tempDir.toFile());
        }
    }

    @Test
    public void testGatlingReportStatsAreNeverRead() throws IOException {
        // ADR 0004: js/stats.js (gone in Gatling 3.14+) and stats.json are not a metrics source
        Path tempDir = Files.createTempDirectory("gatling-stats");
        String statsJson = "{\"stats\":{\"numberOfRequests\":{\"total\":1000,\"ko\":10}}}";
        String statsJs = "var stats = {stats: {\"numberOfRequests\": {\"total\": \"500\", \"ko\": \"10\"}}}\n"
                + "function fillStats(stat){}\n";
        // directly in the dir passed to parse, and in a Gatling simulation subdirectory
        for (Path dir : new Path[] {tempDir, tempDir.resolve("mysimulation-20260907")}) {
            Files.createDirectories(dir.resolve("js"));
            Files.writeString(dir.resolve("stats.json"), statsJson);
            Files.writeString(dir.resolve("js").resolve("stats.json"), statsJson);
            Files.writeString(dir.resolve("js").resolve("stats.js"), statsJs);
            Files.writeString(dir.resolve("stats.js"), statsJs);
        }
        try {
            LoadTestMetrics metrics = GatlingResultsParser.parse(tempDir.toString(), "Stats Test", 50);

            assertEquals(metrics.totalRequests(), 0);
            assertEquals(metrics.engine(), "none");
        } finally {
            deleteRecursive(tempDir.toFile());
        }
    }

    // ── Metrics sidecar (testfly-metrics.json) ───────────────────────────

    private static Path writeSidecar(Path base, String requestsJson) throws IOException {
        Files.writeString(base.resolve(GatlingResultsParser.METRICS_SIDECAR_FILE),
                "{\"schemaVersion\":1,\"startMs\":1000,\"endMs\":3000,\"requests\":" + requestsJson + "}");
        return base;
    }

    @Test
    public void testSidecarStatusCodesAndMeasuredP90() throws IOException {
        Path base = writeSidecar(Files.createTempDirectory("gatling-sidecar"),
                "{\"create\":{\"attempts\":12,\"ok\":12,\"ko\":0,\"status\":{\"201\":12},"
                        + "\"latencyMs\":{\"3\":6,\"4\":3,\"9\":2,\"20\":1}}}");
        try {
            LoadTestMetrics m = GatlingResultsParser.parse(base.toString(), "Rel201", 2);

            assertEquals(m.engine(), "gatling");
            assertEquals(m.users(), 2);
            assertEquals(m.totalRequests(), 12);
            assertEquals(m.statusCodes(), Map.of(201, 12L), "real 201s, not a fabricated 200");
            assertTrue(m.statusCodesMeasured());
            assertFalse(m.partial());
            assertEquals(m.durationMs(), 2000L);
            assertEquals(m.throughputRps(), 6.0, 1e-9);
            // samples 3x6, 4x3, 9x2, 20x1; rank = p/100 * 11
            assertEquals(m.p50LatencyMs(), 3.5, 1e-9);
            assertEquals(m.p75LatencyMs(), 4.0 + 0.25 * 5, 1e-9);
            assertTrue(LoadTestMetrics.isMeasured(m.p90LatencyMs()), "p90 is measured from the histogram");
            assertEquals(m.p90LatencyMs(), 9.0, 1e-9);
            assertEquals(m.p95LatencyMs(), 9.0 + 0.45 * 11, 1e-9);
            assertEquals(m.minLatencyMs(), 3.0);
            assertEquals(m.maxLatencyMs(), 20.0);
            assertEquals(m.meanLatencyMs(), (18 + 12 + 18 + 20) / 12.0, 1e-9);
        } finally {
            deleteRecursive(base.toFile());
        }
    }

    @Test
    public void testSidecarSumsRequestNamesAndReportsNoResponseAsMinusOne() throws IOException {
        // a: 2 responses (200 with a failed check + 500) of 4; b: 2 responses of 6 → 6 without any response
        Path base = writeSidecar(Files.createTempDirectory("gatling-sidecar-ko"),
                "{\"a\":{\"attempts\":4,\"ok\":1,\"ko\":3,\"status\":{\"200\":1,\"500\":1},\"latencyMs\":{\"5\":2}},"
                        + "\"b\":{\"attempts\":6,\"ok\":2,\"ko\":4,\"status\":{\"200\":2},\"latencyMs\":{\"7\":2}}}");
        try {
            LoadTestMetrics m = GatlingResultsParser.parse(base.toString(), "Mixed", 2);

            assertEquals(m.statusCodes(), Map.of(200, 3L, 500, 1L, JdkLoadEngine.TRANSPORT_ERROR_STATUS, 6L));
            long sum = m.statusCodes().values().stream().mapToLong(Long::longValue).sum();
            assertEquals(sum, (long) m.totalRequests(), "status counts must add up to the request total");
        } finally {
            deleteRecursive(base.toFile());
        }
    }

    @Test
    public void testRedirectHopStatusesAreCountedButAreNotSteps() throws IOException {
        Path base = writeSidecar(Files.createTempDirectory("gatling-sidecar-hop"),
                "{\"a\":{\"attempts\":4,\"ok\":4,\"ko\":0,\"status\":{\"200\":2,\"302\":2},\"latencyMs\":{\"5\":4}},"
                        + "\"a Redirect 1\":{\"status\":{\"200\":2},\"latencyMs\":{\"900\":2}}}");
        try {
            LoadTestMetrics m = GatlingResultsParser.parse(base.toString(), "Over", 1);

            assertEquals(m.totalRequests(), 4);
            assertEquals(m.statusCodes(), Map.of(200, 4L, 302, 2L));
            assertFalse(m.statusCodes().containsKey(JdkLoadEngine.TRANSPORT_ERROR_STATUS));
            assertEquals(m.steps().keySet(), java.util.Set.of("a"));
            assertEquals(m.maxLatencyMs(), 5.0, "a redirect hop is not a latency sample");
        } finally {
            deleteRecursive(base.toFile());
        }
    }

    @Test
    public void testHistogramPercentilesEqualLinearInterpolationOnExpandedSamples() throws IOException {
        java.util.Random random = new java.util.Random(42);
        for (int round = 0; round < 25; round++) {
            int n = 1 + random.nextInt(400);
            double[] samples = new double[n];
            Map<Long, Long> histogram = new java.util.TreeMap<>();
            for (int i = 0; i < n; i++) {
                long ms = random.nextInt(round % 2 == 0 ? 30 : 2000);
                samples[i] = ms;
                histogram.merge(ms, 1L, Long::sum);
            }
            java.util.Arrays.sort(samples);
            StringBuilder latency = new StringBuilder();
            histogram.forEach((ms, c) -> latency.append(latency.length() == 0 ? "" : ",")
                    .append('"').append(ms).append("\":").append(c));
            Path base = writeSidecar(Files.createTempDirectory("gatling-hist"),
                    "{\"s\":{\"attempts\":" + n + ",\"ok\":" + n + ",\"ko\":0,\"status\":{\"200\":" + n
                            + "},\"latencyMs\":{" + latency + "}}}");
            try {
                LoadTestMetrics m = GatlingResultsParser.parse(base.toString(), "Hist", 1);
                String ctx = "round " + round + ", n=" + n;
                assertEquals(m.p50LatencyMs(), PercentileCalculator.linearInterpolation(samples, 50), 1e-9, ctx);
                assertEquals(m.p75LatencyMs(), PercentileCalculator.linearInterpolation(samples, 75), 1e-9, ctx);
                assertEquals(m.p90LatencyMs(), PercentileCalculator.linearInterpolation(samples, 90), 1e-9, ctx);
                assertEquals(m.p95LatencyMs(), PercentileCalculator.linearInterpolation(samples, 95), 1e-9, ctx);
                assertEquals(m.p99LatencyMs(), PercentileCalculator.linearInterpolation(samples, 99), 1e-9, ctx);
                assertEquals(m.step("s").p95LatencyMs(),
                        PercentileCalculator.linearInterpolation(samples, 95), 1e-9, ctx);
                assertEquals(m.minLatencyMs(), samples[0], ctx);
                assertEquals(m.maxLatencyMs(), samples[n - 1], ctx);
                assertEquals(m.meanLatencyMs(), PercentileCalculator.mean(samples), 1e-9, ctx);
            } finally {
                deleteRecursive(base.toFile());
            }
        }
    }

    @Test
    public void testMissingCorruptOrUnsupportedSidecarParsesAsEmpty() throws IOException {
        Path base = Files.createTempDirectory("gatling-nosidecar");
        try {
            LoadTestMetrics missing = GatlingResultsParser.parse(base.toString(), "NoSidecar", 1);
            assertEquals(missing.totalRequests(), 0);
            assertEquals(missing.engine(), "none");

            Files.writeString(base.resolve(GatlingResultsParser.METRICS_SIDECAR_FILE), "not json {");
            assertEquals(GatlingResultsParser.parse(base.toString(), "Corrupt", 1).engine(), "none");

            Files.writeString(base.resolve(GatlingResultsParser.METRICS_SIDECAR_FILE),
                    "{\"schemaVersion\":3,\"requests\":{\"s\":{\"attempts\":1,\"ok\":1,\"ko\":0}}}");
            assertEquals(GatlingResultsParser.parse(base.toString(), "Future", 1).engine(), "none",
                    "an unknown schemaVersion must not be misread");
        } finally {
            deleteRecursive(base.toFile());
        }
    }

    @Test
    public void testEngineFailsTheRunOnMissingOrCorruptSidecar() throws Exception {
        Path base = Files.createTempDirectory("gatling-engine-sidecar");
        Path sidecar = base.resolve(GatlingResultsParser.METRICS_SIDECAR_FILE);
        java.lang.reflect.Method read = io.testfly.loadtest.internal.GatlingEngine.class
                .getDeclaredMethod("readMetrics", Path.class, String.class, int.class);
        read.setAccessible(true);
        try {
            for (String content : new String[] {null, "not json {"}) {
                if (content != null) {
                    Files.writeString(sidecar, content);
                }
                try {
                    read.invoke(null, sidecar, "Missing", 1);
                    fail("a missing or unreadable sidecar must fail the run, not return empty metrics");
                } catch (java.lang.reflect.InvocationTargetException e) {
                    assertTrue(e.getCause() instanceof IllegalStateException, "was " + e.getCause());
                    assertTrue(e.getCause().getMessage().startsWith(
                            "[LoadTest] Gatling metrics sidecar missing: " + sidecar), e.getCause().getMessage());
                }
            }
        } finally {
            deleteRecursive(base.toFile());
        }
    }

    @Test
    public void testStepsKeepSidecarOrder() throws IOException {
        // the simulation writes steps in declaration order, then redirect hops
        Path base = writeSidecar(Files.createTempDirectory("gatling-order"),
                "{\"zeta\":{\"attempts\":1,\"ok\":1,\"ko\":0,\"status\":{\"200\":1},\"latencyMs\":{\"1\":1}},"
                        + "\"alpha\":{\"attempts\":1,\"ok\":1,\"ko\":0,\"status\":{\"302\":1},\"latencyMs\":{\"1\":1}},"
                        + "\"mid\":{\"attempts\":1,\"ok\":1,\"ko\":0,\"status\":{\"200\":1},\"latencyMs\":{\"1\":1}},"
                        + "\"alpha Redirect 1\":{\"status\":{\"200\":1},\"latencyMs\":{\"1\":1}}}");
        try {
            LoadTestMetrics m = GatlingResultsParser.parse(base.toString(), "Order", 1);
            assertEquals(new java.util.ArrayList<>(m.steps().keySet()), java.util.List.of("zeta", "alpha", "mid"));
        } finally {
            deleteRecursive(base.toFile());
        }
    }

    @Test
    public void testMissingStartMeansDurationAndThroughputNotMeasured() throws IOException {
        Path base = Files.createTempDirectory("gatling-nostart");
        Files.writeString(base.resolve(GatlingResultsParser.METRICS_SIDECAR_FILE),
                "{\"schemaVersion\":1,\"endMs\":5000,\"requests\":{\"s\":{\"attempts\":3,\"ok\":3,\"ko\":0,"
                        + "\"status\":{\"200\":3},\"latencyMs\":{\"4\":3}}}}");
        try {
            LoadTestMetrics m = GatlingResultsParser.parse(base.toString(), "NoStart", 1);

            assertEquals(m.totalRequests(), 3);
            assertEquals(m.durationMs(), 0L);
            assertTrue(Double.isNaN(m.throughputRps()), "throughput must be NaN, was " + m.throughputRps());
            assertFalse(m.partial(), "counts are complete");
            io.testfly.loadtest.LoadTestAssert a = new io.testfly.loadtest.LoadTestAssert(m);
            for (Runnable r : new Runnable[] {() -> a.assertThroughputAbove(0), () -> a.assertThroughputBelow(1e9)}) {
                AssertionError e = expectThrows(AssertionError.class, r::run);
                assertTrue(e.getMessage().contains("throughput was not measured"), e.getMessage());
            }
            a.assertP95Below(1e9).assertErrorRateBelow(0.5);
        } finally {
            deleteRecursive(base.toFile());
        }
    }

    @Test
    public void testRunStoppedByMaxDurationIsPartial() throws IOException {
        Path base = Files.createTempDirectory("gatling-maxduration");
        String requests = "\"requests\":{\"s\":{\"attempts\":3,\"ok\":3,\"ko\":0,"
                + "\"status\":{\"200\":3},\"latencyMs\":{\"4\":3}}}}";
        try {
            // complete outcomes, but the run lasted until maxDuration (hang guard): not the configured run
            Files.writeString(base.resolve(GatlingResultsParser.METRICS_SIDECAR_FILE),
                    "{\"schemaVersion\":1,\"startMs\":1000,\"endMs\":61000,\"maxDurationMs\":60000," + requests);
            LoadTestMetrics stopped = GatlingResultsParser.parse(base.toString(), "Stopped", 1);
            assertTrue(stopped.partial(), "a run stopped by maxDuration must be partial");
            AssertionError e = expectThrows(AssertionError.class,
                    () -> new io.testfly.loadtest.LoadTestAssert(stopped).assertP95Below(1e9));
            assertTrue(e.getMessage().contains("metrics are partial"), e.getMessage());

            Files.writeString(base.resolve(GatlingResultsParser.METRICS_SIDECAR_FILE),
                    "{\"schemaVersion\":1,\"startMs\":1000,\"endMs\":31000,\"maxDurationMs\":60000," + requests);
            assertFalse(GatlingResultsParser.parse(base.toString(), "InTime", 1).partial(),
                    "a run that ended well before maxDuration is complete");
        } finally {
            deleteRecursive(base.toFile());
        }
    }

    @Test
    public void testMaxDurationCoversOneFullIterationAfterRampUpAndHold() throws Exception {
        Class<?> type = Class.forName("io.testfly.loadtest.internal.GatlingRunConfig$ScenarioConfig");
        java.lang.reflect.Constructor<?> ctor = type.getDeclaredConstructor();
        ctor.setAccessible(true);
        java.lang.reflect.Method max = type.getDeclaredMethod("maxDurationSeconds");
        max.setAccessible(true);
        Class<?> stepType = Class.forName("io.testfly.loadtest.internal.GatlingRunConfig$GatlingStep");

        Object cfg = ctor.newInstance();
        field(type, "rampUpSeconds").setLong(cfg, 10);
        field(type, "holdSeconds").setLong(cfg, 60);
        field(type, "requestTimeoutSeconds").setInt(cfg, 5);
        @SuppressWarnings("unchecked")
        java.util.List<Object> steps = (java.util.List<Object>) field(type, "steps").get(cfg);
        for (int i = 0; i < 4; i++) {
            java.lang.reflect.Constructor<?> stepCtor = stepType.getDeclaredConstructor();
            stepCtor.setAccessible(true);
            steps.add(stepCtor.newInstance());
        }
        // no think time: 10 + 60 + 4 x 5 + 30
        assertEquals(max.invoke(cfg), 120L);

        field(type, "thinkTimeMinMs").setLong(cfg, 500);
        field(type, "thinkTimeMaxMs").setLong(cfg, 2500);
        // random think time uses its maximum: 10 + 60 + ceil(4 x 7.5) + 30
        assertEquals(max.invoke(cfg), 130L);

        field(type, "thinkTimeFixedMs").setLong(cfg, 1200);
        // fixed think time wins: 10 + 60 + ceil(4 x 6.2 = 24.8) + 30
        assertEquals(max.invoke(cfg), 125L);

        steps.clear();
        field(type, "thinkTimeFixedMs").setLong(cfg, -1);
        field(type, "thinkTimeMinMs").setLong(cfg, -1);
        field(type, "thinkTimeMaxMs").setLong(cfg, -1);
        field(type, "requestTimeoutSeconds").setInt(cfg, 0);
        // no steps still counts the fallback request; timeout at least 1s: 10 + 60 + 1 + 30
        assertEquals(max.invoke(cfg), 101L);
    }

    /** Fields of the package-private {@code GatlingRunConfig.ScenarioConfig}, made accessible for the formula test. */
    private static java.lang.reflect.Field field(Class<?> type, String name) throws NoSuchFieldException {
        java.lang.reflect.Field f = type.getField(name);
        f.setAccessible(true);
        return f;
    }

    private void deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursive(child);
            }
        }
        file.delete();
    }
}
