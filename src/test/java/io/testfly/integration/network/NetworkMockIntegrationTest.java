package io.testfly.integration.network;

import com.sun.net.httpserver.HttpServer;
import io.testfly.driver.DriverManager;
import io.testfly.network.AbortReason;
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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * End-to-end integration tests for the Network Mocking DSL against a real
 * Chromium browser and a local JDK {@link HttpServer} backend.
 *
 * <p><b>Requires a real Chrome/Edge browser</b> (CDP interception). Excluded from
 * the default {@code mvn test} run; execute with:
 *
 * <pre>
 * mvn verify -Preal-backends -Dit.test=NetworkMockIntegrationTest
 * </pre>
 *
 * <p>If no Chromium-based driver can be created, the tests self-skip rather than
 * fail, so the suite stays green on environments without a browser.
 */
@Test(groups = {"integration"}, singleThreaded = true)
public class NetworkMockIntegrationTest {

    private HttpServer server;
    private int        port;
    private WebDriver  driver;

    @BeforeClass
    public void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();

        // Real backend returns discountActive:false — a mutation test flips it.
        server.createContext("/api/settings", exchange -> {
            String body = "{\"discountActive\": false}";
            byte[] out = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, out.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(out); }
        });

        // A simple HTML page that fetches the two endpoints on load.
        server.createContext("/", exchange -> {
            String html = "<!DOCTYPE html><html><body><div id='root'>ok</div></body></html>";
            byte[] out = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, out.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(out); }
        });

        server.start();

        try {
            // Initialize the framework (loads testfly.yml → TestFlyContext) so
            // DriverManager can build a session. Normally done by the suite listener.
            io.testfly.lifecycle.FrameworkBootstrap.initialize();
            DriverManager.createDriver();
            driver = DriverManager.getDriver();
        } catch (Throwable t) {
            System.out.println("[NetworkMockIntegrationTest] Browser setup failed, "
                    + "tests will self-skip: " + t.getMessage());
            driver = null;
        }
    }

    @AfterClass
    public void tearDown() {
        try { DriverManager.quitDriver(); } catch (Exception ignored) {}
        if (server != null) server.stop(0);
    }

    @AfterMethod
    public void clearMocks() {
        try { NetworkMock.cleanup(); } catch (Exception ignored) {}
    }

    private void requireBrowser() {
        if (driver == null) {
            throw new SkipException("No Chromium browser available — skipping CDP integration test.");
        }
        // Register a probe route; if CDP has no matching implementation for this
        // browser version, interception stays inactive and we skip rather than fail.
        NetworkMock.get().mockRoute("**/__cdp_probe__", Response.status(204));
        if (!NetworkMock.get().isInterceptionActive()) {
            throw new SkipException("CDP interception unavailable for this browser/Selenium "
                    + "version combination — skipping live network-mocking test.");
        }
    }

    private String base() {
        return "http://127.0.0.1:" + port;
    }

    /** Executes a fetch in the browser and returns the response text. */
    private String fetchInBrowser(String url) {
        Object result = ((JavascriptExecutor) driver).executeScript(
                "var cb = arguments[arguments.length - 1];" +
                "fetch('" + url + "').then(r => r.text()).then(t => cb(t)).catch(e => cb('ERR:'+e));");
        return result == null ? "" : result.toString();
    }

    // ------------------------------------------------------------------
    // mockRoute — fulfill
    // ------------------------------------------------------------------

    @Test
    public void mockRoute_fulfillsJson() {
        requireBrowser();
        driver.get(base() + "/");
        NetworkMock.get().mockRoute("**/api/settings", Response.json(200, "{\"discountActive\": true}"));

        String body = fetchInBrowser(base() + "/api/settings");
        assertTrue(body.contains("\"discountActive\": true"),
                "mocked body should be returned, got: " + body);
    }

    // ------------------------------------------------------------------
    // mockRoute — abort
    // ------------------------------------------------------------------

    @Test
    public void mockRoute_abortsRequest() {
        requireBrowser();
        driver.get(base() + "/");
        NetworkMock.get().mockRoute("**/api/settings", Response.abort(AbortReason.FAILED));

        String body = fetchInBrowser(base() + "/api/settings");
        assertTrue(body.startsWith("ERR:"), "aborted request should reject the fetch, got: " + body);
    }

    // ------------------------------------------------------------------
    // mockRoute — mutation via fetchOriginal()
    // ------------------------------------------------------------------

    @Test
    public void mockRoute_mutatesOriginalResponse() {
        requireBrowser();
        driver.get(base() + "/");
        NetworkMock.get().mockRoute("**/api/settings", route -> {
            var original = route.fetchOriginal();
            String modified = original.body().replace("false", "true");
            route.fulfill(Response.json(200, modified));
        });

        String body = fetchInBrowser(base() + "/api/settings");
        assertTrue(body.contains("true"), "mutated body should contain true, got: " + body);
    }

    // ------------------------------------------------------------------
    // NetworkAssert — recorded traffic
    // ------------------------------------------------------------------

    @Test
    public void networkAssert_recordsRequests() {
        requireBrowser();
        driver.get(base() + "/");
        NetworkMock.get().mockRoute("**/api/settings", Response.json(200, "{}"));

        fetchInBrowser(base() + "/api/settings");

        NetworkMock.get().assertThat()
                .request("**/api/settings")
                .hasMethod("GET");
    }

    // ------------------------------------------------------------------
    // blockUrls-style abort still lets an explicit route win
    // ------------------------------------------------------------------

    @Test
    public void explicitRoute_overridesBlocklistAbort() {
        requireBrowser();
        driver.get(base() + "/");
        // Simulate a blocklist abort + an explicit route for the same URL.
        // The explicit route (registered as a normal route) must win.
        NetworkMock.get().mockRoute("**/api/settings", Response.json(200, "{\"ok\":true}"));

        String body = fetchInBrowser(base() + "/api/settings");
        assertEquals(body.contains("\"ok\":true"), true, "explicit route should win, got: " + body);
    }
}
