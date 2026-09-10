package io.testfly.unit.loadtest;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.testfly.config.TestFlyConfig;
import io.testfly.internal.TestFlyContext;
import io.testfly.loadtest.LoadTestReportAdapter;
import io.testfly.reporting.ReportPaths;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.*;

/**
 * Unit tests for {@link LoadTestReportAdapter}.
 */
@Test(singleThreaded = true)
public class LoadTestReportAdapterTest {

    private static final Object GLOBAL_REPORT_LOCK = ReportPaths.class;
    private static final Object CONTEXT_LOCK = TestFlyContext.class;

    private Path tempDir;
    private LoadTestReportAdapter adapter;

    @BeforeMethod
    public void setUp() throws IOException {
        synchronized (GLOBAL_REPORT_LOCK) {
            synchronized (CONTEXT_LOCK) {
                tempDir = Files.createTempDirectory("loadtest-report-test-");
                adapter = new LoadTestReportAdapter();
                if (!TestFlyContext.isInitialized()) {
                    TestFlyConfig config = new TestFlyConfig();
                    TestFlyConfig.Browser b = new TestFlyConfig.Browser();
                    b.setName("chrome");
                    config.setBrowser(b);
                    TestFlyConfig.Execution e = new TestFlyConfig.Execution();
                    e.setMode("local");
                    config.setExecution(e);
                    TestFlyConfig.Timeouts t = new TestFlyConfig.Timeouts();
                    config.setTimeouts(t);
                    TestFlyContext.initialize(config);
                }
            }
        }
    }

    @AfterMethod
    public void tearDown() throws IOException {
        synchronized (GLOBAL_REPORT_LOCK) {
            synchronized (CONTEXT_LOCK) {
                if (tempDir != null && Files.exists(tempDir)) {
                    Files.walk(tempDir)
                            .sorted(Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(File::delete);
                }
            }
        }
    }

    @Test
    public void getName_returnsLoadTest() {
        assertEquals(adapter.getName(), "loadtest");
    }

    @Test
    public void generate_withNullFile_doesNotThrow() {
        assertDoesNotThrow(() -> adapter.generate(null));
    }

    @Test
    public void generate_withNonExistentFile_doesNotThrow() {
        File nonExistent = new File(tempDir.toFile(), "does-not-exist.json");
        assertDoesNotThrow(() -> adapter.generate(nonExistent));
    }

    @Test
    public void generate_withEmptyFile_doesNotThrow() throws IOException {
        File emptyFile = new File(tempDir.toFile(), "empty.json");
        assertTrue(emptyFile.createNewFile());
        assertDoesNotThrow(() -> adapter.generate(emptyFile));
    }

    @Test
    public void generate_withoutLoadTestsKey_doesNotThrow() throws IOException {
        Path testDir = Files.createTempDirectory("lt-report-test-");
        try {
            File metricsFile = new File(testDir.toFile(), "no-loadtests.json");
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("totalTests", 5);
            new ObjectMapper().writeValue(metricsFile, data);

            assertDoesNotThrow(() -> adapter.generate(metricsFile));
        } finally {
            deleteRecursively(testDir);
        }
    }

    @Test
    public void generate_withLoadTests_executesGracefully() throws IOException {
        synchronized (GLOBAL_REPORT_LOCK) {
            synchronized (CONTEXT_LOCK) {
                Path testDir = Files.createTempDirectory("lt-report-test-");
                try {
                    File metricsFile = new File(testDir.toFile(), "metrics-with-lt.json");
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("totalTests", 1);
                    data.put("passedTests", 1);

                    Map<String, Object> lt = new LinkedHashMap<>();
                    lt.put("scenarioName", "CheckoutFlow");
                    lt.put("engine", "gatling");
                    lt.put("users", 10);
                    lt.put("totalRequests", 100);
                    lt.put("successfulRequests", 98);
                    lt.put("failedRequests", 2);
                    lt.put("throughputRps", 25.5);
                    lt.put("p95LatencyMs", 150.0);
                    lt.put("errorRate", 0.02);
                    data.put("loadTests", List.of(lt));

                    new ObjectMapper().writeValue(metricsFile, data);

                    // Verify that generation does not throw any exceptions
                    assertDoesNotThrow(() -> adapter.generate(metricsFile));
                } finally {
                    deleteRecursively(testDir);
                }
            }
        }
    }

    @Test
    public void findGatlingReports_returnsFoundIndexHtmlFiles() throws IOException {
        Path testDir = Files.createTempDirectory("lt-gatling-test-");
        try {
            File baseDir = new File(testDir.toFile(), "loadtest");
            File simDir = new File(baseDir, "simulation-123/subfolder");
            assertTrue(simDir.mkdirs());

            File indexHtml = new File(simDir, "index.html");
            try (FileWriter writer = new FileWriter(indexHtml)) {
                writer.write("<html><body>Gatling Simulation Report</body></html>");
            }

            List<File> reports = LoadTestReportAdapter.findGatlingReports(baseDir);
            assertEquals(reports.size(), 1);
            assertEquals(reports.get(0).getName(), "index.html");
        } finally {
            deleteRecursively(testDir);
        }
    }

    @Test
    public void findGatlingReports_withNullOrEmptyDir_returnsEmptyList() {
        assertTrue(LoadTestReportAdapter.findGatlingReports(null).isEmpty());
        assertTrue(LoadTestReportAdapter.findGatlingReports(new File(tempDir.toFile(), "missing")).isEmpty());
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) return;
        Files.walk(path)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
    }

    private static void assertDoesNotThrow(Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            fail("Expected no exception, but got: " + e.getMessage(), e);
        }
    }
}
