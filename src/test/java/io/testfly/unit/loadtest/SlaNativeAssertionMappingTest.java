package io.testfly.unit.loadtest;

import io.gatling.javaapi.core.Assertion;
import io.testfly.loadtest.LoadSla;
import io.testfly.loadtest.LoadTestMetrics;
import io.testfly.loadtest.SlaResult;
import io.testfly.loadtest.internal.GatlingBridge;
import io.testfly.loadtest.internal.GatlingResultsParser;
import io.testfly.loadtest.internal.SlaEvaluator;
import org.testng.SkipException;
import org.testng.annotations.Test;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static io.gatling.javaapi.core.CoreDsl.details;
import static io.gatling.javaapi.core.CoreDsl.global;
import static org.testng.Assert.*;

/**
 * SLA internals of ADR 0004 decision 5 (Phase 3): the native Gatling assertions built from SLA rules (report only),
 * compared with Gatling's own DSL through {@code asScala()}, and the step p99 that {@code StepSla.p99Below} needs.
 */
public class SlaNativeAssertionMappingTest {

    @Test
    public void nativeAssertions_mapEveryMetric_roundingMillisecondThresholdsUp() throws Exception {
        if (!GatlingBridge.isAvailable()) {
            throw new SkipException("Gatling is not on the classpath");
        }
        List<Object> rules = List.of(
                assertion(null, "P95", "BELOW", 40.2),
                assertion(null, "P50", "BELOW", 10),
                assertion("Buy :: pay", "P99", "BELOW", 800),
                assertion(null, "MEAN", "BELOW", 15.5),
                assertion(null, "MAX", "BELOW", 1000),
                assertion("Buy :: pay", "ERROR_RATE", "BELOW", 0.015),
                assertion(null, "THROUGHPUT", "ABOVE", 2.5));

        Class<?> sim = Class.forName("io.testfly.loadtest.internal.TestFlyGatlingSimulation");
        Method build = sim.getDeclaredMethod("nativeAssertions", List.class);
        build.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Assertion> actual = (List<Assertion>) build.invoke(null, rules);

        List<Assertion> expected = List.of(
                global().responseTime().percentile(95.0).lt(41),   // 40.2 ms → lt(41): same verdict on whole ms
                global().responseTime().percentile(50.0).lt(10),
                details("Buy :: pay").responseTime().percentile(99.0).lt(800),
                global().responseTime().mean().lt(16),
                global().responseTime().max().lt(1000),
                details("Buy :: pay").failedRequests().percent().lt(1.5),
                global().requestsPerSec().gt(2.5));
        assertEquals(actual.size(), expected.size());
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(scala(actual.get(i)), scala(expected.get(i)), "assertion " + i);
        }
    }

    @Test
    public void stepMetrics_previousConstructor_hasUnmeasuredP99() {
        LoadTestMetrics.StepMetrics old = new LoadTestMetrics.StepMetrics("s", 1, 1, 0, 5, 0);
        assertTrue(Double.isNaN(old.p99LatencyMs()), "6-arg constructor: p99 not measured");
        assertEquals(new LoadTestMetrics.StepMetrics("s", 1, 1, 0, 5, 0, 9).p99LatencyMs(), 9.0);
    }

    @Test
    public void stepP99_isFilledFromTheGatlingHistogram_andEvaluatedByStepSla() throws Exception {
        Path dir = Files.createTempDirectory("sla-step-p99");
        try {
            // samples 10 x 9 and 100 x 1: rank .99 * 9 = 8.91 → 10 + .91 * 90 = 91.9
            Files.writeString(dir.resolve(GatlingResultsParser.METRICS_SIDECAR_FILE),
                    "{\"schemaVersion\":2,\"startMs\":1000,\"endMs\":2000,\"scenarios\":{\"S\":[\"pay\"]},"
                            + "\"requests\":{\"pay\":{\"attempts\":10,\"ok\":10,\"ko\":0,\"status\":{\"200\":10},"
                            + "\"latencyMs\":{\"10\":9,\"100\":1}}}}");
            LoadTestMetrics m = GatlingResultsParser.parse(dir.toString(), "S", 1);
            assertEquals(m.step("pay").p99LatencyMs(), 91.9, 1e-9);

            List<SlaResult> results = SlaEvaluator.evaluate(
                    LoadSla.sla().step("pay").p99Below(92).step("pay").p99Below(91.9), m);
            assertTrue(results.get(0).passed(), results.toString());
            assertEquals(results.get(0).actual(), 91.9, 1e-9);
            assertFalse(results.get(1).passed(), "strictly below: " + results);
            assertEquals(io.testfly.metrics.ExecutionMetrics.loadTestMetricsToMap(m).get("steps") instanceof Map<?, ?> s
                    ? ((Map<?, ?>) s.get("pay")).get("p99LatencyMs") : null, 91.9);
        } finally {
            for (File f : dir.toFile().listFiles()) {
                f.delete();
            }
            dir.toFile().delete();
        }
    }

    /** A {@code GatlingRunConfig.GatlingAssertion} (package-private outer class, hence reflection). */
    private static Object assertion(String requestName, String metric, String comparison, double threshold)
            throws Exception {
        Class<?> type = Class.forName("io.testfly.loadtest.internal.GatlingRunConfig$GatlingAssertion");
        Constructor<?> ctor = type.getDeclaredConstructor();
        ctor.setAccessible(true);
        Object a = ctor.newInstance();
        for (Object[] f : new Object[][] {{"requestName", requestName}, {"metric", metric},
                {"comparison", comparison}, {"threshold", threshold}}) {
            java.lang.reflect.Field field = type.getField((String) f[0]);
            field.setAccessible(true);
            field.set(a, f[1]);
        }
        return a;
    }

    private static String scala(Assertion a) {
        try {
            return String.valueOf(a.getClass().getMethod("asScala").invoke(a));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("no asScala() on " + a.getClass(), e);
        }
    }
}
