package io.testfly.unit.metrics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.testfly.config.TestFlyConfig;
import io.testfly.internal.TestFlyContext;
import io.testfly.metrics.ExecutionMetrics;
import io.testfly.reporting.ReportPaths;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.*;

/**
 * Verifies cumulative test merging across separate test executions when mergeRuns is enabled.
 */
@Test(singleThreaded = true)
public class ExecutionMetricsMergeTest {

    private static final Object GLOBAL_REPORT_LOCK = ReportPaths.class;
    private static final Object CONTEXT_LOCK = TestFlyContext.class;

    @BeforeMethod
    public void setup() throws Exception {
        synchronized (GLOBAL_REPORT_LOCK) {
            synchronized (CONTEXT_LOCK) {
                System.clearProperty("testfly.merge");
                System.setProperty("testfly.reports.dir", "target/merge-test");
                ExecutionMetrics.reset();
                resetTestFlyContext();
                File json = ReportPaths.metricsJson();
                if (json.exists()) json.delete();
                File history = ReportPaths.metricsHistoryDir();
                if (history.exists()) {
                    File[] files = history.listFiles();
                    if (files != null) for (File f : files) f.delete();
                }
            }
        }
    }

    @AfterMethod
    public void cleanup() throws Exception {
        synchronized (GLOBAL_REPORT_LOCK) {
            synchronized (CONTEXT_LOCK) {
                System.clearProperty("testfly.merge");
                File json = ReportPaths.metricsJson();
                if (json.exists()) json.delete();
                System.clearProperty("testfly.reports.dir");
                ExecutionMetrics.reset();
                resetTestFlyContext();
            }
        }
    }

    private static void resetTestFlyContext() throws Exception {
        Field configField = TestFlyContext.class.getDeclaredField("CONFIG");
        configField.setAccessible(true);
        AtomicReference<?> ref = (AtomicReference<?>) configField.get(null);
        ref.set(null);
        TestFlyContext.clearCurrentTestId();
    }

    private static TestFlyConfig createConfig(boolean mergeRuns) {
        TestFlyConfig config = new TestFlyConfig();
        TestFlyConfig.Reporting reporting = new TestFlyConfig.Reporting();
        reporting.setMergeRuns(mergeRuns);
        config.setReporting(reporting);
        return config;
    }

    @Test
    public void exportToJson_mergesTestsWhenMergeRunsEnabled() throws Exception {
        synchronized (GLOBAL_REPORT_LOCK) {
            synchronized (CONTEXT_LOCK) {
                TestFlyContext.initialize(createConfig(true));

                // --- Run 1: Test A ---
                ExecutionMetrics.markStart("com.example.TestClass.testA");
                ExecutionMetrics.recordTestClass("com.example.TestClass.testA", "com.example.TestClass");
                ExecutionMetrics.recordStatus("com.example.TestClass.testA", "PASSED");
                ExecutionMetrics.markEnd("com.example.TestClass.testA");
                ExecutionMetrics.exportToJson();

                ObjectMapper mapper = new ObjectMapper();
                JsonNode root1 = mapper.readTree(ReportPaths.metricsJson());
                assertEquals(root1.get("totalTests").asInt(), 1);
                assertEquals(root1.get("passedTests").asInt(), 1);
                assertEquals(root1.get("tests").size(), 1);
                assertEquals(root1.get("tests").get(0).get("testId").asText(), "com.example.TestClass.testA");

                // --- Run 2: Reset in-memory state (simulating new JVM / new test command) ---
                ExecutionMetrics.reset();
                // Test B runs
                ExecutionMetrics.markStart("com.example.TestClass.testB");
                ExecutionMetrics.recordTestClass("com.example.TestClass.testB", "com.example.TestClass");
                ExecutionMetrics.recordStatus("com.example.TestClass.testB", "PASSED");
                ExecutionMetrics.markEnd("com.example.TestClass.testB");
                ExecutionMetrics.exportToJson();

                JsonNode root2 = mapper.readTree(ReportPaths.metricsJson());
                assertEquals(root2.get("totalTests").asInt(), 2, "Both testA and testB should be present after merge");
                assertEquals(root2.get("passedTests").asInt(), 2);
                assertEquals(root2.get("tests").size(), 2);

                // --- Run 3: Test A is re-run and fails ---
                ExecutionMetrics.reset();
                ExecutionMetrics.markStart("com.example.TestClass.testA");
                ExecutionMetrics.recordTestClass("com.example.TestClass.testA", "com.example.TestClass");
                ExecutionMetrics.recordStatus("com.example.TestClass.testA", "FAILED");
                ExecutionMetrics.markEnd("com.example.TestClass.testA");
                ExecutionMetrics.exportToJson();

                JsonNode root3 = mapper.readTree(ReportPaths.metricsJson());
                assertEquals(root3.get("totalTests").asInt(), 2, "Test count remains 2 (testA updated)");
                assertEquals(root3.get("passedTests").asInt(), 1);
                assertEquals(root3.get("failedTests").asInt(), 1);
            }
        }
    }

    @Test
    public void exportToJson_honorsSystemPropertyMergeOverride() throws Exception {
        synchronized (GLOBAL_REPORT_LOCK) {
            synchronized (CONTEXT_LOCK) {
                // Config has merge disabled
                TestFlyContext.initialize(createConfig(false));
                System.setProperty("testfly.merge", "true");

                // Run 1: Test 1
                ExecutionMetrics.markStart("test1");
                ExecutionMetrics.recordStatus("test1", "PASSED");
                ExecutionMetrics.markEnd("test1");
                ExecutionMetrics.exportToJson();

                // Run 2: Test 2
                ExecutionMetrics.reset();
                ExecutionMetrics.markStart("test2");
                ExecutionMetrics.recordStatus("test2", "PASSED");
                ExecutionMetrics.markEnd("test2");
                ExecutionMetrics.exportToJson();

                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(ReportPaths.metricsJson());
                assertEquals(root.get("totalTests").asInt(), 2, "System property -Dtestfly.merge=true should enable merging");
            }
        }
    }

    @Test
    public void exportToJson_doesNotMergeWhenDisabled() throws Exception {
        synchronized (GLOBAL_REPORT_LOCK) {
            synchronized (CONTEXT_LOCK) {
                TestFlyContext.initialize(createConfig(false));

                // Run 1: Test 1
                ExecutionMetrics.markStart("test1");
                ExecutionMetrics.recordStatus("test1", "PASSED");
                ExecutionMetrics.markEnd("test1");
                ExecutionMetrics.exportToJson();

                // Run 2: Test 2
                ExecutionMetrics.reset();
                ExecutionMetrics.markStart("test2");
                ExecutionMetrics.recordStatus("test2", "PASSED");
                ExecutionMetrics.markEnd("test2");
                ExecutionMetrics.exportToJson();

                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(ReportPaths.metricsJson());
                assertEquals(root.get("totalTests").asInt(), 1, "Without mergeRuns, previous tests are overwritten");
                assertEquals(root.get("tests").get(0).get("testId").asText(), "test2");
            }
        }
    }
}
