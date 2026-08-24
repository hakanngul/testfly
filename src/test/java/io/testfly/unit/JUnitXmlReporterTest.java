package io.testfly.unit;

import io.testfly.ci.CiMetadata;
import io.testfly.config.TestFlyConfig;
import io.testfly.internal.TestFlyContext;
import io.testfly.metrics.ExecutionMetrics;
import io.testfly.metrics.TestTiming;
import io.testfly.reporting.JUnitXmlReporter;
import io.testfly.reporting.ReportPaths;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.*;

/**
 * Unit tests for {@link JUnitXmlReporter}.
 * Verifies that the generated XML file exists and contains well-formed content.
 * Thread-safe for parallel=methods via singleThreaded and global report lock.
 */
@Test(singleThreaded = true)
public class JUnitXmlReporterTest {

    private static final File XML_FILE =
            new File("target/surefire-reports/TEST-TestFly.xml");

    private static final Object GLOBAL_REPORT_LOCK = ReportPaths.class;

    @BeforeMethod
    public void setup() throws Exception {
        synchronized (GLOBAL_REPORT_LOCK) {
            System.clearProperty("testfly.reports.dir");
            if (XML_FILE.exists()) XML_FILE.delete();
            ExecutionMetrics.reset();
            resetTestFlyContext();
        }
    }

    @AfterMethod
    public void cleanup() throws Exception {
        synchronized (GLOBAL_REPORT_LOCK) {
            System.clearProperty("testfly.reports.dir");
            if (XML_FILE.exists()) XML_FILE.delete();
            ExecutionMetrics.reset();
            resetTestFlyContext();
        }
    }

    private static void resetTestFlyContext() throws Exception {
        Field configField = TestFlyContext.class.getDeclaredField("CONFIG");
        configField.setAccessible(true);
        AtomicReference<?> ref = (AtomicReference<?>) configField.get(null);
        ref.set(null);
        // also clear ThreadLocal
        TestFlyContext.clearCurrentTestId();
        // clear ExecutionMetrics ci metadata
        try {
            Field ciField = ExecutionMetrics.class.getDeclaredField("ciMetadata");
            ciField.setAccessible(true);
            ciField.set(null, null);
        } catch (Exception ignored) {}
    }

    private static TestFlyConfig minimalConfig(boolean captureMetadata) {
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
        ci.setCaptureMetadata(captureMetadata);
        config.setCi(ci);

        return config;
    }

    // ----------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------

    private TestTiming timing(String id, String status) {
        TestTiming t = new TestTiming(id, "main");
        t.setStatus(status);
        t.setTotalTime(250L);
        return t;
    }

    private String readXml() throws IOException {
        synchronized (GLOBAL_REPORT_LOCK) {
            return Files.readString(XML_FILE.toPath());
        }
    }

    private void exportSync(List<TestTiming> timings, long duration) {
        synchronized (GLOBAL_REPORT_LOCK) {
            System.clearProperty("testfly.reports.dir");
            JUnitXmlReporter.export(timings, duration);
        }
    }

    // ----------------------------------------------------------
    // File creation
    // ----------------------------------------------------------

    @Test
    public void export_createsXmlFile() {
        exportSync(List.of(timing("t1", "PASSED")), 250L);
        synchronized (GLOBAL_REPORT_LOCK) {
            assertTrue(XML_FILE.exists(), "XML report file should be created");
        }
    }

    @Test
    public void export_emptyTimings_createsFile() {
        exportSync(List.of(), 0L);
        synchronized (GLOBAL_REPORT_LOCK) {
            assertTrue(XML_FILE.exists());
        }
    }

    // ----------------------------------------------------------
    // XML structure
    // ----------------------------------------------------------

    @Test
    public void export_containsXmlDeclaration() throws IOException {
        exportSync(List.of(timing("t1", "PASSED")), 500L);
        assertTrue(readXml().startsWith("<?xml"), "File must start with XML declaration");
    }

    @Test
    public void export_containsTestsuiteElement() throws IOException {
        exportSync(List.of(timing("t1", "PASSED")), 500L);
        assertTrue(readXml().contains("<testsuite"), "Must contain <testsuite> element");
    }

    @Test
    public void export_testCounts_matchTimings() throws IOException {
        List<TestTiming> timings = List.of(
                timing("t1", "PASSED"),
                timing("t2", "FAILED"),
                timing("t3", "SKIPPED")
        );
        exportSync(timings, 1000L);
        String xml = readXml();
        assertTrue(xml.contains("tests=\"3\""), "tests attribute should be 3");
        assertTrue(xml.contains("failures=\"1\""), "failures attribute should be 1");
        assertTrue(xml.contains("skipped=\"1\""), "skipped attribute should be 1");
    }

