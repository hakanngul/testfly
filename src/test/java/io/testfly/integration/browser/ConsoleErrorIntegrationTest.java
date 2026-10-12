package io.testfly.integration.browser;

import com.sun.net.httpserver.HttpServer;
import io.testfly.browser.ConsoleErrorCollector;
import io.testfly.driver.DriverManager;
import org.openqa.selenium.By;
import org.openqa.selenium.HasCapabilities;
import org.openqa.selenium.WebDriver;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import static org.testng.Assert.assertTrue;

/**
 * Console error capture on a real browser. The errors that matter most are the ones raised while a
 * page loads and on pages reached by clicking a link: a collector that only sees errors raised
 * after the test called {@code open()} lets {@code failOnConsoleErrors} pass when it should fail.
 *
 * <p>Excluded from the default {@code mvn test} run; execute with:
 *
 * <pre>
 * mvn verify -Preal-backends -Dit.test=ConsoleErrorIntegrationTest
 * </pre>
 *
 * <p>Self-skips when no browser can be created. The load-time and link-navigation cases also skip
 * on non-Chromium browsers without {@code browser.bidi}, where TestFly documents that these errors
 * are missed (see {@code ConsoleErrorBiDiIntegrationTest} for the BiDi path).
 */
@Test(groups = {"integration"}, singleThreaded = true)
public class ConsoleErrorIntegrationTest {

    private HttpServer server;
    private WebDriver driver;
    private boolean originalCapture;

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
        // Chrome asks for /favicon.ico on every origin; a 404 would be reported as a console error.
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
            originalCapture = io.testfly.internal.TestFlyContext.getConfig().getBrowser().isCaptureConsoleErrors();
            io.testfly.internal.TestFlyContext.getConfig().getBrowser().setCaptureConsoleErrors(true);
            DriverManager.createDriver();
            driver = DriverManager.getDriver();
        } catch (Throwable t) {
            System.out.println("[ConsoleErrorIntegrationTest] Browser setup failed, tests will self-skip: "
                    + t.getMessage());
            driver = null;
        }
    }

    @AfterClass
    public void tearDown() {
        try {
            io.testfly.internal.TestFlyContext.getConfig().getBrowser().setCaptureConsoleErrors(originalCapture);
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

    private void open(String path) {
        if (driver == null) {
            throw new SkipException("No browser available — skipping console error test.");
        }
        driver.get("http://127.0.0.1:" + server.getAddress().getPort() + path);
        // Same as NavigationSupport.open(): the shim goes in after the page has loaded.
        if (ConsoleErrorCollector.isEnabled()) {
            ConsoleErrorCollector.injectShim();
        }
    }

    /** Skips unless errors raised before the shim is injected can be captured: Chromium logs or BiDi. */
    private void requireLoadTimeCapture() {
        if (driver == null || ConsoleErrorCollector.isBiDiActive()) {
            return;
        }
        String browser = driver instanceof HasCapabilities hc
                ? hc.getCapabilities().getBrowserName().toLowerCase(Locale.ROOT)
                : "";
        if (!browser.equals("chrome") && !browser.contains("edge")) {
            throw new SkipException("Load-time console errors are not captured on " + browser
                    + " without browser.bidi (documented limitation).");
        }
    }

    private static boolean mentions(List<String> errors, String text) {
        return errors.stream().anyMatch(e -> e.contains(text));
    }

    @Test
    public void cleanPage_reportsNothing() {
        open("/clean");
        List<String> errors = ConsoleErrorCollector.collect();
        assertTrue(errors.isEmpty(), "unexpected errors: " + errors);
    }

    @Test
    public void uncaughtErrorWhileLoading_isReported() {
        requireLoadTimeCapture();
        open("/uncaught");
        List<String> errors = ConsoleErrorCollector.collect();
        assertTrue(mentions(errors, "boom-at-load"), "uncaught load-time error missed: " + errors);
    }

    @Test
    public void consoleErrorWhileLoading_isReported() {
        requireLoadTimeCapture();
        open("/logged");
        List<String> errors = ConsoleErrorCollector.collect();
        assertTrue(mentions(errors, "logged-at-load"), "load-time console.error missed: " + errors);
    }

    @Test
    public void errorOnPageReachedByClickingALink_isReported() {
        requireLoadTimeCapture();
        open("/start");
        driver.findElement(By.id("go")).click();
        List<String> errors = ConsoleErrorCollector.collect();
        assertTrue(mentions(errors, "second-page-error"), "error after link navigation missed: " + errors);
    }

    @Test
    public void errorRaisedAfterLoad_isReported() throws Exception {
        open("/late");
        Thread.sleep(600);
        List<String> errors = ConsoleErrorCollector.collect();
        assertTrue(mentions(errors, "late-error"), "late error missed: " + errors);
    }
}
