package io.testfly.unit.loadtest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.testfly.loadtest.LoadTestMetrics;
import io.testfly.metrics.ExecutionMetrics;
import io.testfly.metrics.TestTiming;
import io.testfly.reporting.ReportPaths;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.Map;

import static org.testng.Assert.*;

/**
 * Tests the integration of load-test metrics with {@link ExecutionMetrics},
 * {@link TestTiming}, and JSON export.
 */
@Test(singleThreaded = true)
public class ExecutionMetricsLoadTestIntegrationTest {

    @BeforeMethod
    @AfterMethod
    public void cleanup() {
        ExecutionMetrics.reset();
    }

    private LoadTestMetrics sampleMetrics(String scenarioName) {
        return new LoadTestMetrics(
                scenarioName, 500, 490, 10, 100.0,
                45.0, 40.0, 70.0, 90.0, 120.0, 10.0, 150.0,
                0.02, Map.of(200, 490L, 500, 10L),
                5000, 20, "gatling",
                Map.of("Home", new LoadTestMetrics.StepMetrics("Home", 500, 490, 10, 90.0, 0.02)));
    }

    @Test
    public void testRecordLoadTestGlobal() {
        LoadTestMetrics metrics = sampleMetrics("GlobalScenario");
        ExecutionMetrics.recordLoadTest(metrics);

        assertEquals(ExecutionMetrics.getLoadTests().size(), 1);
        assertEquals(ExecutionMetrics.getLoadTests().get(0).scenarioName(), "GlobalScenario");

        // Duplicate record should not add a second entry
        ExecutionMetrics.recordLoadTest(metrics);
        assertEquals(ExecutionMetrics.getLoadTests().size(), 1);
    }

    @Test
    public void testRecordLoadTestWithTestId() {
        String testId = "io.testfly.examples.loadtest.SampleTest#testMethod";
        LoadTestMetrics metrics = sampleMetrics("SpecificScenario");

        ExecutionMetrics.recordLoadTest(testId, metrics);

        // Global list updated
        assertEquals(ExecutionMetrics.getLoadTests().size(), 1);

        // TestTiming updated
        TestTiming timing = ExecutionMetrics.getTiming(testId);
        assertNotNull(timing);
        assertNotNull(timing.getLoadTestMetrics());
        assertEquals(timing.getLoadTestMetrics().scenarioName(), "SpecificScenario");
        assertEquals(timing.getLoadTestMetrics().users(), 20);
        assertEquals(timing.getLoadTestMetrics().engine(), "gatling");
    }

    @Test
    public void testClearLoadTests() {
        ExecutionMetrics.recordLoadTest(sampleMetrics("Scenario1"));
        ExecutionMetrics.recordLoadTest(sampleMetrics("Scenario2"));
        assertEquals(ExecutionMetrics.getLoadTests().size(), 2);

        ExecutionMetrics.clearLoadTests();
        assertTrue(ExecutionMetrics.getLoadTests().isEmpty());
    }

    @Test
    public void testResetClearsLoadTests() {
        ExecutionMetrics.recordLoadTest(sampleMetrics("Scenario1"));
        assertEquals(ExecutionMetrics.getLoadTests().size(), 1);

        ExecutionMetrics.reset();
        assertTrue(ExecutionMetrics.getLoadTests().isEmpty());
    }

    @Test
    public void testLoadTestMetricsToMap() {
        LoadTestMetrics metrics = sampleMetrics("MapScenario");
        Map<String, Object> map = ExecutionMetrics.loadTestMetricsToMap(metrics);

        assertEquals(map.get("scenarioName"), "MapScenario");
        assertEquals(map.get("engine"), "gatling");
        assertEquals(map.get("users"), 20);
        assertEquals(map.get("totalRequests"), 500);
        assertEquals(map.get("successfulRequests"), 490);
        assertEquals(map.get("failedRequests"), 10);
        assertEquals(map.get("throughputRps"), 100.0);
        assertEquals(map.get("meanLatencyMs"), 45.0);
        assertEquals(map.get("p50LatencyMs"), 40.0);
        assertEquals(map.get("p90LatencyMs"), 70.0);
        assertEquals(map.get("p95LatencyMs"), 90.0);
        assertEquals(map.get("p99LatencyMs"), 120.0);
        assertEquals(map.get("errorRate"), 0.02);

        @SuppressWarnings("unchecked")
        Map<String, Object> steps = (Map<String, Object>) map.get("steps");
        assertNotNull(steps);
        assertTrue(steps.containsKey("Home"));

        @SuppressWarnings("unchecked")
        Map<String, Object> homeStep = (Map<String, Object>) steps.get("Home");
        assertEquals(homeStep.get("name"), "Home");
        assertEquals(homeStep.get("p95LatencyMs"), 90.0);
    }

    @Test
    public void testExportToJsonIncludesLoadTests() throws Exception {
        String testId = "io.testfly.examples.loadtest.SampleExportTest#run";
        LoadTestMetrics metrics = sampleMetrics("ExportScenario");

        ExecutionMetrics.markStart(testId);
        ExecutionMetrics.recordLoadTest(testId, metrics);
        ExecutionMetrics.recordStatus(testId, "PASSED");
        ExecutionMetrics.markEnd(testId);

        ExecutionMetrics.exportToJson();

        File metricsFile = ReportPaths.metricsJson();
        assertTrue(metricsFile.exists(), "testfly-metrics.json must exist");

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(metricsFile);

        // Verify top-level loadTests[] array exists and is populated
        assertTrue(root.has("loadTests"), "Root JSON must contain 'loadTests'");
        JsonNode loadTestsNode = root.get("loadTests");
        assertTrue(loadTestsNode.isArray());
        assertTrue(loadTestsNode.size() >= 1);

        boolean foundScenario = false;
        for (JsonNode lt : loadTestsNode) {
            if ("ExportScenario".equals(lt.path("scenarioName").asText())) {
                foundScenario = true;
                assertEquals(lt.path("engine").asText(), "gatling");
                assertEquals(lt.path("users").asInt(), 20);
                assertEquals(lt.path("totalRequests").asInt(), 500);
                assertEquals(lt.path("successfulRequests").asInt(), 490);
                assertEquals(lt.path("failedRequests").asInt(), 10);
                assertEquals(lt.path("p95LatencyMs").asDouble(), 90.0);
                assertTrue(lt.has("steps"));
                assertTrue(lt.get("steps").has("Home"));
            }
        }
        assertTrue(foundScenario, "ExportScenario must be found in loadTests array");

        // Verify per-test loadTestMetrics in tests[] array
        assertTrue(root.has("tests"));
        boolean foundInTestTiming = false;
        for (JsonNode testNode : root.get("tests")) {
            if (testId.equals(testNode.path("testId").asText())) {
                assertTrue(testNode.has("loadTestMetrics"), "Test entry must have loadTestMetrics");
                assertEquals(testNode.path("loadTestMetrics").path("scenarioName").asText(), "ExportScenario");
                foundInTestTiming = true;
            }
        }
        assertTrue(foundInTestTiming, "Test timing entry must contain loadTestMetrics");
    }
}
