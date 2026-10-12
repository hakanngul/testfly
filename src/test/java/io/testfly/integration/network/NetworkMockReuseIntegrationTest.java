package io.testfly.integration.network;

import com.sun.net.httpserver.HttpServer;
import io.testfly.driver.DriverManager;
import io.testfly.network.NetworkMock;
import io.testfly.network.Response;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Network mocking across several "tests" on one reused driver, as with
 * {@code browser.lifecycle: per-suite}: each round must see only its own mock, and a round without
 * a mock must reach the real server.
 *
 * <pre>
 * mvn verify -Preal-backends -Dit.test=NetworkMockReuseIntegrationTest
 * </pre>
 */
@Test(groups = {"integration"}, singleThreaded = true)
public class NetworkMockReuseIntegrationTest {

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
            byte[] out = "<!DOCTYPE html><html><body>ok</body></html>".getBytes(StandardCharsets.UTF_8);
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
            System.out.println("[NetworkMockReuseIntegrationTest] Browser setup failed, tests will self-skip: "
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

    private String fetchInBrowser(String url) {
        driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(10));
        Object result = ((JavascriptExecutor) driver).executeAsyncScript(
                "var cb = arguments[arguments.length - 1];"
                        + "fetch(arguments[0]).then(r => r.text()).then(t => cb(t)).catch(e => cb('ERR:' + e));",
                url);
        return String.valueOf(result);
    }

    @Test
    public void eachRoundSeesOnlyItsOwnMock_andTheLastRoundReachesTheRealServer() {
        if (driver == null || !(driver instanceof org.openqa.selenium.chromium.ChromiumDriver)) {
            throw new SkipException("No Chromium browser available — skipping network mock reuse test.");
        }
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/api/ping";
        driver.get("http://127.0.0.1:" + server.getAddress().getPort() + "/");

        for (int round = 1; round <= 5; round++) {
            NetworkMock.get().mockRoute("**/api/ping", Response.text(200, "mock-" + round));
            assertTrue(NetworkMock.get().isInterceptionActive(), "round " + round);
            assertEquals(fetchInBrowser(url), "mock-" + round, "round " + round);
            assertEquals(NetworkMock.get().assertThat().getClass().getSimpleName(), "NetworkAssert");
            NetworkMock.cleanup();
        }

        assertEquals(fetchInBrowser(url), "real", "without a mock the request must reach the server");
    }
}
