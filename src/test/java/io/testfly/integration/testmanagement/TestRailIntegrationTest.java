package io.testfly.integration.testmanagement;

import com.sun.net.httpserver.HttpServer;
import io.testfly.config.TestFlyConfig;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.testng.Assert.*;

/**
 * Integration tests for the TestRail REST client.
 * Uses a local JDK {@link com.sun.net.httpserver.HttpServer} to stub
 * the TestRail v2 API — no external services required.
 *
 * <p>
 * Run with:
 * 
 * <pre>
 * mvn verify -Preal-backends -Dit.test=TestRailIntegrationTest
 * </pre>
 */
public class TestRailIntegrationTest {

    private HttpServer server;
    private int port;

    /** Captures the last request for assertion. */
    private volatile String lastAuthHeader;
    private volatile String lastContentType;
    private volatile String lastRequestBody;
    private volatile String lastRequestPath;
    private volatile String lastRequestMethod;

    @BeforeClass
    public void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();

        // Stub: POST /index.php?/api/v2/add_run/{projectId}
        server.createContext("/index.php", exchange -> {
            lastRequestMethod = exchange.getRequestMethod();
            lastRequestPath = exchange.getRequestURI().toString();
            lastAuthHeader = exchange.getRequestHeaders().getFirst("Authorization");
            lastContentType = exchange.getRequestHeaders().getFirst("Content-Type");

            // Read request body
            lastRequestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

            String path = exchange.getRequestURI().toString();

            if (path.contains("/api/v2/add_run/")) {
                // Return a run creation response
                String response = "{\"id\": 42, \"name\": \"TestFly Run\", \"suite_id\": 10}";
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            } else if (path.contains("/api/v2/add_result_for_case/")) {
                // Return a result creation response
                String response = "{\"id\": 100, \"status_id\": 1}";
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            } else {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
            }
        });

