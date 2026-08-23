package io.testfly.unit;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.testfly.config.TestFlyConfig;
import io.testfly.internal.TestFlyContext;
import io.testfly.reporting.HtmlReportGenerator;
import io.testfly.reporting.ReportPaths;

/**
 * Unit tests for {@link HtmlReportGenerator}.
 * Verifies that the HTML report is generated and renders CI metadata.
 */
public class HtmlReportGeneratorTest {

    @AfterMethod
    public void cleanup() throws Exception {
        File html = ReportPaths.htmlReport();
        File json = ReportPaths.metricsJson();
        if (html.exists()) html.delete();
        if (json.exists()) json.delete();
        resetTestFlyContext();
    }

    private static void resetTestFlyContext() throws Exception {
        Field configField = TestFlyContext.class.getDeclaredField("CONFIG");
        configField.setAccessible(true);
        AtomicReference<?> ref = (AtomicReference<?>) configField.get(null);
        ref.set(null);
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

        TestFlyConfig.Ci ci = new TestFlyConfig.Ci();
        ci.setCaptureMetadata(true);
        config.setCi(ci);

        return config;
    }

    private static void writeMetricsWithCi() throws IOException {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("totalTests", 1);
        root.put("passedTests", 1);
        root.put("failedTests", 0);
        root.put("skippedTests", 0);
        root.put("passRate", 100.0);
        root.put("flakyTests", 0);
        root.put("recoveredTests", 0);
        root.put("totalTimeMs", 250L);
        root.put("averageTimeMs", 250L);

        Map<String, Object> ci = new LinkedHashMap<>();
        ci.put("provider", "GitHub Actions");
        ci.put("buildNumber", "42");
        ci.put("buildId", "123");
        ci.put("branch", "main");
        ci.put("commitSha", "abc123");
        ci.put("repository", "testfly/testfly");
        ci.put("actor", "hagul");
        ci.put("jobName", "unit-tests");
        ci.put("buildUrl", "https://github.com/hakanngul/testfly/actions/runs/123");
        root.put("ci", ci);

        ObjectMapper mapper = new ObjectMapper();
        File json = ReportPaths.metricsJson();
        json.getParentFile().mkdirs();
        mapper.writeValue(json, root);
    }

    @Test
    public void generate_createsHtmlReport() throws IOException {
        TestFlyContext.initialize(minimalConfig());
        writeMetricsWithCi();

        HtmlReportGenerator.generate();

        File html = ReportPaths.htmlReport();
        assertTrue(html.exists(), "HTML report file should be created");
    }

    @Test
    public void generate_includesBuildMetadata() throws IOException {
        TestFlyContext.initialize(minimalConfig());
        writeMetricsWithCi();

        HtmlReportGenerator.generate();

        String html = Files.readString(ReportPaths.htmlReport().toPath());
        assertTrue(html.contains("Build Metadata"), "Report must contain build metadata section");
        assertTrue(html.contains("GitHub Actions"), "CI provider must be rendered");
        assertTrue(html.contains("42"), "Build number must be rendered");
        assertTrue(html.contains("main"), "Branch must be rendered");
        assertTrue(html.contains("testfly/testfly"), "Repository must be rendered");
    }

    @Test
    public void generate_buildUrlIsLinked() throws IOException {
        TestFlyContext.initialize(minimalConfig());
        writeMetricsWithCi();

        HtmlReportGenerator.generate();

        String html = Files.readString(ReportPaths.htmlReport().toPath());
        assertTrue(html.contains("https://github.com/hakanngul/testfly/actions/runs/123"),
                "Build URL must be rendered as a link");
    }

    @Test
    public void generate_noMetricsJson_isNoOp() {
        TestFlyContext.initialize(minimalConfig());
        File json = ReportPaths.metricsJson();
        if (json.exists()) json.delete();

        // Should not throw
        HtmlReportGenerator.generate();

        assertFalse(ReportPaths.htmlReport().exists(),
                "HTML report should not be created when metrics JSON is missing");
    }
}
