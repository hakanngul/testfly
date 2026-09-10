package io.testfly.unit.loadtest;

import com.sun.net.httpserver.HttpServer;
import io.testfly.loadtest.LoadScenario;
import io.testfly.loadtest.LoadTestAssert;
import io.testfly.loadtest.LoadTestConfig;
import io.testfly.loadtest.LoadTestMetrics;
import io.testfly.loadtest.LoadTestRunner;
import io.testfly.loadtest.internal.JdkLoadEngine;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.*;

/**
 * Tests {@link JdkLoadEngine} against a local mock HTTP server.
 * No external network access required.
 */
@Test(singleThreaded = true)
public class JdkLoadEngineTest {

    private HttpServer server;
    private int port;
    private final AtomicInteger requestCount = new AtomicInteger(0);

    @BeforeClass
    public void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();

        server.createContext("/api/health", exchange -> {
            requestCount.incrementAndGet();
            String response = "{\"status\":\"ok\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        });

        server.createContext("/api/slow", exchange -> {
            requestCount.incrementAndGet();
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            String response = "{\"slow\":true}";
            exchange.sendResponseHeaders(200, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        });

        server.createContext("/api/error", exchange -> {
            requestCount.incrementAndGet();
            String response = "{\"error\":\"internal\"}";
            exchange.sendResponseHeaders(500, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        });

        server.setExecutor(null);
        server.start();
    }

    @AfterClass
    public void stopServer() {
        if (server != null) server.stop(0);
    }

    // ── Engine basics ────────────────────────────────────────────────────

    @Test
    public void testEngineName() {
        JdkLoadEngine engine = new JdkLoadEngine();
        assertEquals(engine.name(), "jdk");
        assertTrue(engine.isAvailable());
    }

    // ── Single user, single step ─────────────────────────────────────────

    @Test
    public void testSingleUserSingleStep() {
        requestCount.set(0);

        LoadScenario scenario = LoadScenario.single("/api/health")
                .baseUrl("http://localhost:" + port)
                .users(1)
                .rampUp(java.time.Duration.ofSeconds(0))
                .hold(java.time.Duration.ofSeconds(2))
                .cooldown(java.time.Duration.ofSeconds(0))
                .engine("jdk");

        LoadTestAssert result = LoadTestRunner.run(scenario);
        LoadTestMetrics metrics = result.metrics();

        assertEquals(metrics.scenarioName(), "/api/health");
        assertEquals(metrics.users(), 1);
        assertEquals(metrics.engine(), "jdk");
        assertTrue(metrics.totalRequests() > 0, "Should have made at least 1 request");
        assertEquals(metrics.failedRequests(), 0, "No failures expected");
        assertTrue(metrics.throughputRps() > 0);
        assertTrue(requestCount.get() > 0);
    }

    // ── Multiple users ───────────────────────────────────────────────────

    @Test
    public void testMultipleUsers() {
        requestCount.set(0);

        LoadScenario scenario = LoadScenario.named("Multi User")
                .baseUrl("http://localhost:" + port)
                .get("/api/health")
                .users(5)
                .rampUp(java.time.Duration.ofSeconds(1))
                .hold(java.time.Duration.ofSeconds(2))
                .cooldown(java.time.Duration.ofSeconds(0))
                .engine("jdk");

        LoadTestMetrics metrics = LoadTestRunner.run(scenario).metrics();

        assertEquals(metrics.users(), 5);
        assertTrue(metrics.totalRequests() >= 5, "At least 1 request per user");
        assertEquals(metrics.failedRequests(), 0);
    }

    // ── Latency percentiles ──────────────────────────────────────────────

    @Test
    public void testLatencyPercentiles() {
        LoadScenario scenario = LoadScenario.named("Slow Endpoint")
                .baseUrl("http://localhost:" + port)
                .get("/api/slow")
                .users(2)
                .rampUp(java.time.Duration.ofSeconds(0))
                .hold(java.time.Duration.ofSeconds(2))
                .engine("jdk");

        LoadTestMetrics metrics = LoadTestRunner.run(scenario).metrics();

        assertTrue(metrics.p50LatencyMs() > 0);
        assertTrue(metrics.p95LatencyMs() >= metrics.p50LatencyMs());
        assertTrue(metrics.p99LatencyMs() >= metrics.p95LatencyMs());
        assertTrue(metrics.maxLatencyMs() >= metrics.p99LatencyMs());
        assertTrue(metrics.minLatencyMs() <= metrics.p50LatencyMs());
        assertTrue(metrics.meanLatencyMs() > 0);
    }

    // ── Error handling ───────────────────────────────────────────────────

    @Test
    public void testErrorEndpoint() {
        LoadScenario scenario = LoadScenario.named("Error Test")
                .baseUrl("http://localhost:" + port)
                .get("/api/error")
                .users(2)
                .rampUp(java.time.Duration.ofSeconds(0))
                .hold(java.time.Duration.ofSeconds(2))
                .engine("jdk");

        LoadTestMetrics metrics = LoadTestRunner.run(scenario).metrics();

        // The /api/error endpoint returns 500 but the request itself succeeds
        // (no exception thrown), so it counts as successful at the HTTP level
        assertTrue(metrics.totalRequests() > 0);
    }

    // ── Assertions pass/fail ─────────────────────────────────────────────

    @Test
    public void testAssertionsPass() {
        LoadScenario scenario = LoadScenario.named("Assert Pass")
                .baseUrl("http://localhost:" + port)
                .get("/api/health")
                .users(2)
                .rampUp(java.time.Duration.ofSeconds(0))
                .hold(java.time.Duration.ofSeconds(2))
                .engine("jdk");

        // Should not throw
        LoadTestRunner.run(scenario)
                .assertThroughputAbove(0)
                .assertP95Below(10000)
                .assertErrorRateBelow(1.0);
    }

    @Test(expectedExceptions = AssertionError.class)
    public void testThroughputAssertionFails() {
        LoadScenario scenario = LoadScenario.named("Assert Fail")
                .baseUrl("http://localhost:" + port)
                .get("/api/health")
                .users(1)
                .rampUp(java.time.Duration.ofSeconds(0))
                .hold(java.time.Duration.ofSeconds(1))
                .engine("jdk");

        LoadTestRunner.run(scenario).assertThroughputAbove(999999);
    }

    // ── Multi-step scenario ──────────────────────────────────────────────

    @Test
    public void testMultiStepScenario() {
        requestCount.set(0);

        LoadScenario scenario = LoadScenario.named("Multi Step")
                .baseUrl("http://localhost:" + port)
                .users(2)
                .rampUp(java.time.Duration.ofSeconds(0))
                .hold(java.time.Duration.ofSeconds(2))
                .step("Health").get("/api/health").and()
                .step("Slow").get("/api/slow").and()
                .engine("jdk");

        LoadTestMetrics metrics = LoadTestRunner.run(scenario).metrics();

        assertEquals(metrics.steps().size(), 2);
        assertTrue(metrics.steps().containsKey("Health"));
        assertTrue(metrics.steps().containsKey("Slow"));
        assertTrue(metrics.steps().get("Health").totalRequests() > 0);
        assertTrue(metrics.steps().get("Slow").totalRequests() > 0);
    }

    // ── Percentile calculation ───────────────────────────────────────────

    @Test
    public void testPercentileCalculation() {
        double[] sorted = {10, 20, 30, 40, 50, 60, 70, 80, 90, 100};

        assertEquals(JdkLoadEngine.percentile(sorted, 50), 55.0, 0.01);
        assertEquals(JdkLoadEngine.percentile(sorted, 90), 91.0, 0.01);
        assertEquals(JdkLoadEngine.percentile(sorted, 95), 95.5, 0.01);
        assertEquals(JdkLoadEngine.percentile(sorted, 99), 99.1, 0.01);
        assertEquals(JdkLoadEngine.percentile(sorted, 0), 10.0, 0.01);
        assertEquals(JdkLoadEngine.percentile(sorted, 100), 100.0, 0.01);
    }

    @Test
    public void testPercentileEmptyArray() {
        assertEquals(JdkLoadEngine.percentile(new double[0], 50), 0.0);
    }

    @Test
    public void testPercentileSingleElement() {
        assertEquals(JdkLoadEngine.percentile(new double[]{42}, 50), 42.0);
        assertEquals(JdkLoadEngine.percentile(new double[]{42}, 99), 42.0);
    }

    @Test
    public void testMeanCalculation() {
        assertEquals(JdkLoadEngine.mean(new double[]{10, 20, 30}), 20.0, 0.01);
        assertEquals(JdkLoadEngine.mean(new double[0]), 0.0);
    }

    // ── JSONPath extraction ──────────────────────────────────────────────

    @Test
    public void testExtractJsonPath() {
        String json = "{\"status\":\"ok\",\"data\":{\"id\":42}}";

        assertEquals(JdkLoadEngine.extractJsonPath(json, "$.status"), "ok");
        // Nested extraction
        String nested = JdkLoadEngine.extractJsonPath(json, "$.data");
        assertNotNull(nested);
        assertTrue(nested.contains("42"));
    }

    @Test
    public void testExtractJsonPathMissing() {
        assertNull(JdkLoadEngine.extractJsonPath("{\"a\":1}", "$.b"));
        assertNull(JdkLoadEngine.extractJsonPath(null, "$.a"));
        assertNull(JdkLoadEngine.extractJsonPath("{\"a\":1}", null));
    }

    // ── No steps validation ──────────────────────────────────────────────

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testEmptyScenarioThrows() {
        LoadScenario scenario = LoadScenario.named("Empty")
                .baseUrl("http://localhost:" + port)
                .users(1)
                .engine("jdk");

        LoadTestRunner.run(scenario);
    }

    // ── No baseUrl validation ────────────────────────────────────────────

    @Test(expectedExceptions = IllegalStateException.class)
    public void testNoBaseUrlThrows() {
        LoadScenario scenario = LoadScenario.named("No URL")
                .get("/api/health")
                .users(1)
                .engine("jdk");

        LoadTestRunner.run(scenario);
    }
}
