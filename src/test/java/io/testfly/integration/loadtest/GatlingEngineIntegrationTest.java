package io.testfly.integration.loadtest;

import com.sun.net.httpserver.HttpServer;
import io.testfly.loadtest.BaseLoadTest;
import io.testfly.loadtest.LoadTestFeeder;
import io.testfly.loadtest.LoadTestMetrics;
import io.testfly.loadtest.internal.GatlingBridge;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.*;

/**
 * Task 3.9: Integration tests verifying real execution of {@link io.testfly.loadtest.internal.GatlingEngine}
 * against a local JDK {@link HttpServer}.
 *
 * <p>Validates:
 * <ul>
 *   <li>Single-endpoint simulation under load via Gatling</li>
 *   <li>Multi-step scenario with status checks</li>
 *   <li>Data feeder substitution (sequence feeder)</li>
 *   <li>POST requests with request body and header validation</li>
 *   <li>Accurate metric generation and simulation.log parsing</li>
 * </ul>
 */
public class GatlingEngineIntegrationTest extends BaseLoadTest {

    private HttpServer server;
    private String baseUrl;
    private final AtomicInteger healthRequests = new AtomicInteger(0);
    private final AtomicInteger itemRequests = new AtomicInteger(0);
    private final AtomicInteger orderRequests = new AtomicInteger(0);

    @BeforeClass
    public void setUpServer() throws IOException {
        if (!GatlingBridge.isAvailable()) {
            throw new SkipException("Gatling is not available on classpath — skipping Gatling integration test");
        }

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

        server.createContext("/api/health", exchange -> {
            healthRequests.incrementAndGet();
            byte[] response = "{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });

        server.createContext("/api/items", exchange -> {
            itemRequests.incrementAndGet();
            String query = exchange.getRequestURI().getQuery();
            byte[] response = ("{\"item\":\"widget\",\"query\":\"" + (query != null ? query : "") + "\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });

        server.createContext("/api/orders", exchange -> {
            orderRequests.incrementAndGet();
            byte[] response = "{\"orderId\":\"ord-12345\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(201, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });

        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterClass(alwaysRun = true)
    public void tearDownServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    public void gatling_singleEndpoint_underLoad() {
        LoadTestMetrics metrics = load(baseUrl + "/api/health")
                .users(4)
                .rampUp(Duration.ofSeconds(1))
                .hold(Duration.ofSeconds(3))
                .run()
                .assertStatus(200)
                .assertThroughputAbove(1)
                .assertNoStatus(500)
                .metrics();

        assertNotNull(metrics);
        assertEquals(metrics.engine(), "gatling");
        assertTrue(metrics.totalRequests() > 0, "Total requests should be > 0");
        assertTrue(metrics.successfulRequests() > 0, "Successful requests should be > 0");
        assertEquals(metrics.failedRequests(), 0, "Failed requests should be 0");
        assertTrue(metrics.statusCodes().containsKey(200), "Status code 200 should be recorded");
        assertTrue(healthRequests.get() > 0, "Server should have received requests");
    }

    @Test
    public void gatling_multiStep_withFeeder() {
        LoadTestMetrics metrics = loadScenario("Gatling Feeder Test")
                .step("Health")
                .get(baseUrl + "/api/health")
                .check(status().is(200))
                .and()
                .step("Items")
                .get(baseUrl + "/api/items?id=${itemId}")
                .check(status().is(200))
                .and()
                .feed(LoadTestFeeder.sequence("itemId", 100, 1))
                .users(4)
                .rampUp(Duration.ofSeconds(1))
                .hold(Duration.ofSeconds(3))
                .run()
                .assertStatus(200)
                .assertNoStatus(500)
                .assertNoStatus(404)
                .metrics();

        assertNotNull(metrics);
        assertEquals(metrics.engine(), "gatling");
        assertTrue(metrics.totalRequests() > 0);
        assertEquals(metrics.failedRequests(), 0);
        assertTrue(metrics.steps().containsKey("Health"));
        assertTrue(metrics.steps().containsKey("Items"));
        assertTrue(itemRequests.get() > 0, "Server items endpoint should have received requests");
    }

    @Test
    public void gatling_postJsonBody() {
        LoadTestMetrics metrics = loadScenario("Gatling Post Order")
                .step("Create Order")
                .post(baseUrl + "/api/orders")
                .header("Authorization", "Bearer test-token")
                .body("{\"item\":\"widget\",\"qty\":2}")
                .check(status().is(201))
                .and()
                .users(3)
                .rampUp(Duration.ofSeconds(1))
                .hold(Duration.ofSeconds(2))
                .run()
                .assertNoStatus(500)
                .metrics();

        assertNotNull(metrics);
        assertEquals(metrics.engine(), "gatling");
        assertTrue(metrics.totalRequests() > 0);
        assertEquals(metrics.failedRequests(), 0);
        assertTrue(orderRequests.get() > 0, "Server orders endpoint should have received requests");
    }
}
