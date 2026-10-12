package io.testfly.integration.browser;

import com.sun.net.httpserver.HttpServer;
import io.testfly.browser.DeviceEmulator;
import io.testfly.driver.DriverManager;
import io.testfly.network.NetworkMock;
import io.testfly.network.Response;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Device emulation on a real Chromium: the emulated viewport and user agent must stick, must
 * survive navigation, and must not disturb other DevTools features (network mocking) running on
 * the same driver.
 *
 * <p>Excluded from the default {@code mvn test} run; execute with:
 *
 * <pre>
 * mvn verify -Preal-backends -Dit.test=DeviceEmulatorIntegrationTest
 * </pre>
 *
 * <p>Self-skips when no Chromium browser can be created.
 */
@Test(groups = {"integration"}, singleThreaded = true)
public class DeviceEmulatorIntegrationTest {

    private HttpServer server;
    private WebDriver driver;

    @BeforeClass
    public void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/ping", exchange -> {
            byte[] out = "real".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, out.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(out);
            }
        });
        server.createContext("/", exchange -> {
            byte[] out = ("<!DOCTYPE html><html><head><meta name=\"viewport\" content=\"width=device-width\"></head>"
                    + "<body>ok</body></html>").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html");
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
        } catch (Throwable t) {
            System.out.println("[DeviceEmulatorIntegrationTest] Browser setup failed, tests will self-skip: "
                    + t.getMessage());
            driver = null;
        }
    }

    @AfterClass
    public void tearDown() {
        try {
            DriverManager.quitDriver();
        } catch (Exception ignored) {
        }
        if (server != null) {
            server.stop(0);
        }
    }

    @AfterMethod
    public void reset() {
        try {
            NetworkMock.cleanup();
        } catch (Exception ignored) {
        }
        try {
            if (driver != null) {
                DeviceEmulator.reset();
            }
        } catch (Exception ignored) {
        }
    }

    private String base() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private void requireChromium() {
        if (driver == null || !(driver instanceof org.openqa.selenium.chromium.ChromiumDriver)) {
            throw new SkipException("No Chromium browser available — skipping device emulation test.");
        }
    }

    private Object js(String script) {
        return ((JavascriptExecutor) driver).executeScript(script);
    }

    private String fetchInBrowser(String url) {
        driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(10));
        Object result = ((JavascriptExecutor) driver).executeAsyncScript(
                "var cb = arguments[arguments.length - 1];"
                        + "fetch(arguments[0]).then(r => r.text()).then(t => cb(t)).catch(e => cb('ERR:' + e));",
                url);
        return String.valueOf(result);
    }

    @Test
    public void emulate_appliesViewportAndUserAgent_andSurvivesNavigation() {
        requireChromium();
        driver.get(base() + "/");

        DeviceEmulator.emulate("iPhone 14");
        driver.get(base() + "/");

        assertEquals(((Number) js("return window.innerWidth")).intValue(), 390, "emulated viewport width");
        assertTrue(String.valueOf(js("return navigator.userAgent")).contains("iPhone"),
                "emulated user agent, got: " + js("return navigator.userAgent"));
    }

    @Test
    public void emulate_doesNotBreakNetworkMockOnTheSameDriver() {
        requireChromium();
        driver.get(base() + "/");
        NetworkMock.get().mockRoute("**/api/ping", Response.text(200, "mocked"));
        assertEquals(fetchInBrowser(base() + "/api/ping"), "mocked", "mock works before emulation");

        DeviceEmulator.emulate("iPhone 14");

        assertEquals(fetchInBrowser(base() + "/api/ping"), "mocked", "mock must still work after emulation");
    }

    @Test
    public void reset_restoresTheDesktopUserAgent() {
        requireChromium();
        DeviceEmulator.emulate("iPhone 14");
        DeviceEmulator.reset();
        driver.get(base() + "/");

        assertTrue(!String.valueOf(js("return navigator.userAgent")).contains("iPhone"),
                "user agent must be back to the browser default, got: " + js("return navigator.userAgent"));
    }
}
