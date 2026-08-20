package io.testfly.unit.reportportal;

import io.testfly.config.TestFlyConfig;
import io.testfly.internal.TestFlyContext;
import io.testfly.reporting.reportportal.ReportPortalReportAdapter;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.*;

/**
 * Unit tests for {@link ReportPortalReportAdapter}.
 */
public class ReportPortalReportAdapterTest {

    private File metricsFile;

    @BeforeMethod
    public void setup() throws IOException {
        metricsFile = File.createTempFile("metrics", ".json");
        clearRpSystemProperties();
    }

    @AfterMethod
    public void cleanup() throws Exception {
        resetContext();
        if (metricsFile != null) {
            metricsFile.delete();
        }
        clearRpSystemProperties();
    }

    private static void resetContext() throws Exception {
        Field configField = TestFlyContext.class.getDeclaredField("CONFIG");
        configField.setAccessible(true);
        AtomicReference<?> ref = (AtomicReference<?>) configField.get(null);
        ref.set(null);
        TestFlyContext.clearCurrentTestId();
    }

    private static void clearRpSystemProperties() {
        System.clearProperty("rp.endpoint");
        System.clearProperty("rp.api.key");
        System.clearProperty("rp.project");
        System.clearProperty("rp.launch");
        System.clearProperty("rp.description");
        System.clearProperty("rp.attributes");
    }

    @Test
    public void generate_whenContextNotInitialized_doesNothing() {
        ReportPortalReportAdapter adapter = new ReportPortalReportAdapter();
        adapter.generate(metricsFile);
        // Should return early without throwing.
    }

    @Test
    public void generate_whenReportPortalDisabled_doesNothing() {
        TestFlyConfig config = minimalConfig();
        config.getReporting().getReportPortal().setEnabled(false);
        TestFlyContext.initialize(config);

        ReportPortalReportAdapter adapter = new ReportPortalReportAdapter();
        adapter.generate(metricsFile);
    }

    @Test
    public void generate_withInvalidConfig_doesNothing() {
        TestFlyConfig config = minimalConfig();
        config.getReporting().getReportPortal().setEnabled(true);
        config.getReporting().getReportPortal().setEndpoint(null);
        TestFlyContext.initialize(config);

        ReportPortalReportAdapter adapter = new ReportPortalReportAdapter();
        adapter.generate(metricsFile);
    }

    @Test
    public void generate_withValidConfigAndMetrics_logsSummary() throws IOException {
        TestFlyConfig config = minimalConfig();
        config.getReporting().getReportPortal().setEnabled(true);
        config.getReporting().getReportPortal().setEndpoint("http://localhost:8080");
        config.getReporting().getReportPortal().setApiKey("test-api-key");
        config.getReporting().getReportPortal().setProject("superadmin_personal");
        TestFlyContext.initialize(config);

        String json = "{"
                + "\"totalTests\": 3, \"passedTests\": 2, \"failedTests\": 1, \"skippedTests\": 0,"
                + "\"tests\": ["
                + "  {\"testId\": \"t1\", \"status\": \"PASSED\"},"
                + "  {\"testId\": \"t2\", \"status\": \"FAILED\"},"
                + "  {\"testId\": \"t3\", \"status\": \"SKIPPED\"}"
                + "]}"
                + "}";
        Files.writeString(metricsFile.toPath(), json);

        ReportPortalReportAdapter adapter = new ReportPortalReportAdapter();
        adapter.generate(metricsFile);
    }

    @Test
    public void getName_returnsReportPortal() {
        assertEquals(new ReportPortalReportAdapter().getName(), "reportportal");
    }

    private static TestFlyConfig minimalConfig() {
        TestFlyConfig config = new TestFlyConfig();

        TestFlyConfig.Browser browser = new TestFlyConfig.Browser();
        browser.setName("chrome");
        config.setBrowser(browser);

        TestFlyConfig.Execution execution = new TestFlyConfig.Execution();
        execution.setMode("local");
        execution.setBaseUrl("https://example.com");
        execution.setMaxActiveSessions(5);
        config.setExecution(execution);

        TestFlyConfig.Timeouts timeouts = new TestFlyConfig.Timeouts();
        timeouts.setExplicit(10);
        timeouts.setPageLoad(30);
        config.setTimeouts(timeouts);

        TestFlyConfig.Reporting reporting = new TestFlyConfig.Reporting();
        TestFlyConfig.Reporting.ReportPortal rp = new TestFlyConfig.Reporting.ReportPortal();
        rp.setEnabled(false);
        reporting.setReportPortal(rp);
        config.setReporting(reporting);

        return config;
    }
}