        server.setExecutor(null);
        server.start();
    }

    @AfterClass
    public void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    // ── Auth header construction ────────────────────────────────────────

    @Test(groups = { "integration" })
    public void createRun_sendsBasicAuthHeader() throws Exception {
        Object client = createTestRailClient("testuser@example.com", "api-key-123");
        invokeCreateRun(client, 1, 10, "Integration Test Run");

        assertNotNull(lastAuthHeader, "Authorization header must be present");
        assertTrue(lastAuthHeader.startsWith("Basic "), "Should use Basic auth");

        // Verify the decoded credentials
        String decoded = new String(
                Base64.getDecoder().decode(lastAuthHeader.substring("Basic ".length())),
                StandardCharsets.UTF_8);
        assertEquals(decoded, "testuser@example.com:api-key-123",
                "Auth header should encode username:apiKey");
    }

    @Test(groups = { "integration" })
    public void createRun_sendsJsonContentType() throws Exception {
        Object client = createTestRailClient("user@test.com", "key");
        invokeCreateRun(client, 1, 10, "Test Run");

        assertEquals(lastContentType, "application/json",
                "Content-Type should be application/json");
    }

    // ── Result payload mapping ──────────────────────────────────────────

    @Test(groups = { "integration" })
    public void createRun_sendsCorrectPayload() throws Exception {
        Object client = createTestRailClient("user@test.com", "key");
        invokeCreateRun(client, 5, 10, "My Run");

        assertTrue(lastRequestPath.contains("/api/v2/add_run/5"),
                "URL should include project ID. Got: " + lastRequestPath);
        assertTrue(lastRequestBody.contains("\"name\":\"My Run\""),
                "Body should contain run name. Got: " + lastRequestBody);
        assertTrue(lastRequestBody.contains("\"suite_id\":10"),
                "Body should contain suite_id. Got: " + lastRequestBody);
        assertTrue(lastRequestBody.contains("\"include_all\":true"),
                "Body should include all cases. Got: " + lastRequestBody);
    }

    @Test(groups = { "integration" })
    public void createRun_returnsRunId() throws Exception {
        Object client = createTestRailClient("user@test.com", "key");
        int runId = invokeCreateRun(client, 1, 10, "Test Run");

        assertEquals(runId, 42, "Should parse run ID from response");
    }

    @Test(groups = { "integration" })
    public void addResult_sendsCorrectStatusMapping() throws Exception {
        Object client = createTestRailClient("user@test.com", "key");

        // PASSED → status_id 1
        invokeAddResult(client, 42, 100, "PASSED", null);
        assertTrue(lastRequestPath.contains("/api/v2/add_result_for_case/42/100"),
                "URL should include run and case IDs. Got: " + lastRequestPath);
        assertTrue(lastRequestBody.contains("\"status_id\":1"),
                "PASSED should map to status_id 1. Got: " + lastRequestBody);
    }

    @Test(groups = { "integration" })
    public void addResult_failedMapsToStatusId5() throws Exception {
        Object client = createTestRailClient("user@test.com", "key");

        invokeAddResult(client, 42, 200, "FAILED", "Assertion failed: expected X");
        assertTrue(lastRequestBody.contains("\"status_id\":5"),
                "FAILED should map to status_id 5. Got: " + lastRequestBody);
        assertTrue(lastRequestBody.contains("Assertion failed"),
                "Body should include comment. Got: " + lastRequestBody);
    }

    @Test(groups = { "integration" })
    public void addResult_skippedMapsToStatusId4() throws Exception {
        Object client = createTestRailClient("user@test.com", "key");

        invokeAddResult(client, 42, 300, "SKIPPED", null);
        assertTrue(lastRequestBody.contains("\"status_id\":4"),
                "SKIPPED should map to status_id 4 (Retest). Got: " + lastRequestBody);
    }

    // ── Error handling ──────────────────────────────────────────────────

    @Test(groups = { "integration" })
    public void createRun_401Response_throwsClearException() throws Exception {
        // Start a server that returns 401
        HttpServer authServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int authPort = authServer.getAddress().getPort();
        authServer.createContext("/index.php", exchange -> {
            String body = "{\"error\":\"Authentication failed\"}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(401, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        authServer.setExecutor(null);
        authServer.start();

        try {
            Object client = createTestRailClientWithUrl("http://127.0.0.1:" + authPort, "bad@user.com", "wrong-key");
            try {
                invokeCreateRun(client, 1, 10, "Should Fail");
                fail("Should have thrown for 401 response");
            } catch (Exception e) {
                String msg = getRootCauseMessage(e);
                assertTrue(msg.contains("401"), "Error should mention HTTP 401. Got: " + msg);
            }
        } finally {
            authServer.stop(0);
        }
    }

    @Test(groups = { "integration" })
    public void addResult_500Response_throwsClearException() throws Exception {
        // Start a server that returns 500
        HttpServer errorServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int errorPort = errorServer.getAddress().getPort();
        errorServer.createContext("/index.php", exchange -> {
            String body = "{\"error\":\"Internal server error\"}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        errorServer.setExecutor(null);
        errorServer.start();

        try {
            Object client = createTestRailClientWithUrl("http://127.0.0.1:" + errorPort, "user@test.com", "key");
            try {
                invokeAddResult(client, 1, 100, "PASSED", null);
                fail("Should have thrown for 500 response");
            } catch (Exception e) {
                String msg = getRootCauseMessage(e);
                assertTrue(msg.contains("500"), "Error should mention HTTP 500. Got: " + msg);
            }
        } finally {
            errorServer.stop(0);
        }
    }

    // ── Reflection helpers (TestRailClient is package-private) ──────────

    private Object createTestRailClient(String username, String apiKey) throws Exception {
        return createTestRailClientWithUrl("http://127.0.0.1:" + port, username, apiKey);
    }

    private Object createTestRailClientWithUrl(String url, String username, String apiKey) throws Exception {
        TestFlyConfig.TestManagement.TestRail cfg = new TestFlyConfig.TestManagement.TestRail();
        cfg.setUrl(url);
        cfg.setUsername(username);
        cfg.setApiKey(apiKey);

        Class<?> clazz = Class.forName("io.testfly.testmanagement.TestRailClient");
        Constructor<?> ctor = clazz.getDeclaredConstructor(TestFlyConfig.TestManagement.TestRail.class);
        ctor.setAccessible(true);
        return ctor.newInstance(cfg);
    }

    private int invokeCreateRun(Object client, int projectId, int suiteId, String runName) throws Exception {
        Method m = client.getClass().getDeclaredMethod("createRun", int.class, int.class, String.class);
        m.setAccessible(true);
        return (int) m.invoke(client, projectId, suiteId, runName);
    }

    private void invokeAddResult(Object client, int runId, int caseId, String status, String comment) throws Exception {
        Method m = client.getClass().getDeclaredMethod("addResult", int.class, int.class, String.class, String.class);
        m.setAccessible(true);
        m.invoke(client, runId, caseId, status, comment);
    }

    private String getRootCauseMessage(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
    }
}