    @Test
    public void export_passedTest_isSelfClosingTestcase() throws IOException {
        exportSync(List.of(timing("myPassedTest", "PASSED")), 100L);
        String xml = readXml();
        assertTrue(xml.contains("name=\"myPassedTest\""));
        assertTrue(xml.contains("/>"), "Passed tests should be self-closing <testcase/>");
    }

    @Test
    public void export_failedTest_containsFailureElement() throws IOException {
        exportSync(List.of(timing("myFailedTest", "FAILED")), 100L);
        String xml = readXml();
        assertTrue(xml.contains("name=\"myFailedTest\""));
        assertTrue(xml.contains("<failure"), "Failed tests must contain <failure> element");
    }

    @Test
    public void export_skippedTest_containsSkippedElement() throws IOException {
        exportSync(List.of(timing("mySkippedTest", "SKIPPED")), 100L);
        String xml = readXml();
        assertTrue(xml.contains("name=\"mySkippedTest\""));
        assertTrue(xml.contains("<skipped/>"), "Skipped tests must contain <skipped/>");
    }

    // ----------------------------------------------------------
    // XML escaping
    // ----------------------------------------------------------

    @Test
    public void export_testIdWithSpecialChars_escapedProperly() throws IOException {
        exportSync(
                List.of(timing("test<with>&special\"chars", "PASSED")), 100L);
        String xml = readXml();
        assertFalse(xml.contains("<with>"),
                "Unescaped < > must not appear in attribute values");
        assertTrue(xml.contains("<") || xml.contains("&"),
                "Special chars must be XML-escaped");
    }

    // ----------------------------------------------------------
    // Failure message and stack trace content
    // ----------------------------------------------------------

    @Test
    public void failureElement_containsActualErrorMessage() throws IOException {
        TestTiming t = timing("failTest", "FAILED");
        t.setErrorMessage("Expected [Login] but found [Error 404]");
        exportSync(List.of(t), 100L);

        String xml = readXml();
        assertTrue(xml.contains("Expected [Login] but found [Error 404]"),
                "failure message attribute must contain actual error text");
    }

    @Test
    public void failureElement_containsStackTrace() throws IOException {
        TestTiming t = timing("failTest2", "FAILED");
        t.setErrorMessage("assertion failed");
        t.setStackTrace("java.lang.AssertionError: assertion failed\n\tat com.example.MyTest.myTest(MyTest.java:42)");
        exportSync(List.of(t), 100L);

        String xml = readXml();
        assertTrue(xml.contains("MyTest.java:42"),
                "failure element body must contain the stack trace");
    }

    // ----------------------------------------------------------
    // Idempotency — second call overwrites first
    // ----------------------------------------------------------

    @Test
    public void export_calledTwice_fileOverwritten() throws IOException {
        exportSync(List.of(timing("t1", "PASSED")), 100L);
        exportSync(List.of(timing("t2", "FAILED"), timing("t3", "FAILED")), 200L);
        String xml = readXml();
        assertTrue(xml.contains("tests=\"2\""),
                "Second export should overwrite with new content");
    }

    // ----------------------------------------------------------
    // CI properties
    // ----------------------------------------------------------

    @Test
    public void export_withoutContext_doesNotIncludeProperties() throws IOException {
        exportSync(List.of(timing("t1", "PASSED")), 100L);
        String xml = readXml();
        assertFalse(xml.contains("<properties>"), "No context means no CI properties");
    }

    @Test
    public void export_withContextButCaptureDisabled_doesNotIncludeProperties() throws IOException {
        synchronized (GLOBAL_REPORT_LOCK) {
            System.clearProperty("testfly.reports.dir");
            TestFlyContext.initialize(minimalConfig(false));
            JUnitXmlReporter.export(List.of(timing("t1", "PASSED")), 100L);
        }
        String xml = readXml();
        assertFalse(xml.contains("<properties>"), "Disabled capture means no CI properties");
    }

    @Test
    public void export_withCaptureEnabledAndMetadata_includesCiProperties() throws IOException {
        synchronized (GLOBAL_REPORT_LOCK) {
            System.clearProperty("testfly.reports.dir");
            TestFlyContext.initialize(minimalConfig(true));
            ExecutionMetrics.setCiMetadata(new CiMetadata(
                    "GitHub Actions", "42", "123", "main",
                    "abc123", null, "https://example.com/run/123",
                    "unit-tests", null, "testfly/testfly",
                    "hagul", "agent-1", null));

            JUnitXmlReporter.export(List.of(timing("t1", "PASSED")), 100L);
        }
        String xml = readXml();
        assertTrue(xml.contains("<properties>"), "CI properties block must be present");
        assertTrue(xml.contains("name=\"provider\""), "Provider property must be present");
        assertTrue(xml.contains("value=\"GitHub Actions\""), "Provider value must be present");
        assertTrue(xml.contains("name=\"buildNumber\""), "Build number property must be present");
    }
}
