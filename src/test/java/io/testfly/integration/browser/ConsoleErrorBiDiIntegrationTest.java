package io.testfly.integration.browser;

import com.sun.net.httpserver.HttpServer;
import io.testfly.browser.ConsoleErrorCollector;
import io.testfly.driver.DriverManager;
import io.testfly.internal.TestFlyContext;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Console errors captured through WebDriver BiDi ({@code browser.bidi: true}). The shim is never
 * injected here: every error must come from the BiDi connection that was open before the first page
 * started loading. This is the capture Firefox needs, because it has no WebDriver browser log;
 * Chrome is used here because it also speaks BiDi and is available everywhere.
 *
 * <p>Excluded from the default {@code mvn test} run; execute with:
 *
 * <pre>
 * mvn verify -Preal-backends -Dit.test=ConsoleErrorBiDiIntegrationTest
 * </pre>
 *
 * <p>Self-skips when no browser can be created or the browser has no BiDi connection.
 */
@Test(groups = {"integration"}, singleThreaded = true)
public class ConsoleErrorBiDiIntegrationTest {

    private HttpServer server;
    private WebDriver driver;
    private boolean originalCapture;
    private boolean originalBidi;

    private void serve(String path, String html) {
        server.createContext(path, exchange -> {
            byte[] out = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, out.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(out);
            }
        });
    }

    @BeforeClass
    public void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/favicon.ico", exchange -> {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        serve("/clean", "<html><body>clean</body></html>");
        serve("/uncaught", "<html><body>x<script>throw new Error('boom-at-load');</script></body></html>");
        serve("/logged", "<html><body>x<script>console.error('logged-at-load');</script></body></html>");
        serve("/start", "<html><body><a id='go' href='/second'>go</a></body></html>");
        serve("/second", "<html><body>second<script>console.error('second-page-error');</script></body></html>");
        serve("/late", "<html><body>x<script>setTimeout(function(){ console.error('late-error'); }, 200);"
                + "</script></body></html>");
        server.start();
        try {
            io.testfly.lifecycle.FrameworkBootstrap.initialize();
            var browser = TestFlyContext.getConfig().getBrowser();
            originalCapture = browser.isCaptureConsoleErrors();
            originalBidi = browser.isBidi();
            browser.setCaptureConsoleErrors(true);
            browser.setBidi(true);
            DriverManager.createDriver();
            driver = DriverManager.getDriver();
        } catch (Throwable t) {
            System.out.println("[ConsoleErrorBiDiIntegrationTest] Browser setup failed, tests will self-skip: "
                    + t.getMessage());
            driver = null;
        }
    }

    @AfterClass
    public void tearDown() {
        try {
            var browser = TestFlyContext.getConfig().getBrowser();
            browser.setCaptureConsoleErrors(originalCapture);
            browser.setBidi(originalBidi);
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

    private void requireBiDi() {
        if (driver == null) {
            throw new SkipException("No browser available — skipping BiDi console error test.");
        }
        if (!ConsoleErrorCollector.isBiDiActive()) {
            throw new SkipException("This browser has no WebDriver BiDi connection — skipping.");
        }
    }

    private void open(String path) {
        requireBiDi();
        ConsoleErrorCollector.beginTest(driver); // what the framework does when a test gets its browser
        driver.get("http://127.0.0.1:" + server.getAddress().getPort() + path);
        // Note: no injectShim() here.
    }

    /** BiDi events arrive over a websocket, a moment after the page ran. */
    private List<String> errorsWith(String text) throws InterruptedException {
        List<String> errors = ConsoleErrorCollector.collect();
        for (int i = 0; i < 30 && errors.stream().noneMatch(e -> e.contains(text)); i++) {
            Thread.sleep(100);
            errors = ConsoleErrorCollector.collect();
        }
        return errors;
    }

    @Test
    public void bidiIsTheActiveCapture() {
        requireBiDi();
        assertTrue(ConsoleErrorCollector.isBiDiActive());
    }

    @Test
    public void cleanPage_reportsNothing() throws Exception {
        open("/clean");
        Thread.sleep(300);
        assertTrue(ConsoleErrorCollector.collect().isEmpty(), ConsoleErrorCollector.collect().toString());
    }

    @Test
    public void uncaughtErrorWhileLoading_isReported() throws Exception {
        open("/uncaught");
        List<String> errors = errorsWith("boom-at-load");
        assertTrue(errors.stream().anyMatch(e -> e.contains("boom-at-load")), "missed: " + errors);
    }

    @Test
    public void consoleErrorWhileLoading_isReported() throws Exception {
        open("/logged");
        List<String> errors = errorsWith("logged-at-load");
        assertTrue(errors.stream().anyMatch(e -> e.contains("logged-at-load")), "missed: " + errors);
    }

    @Test
    public void errorOnPageReachedByClickingALink_isReported() throws Exception {
        open("/start");
        driver.findElement(By.id("go")).click();
        List<String> errors = errorsWith("second-page-error");
        assertTrue(errors.stream().anyMatch(e -> e.contains("second-page-error")), "missed: " + errors);
    }

    @Test
    public void errorRaisedAfterLoad_isReported() throws Exception {
        open("/late");
        List<String> errors = errorsWith("late-error");
        assertTrue(errors.stream().anyMatch(e -> e.contains("late-error")), "missed: " + errors);
    }

    @Test
    public void beginTest_forgetsErrorsFromTheTestBefore() throws Exception {
        open("/logged");
        assertFalse(errorsWith("logged-at-load").isEmpty());

        ConsoleErrorCollector.beginTest(driver);

        assertEquals(ConsoleErrorCollector.collect().size(), 0, "a new test starts with no errors");
    }

    @Test
    public void clear_dropsTheErrorsSoFar() throws Exception {
        open("/logged");
        assertFalse(errorsWith("logged-at-load").isEmpty());

        ConsoleErrorCollector.clear();

        assertTrue(ConsoleErrorCollector.collect().isEmpty());
    }
}
