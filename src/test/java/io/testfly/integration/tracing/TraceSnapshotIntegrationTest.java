package io.testfly.integration.tracing;

import com.sun.net.httpserver.HttpServer;
import io.testfly.config.TestFlyConfig;
import io.testfly.driver.DriverManager;
import io.testfly.internal.TestFlyContext;
import io.testfly.locator.Locator;
import io.testfly.metrics.ExecutionMetrics;
import io.testfly.steps.StepLogger;
import io.testfly.tracing.TraceRecorder;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * End to end: a real page is snapshotted at each step, the trace file is written, and the trace
 * is opened in the same browser to prove its panel works. The page contains markup that would
 * break a naively embedded trace (a closing script tag in an attribute and in the text).
 *
 * <p>Excluded from the default {@code mvn test} run; execute with:
 *
 * <pre>
 * mvn verify -Preal-backends -Dit.test=TraceSnapshotIntegrationTest
 * </pre>
 *
 * <p>Self-skips when no browser can be created.
 */
@Test(groups = {"integration"}, singleThreaded = true)
public class TraceSnapshotIntegrationTest {

    private static final String TEST_ID = "io.testfly.integration.tracing.TraceSnapshot#demo";
    private static final String PAGE = """
            <!DOCTYPE html><html><body>
            <h1>Welcome</h1>
            <button id="pay" title="</script><script>window.pwned=1</script>"
                    onclick="document.body.insertAdjacentHTML('beforeend','<div role=alert>Card declined</div>')">Pay</button>
            <p>Close tag: &lt;/script&gt;</p>
            </body></html>
            """;

    private HttpServer server;
    private WebDriver driver;
    private TestFlyConfig.Tracing originalTracing;

    @BeforeClass
    public void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] out = PAGE.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, out.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(out);
            }
        });
        server.start();
        try {
            io.testfly.lifecycle.FrameworkBootstrap.initialize();
            DriverManager.createDriver();
            driver = DriverManager.getDriver();
            TestFlyConfig config = TestFlyContext.getConfig();
            originalTracing = config.getTracing();
            TestFlyConfig.Tracing tracing = new TestFlyConfig.Tracing();
            tracing.setEnabled(true);
            tracing.setSnapshots("both");
            config.setTracing(tracing);
        } catch (Throwable t) {
            System.out.println("[TraceSnapshotIntegrationTest] Browser setup failed, tests will self-skip: "
                    + t.getMessage());
            driver = null;
        }
    }

    @AfterClass
    public void tearDown() {
        try {
            if (driver != null) {
                TestFlyContext.getConfig().setTracing(originalTracing);
            }
        } catch (Exception ignored) {
        }
        try {
            DriverManager.quitDriver();
        } catch (Exception ignored) {
        }
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    public void failedTest_traceShowsBeforeAndAfterSnapshots_andItsPanelWorks() throws Exception {
        if (driver == null) {
            throw new SkipException("No browser available — skipping trace snapshot integration test.");
        }
        TestFlyContext.setCurrentTestId(TEST_ID);
        ExecutionMetrics.markStart(TEST_ID);
        ExecutionMetrics.recordTestClass(TEST_ID, "TraceSnapshotIntegrationTest");

        driver.get("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        StepLogger.step("Open the page");
        Locator.cssSelector("#pay").click();          // logs "Click ..." before the click
        ExecutionMetrics.recordStatus(TEST_ID, "FAILED");

        // Steps carry the page as it was when they started.
        var steps = ExecutionMetrics.getTiming(TEST_ID).getSteps();
        assertTrue(steps.size() >= 2, "expected at least two steps, got " + steps.size());
        assertTrue(steps.get(0).getAriaSnapshot().contains("- heading \"Welcome\" [level=1]"),
                steps.get(0).getAriaSnapshot());
        assertFalse(steps.get(0).getAriaSnapshot().contains("Card declined"));
        assertNotNull(steps.get(0).getDomSnapshot());

        TraceRecorder.save(TEST_ID, "demo");

        // The last step now also has the page as the failing test left it.
        var last = steps.get(steps.size() - 1);
        assertTrue(last.getAfterAriaSnapshot().contains("- alert:\n  - text \"Card declined\""),
                last.getAfterAriaSnapshot());

        String relative = ExecutionMetrics.getTiming(TEST_ID).getTracePath();
        assertEquals(relative, "traces/TraceSnapshotIntegrationTest/demo-trace.html");
        File trace = new File("target/" + relative);
        assertTrue(trace.exists(), trace.getAbsolutePath());
        String html = Files.readString(trace.toPath());
        assertFalse(html.contains("<script>window.pwned"), "page markup must not reach the trace unescaped");

        // Open the trace in the browser and use its panel.
        driver.get(trace.toURI().toString());
        assertEquals(((JavascriptExecutor) driver).executeScript("return window.pwned === undefined"), true,
                "page markup must not execute inside the trace");
        driver.findElements(By.cssSelector(".step")).getFirst().click();
        String pre = (String) ((JavascriptExecutor) driver)
                .executeScript("return document.getElementById('snapshot-pre').textContent");
        assertTrue(pre.contains("- heading \"Welcome\" [level=1]"), pre);

        ((JavascriptExecutor) driver).executeScript("showScreenshot(" + (steps.size() - 1) + ")");
        ((JavascriptExecutor) driver).executeScript(
                "document.querySelector('.snap-tab[data-kind=diff]').click()");
        String diff = (String) ((JavascriptExecutor) driver)
                .executeScript("return document.getElementById('snapshot-pre').textContent");
        assertTrue(diff.contains("+ - alert:"), diff);
        assertTrue(diff.contains("+   - text \"Card declined\""), diff);
    }
}
