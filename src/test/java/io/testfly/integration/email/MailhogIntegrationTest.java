package io.testfly.integration.email;

import com.sun.net.httpserver.HttpServer;
import io.testfly.config.TestFlyConfig;
import io.testfly.email.Email;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.testng.Assert.*;

/**
 * Integration tests for the Mailhog email provider.
 * Uses a local JDK {@link com.sun.net.httpserver.HttpServer} to stub
 * the Mailhog HTTP API — no Docker or external services required.
 *
 * <p>
 * Run with:
 * 
 * <pre>
 * mvn verify -Preal-backends -Dit.test=MailhogIntegrationTest
 * </pre>
 */
@Test(singleThreaded = true)
public class MailhogIntegrationTest {

    private HttpServer server;
    private int port;

    /** Tracks how the last request was received for assertion. */
    private volatile String lastRequestMethod;
    private volatile String lastRequestPath;

    @BeforeClass
    public void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();

        // Stub: GET /api/v2/messages — returns sample emails
        server.createContext("/api/v2/messages", exchange -> {
            lastRequestMethod = exchange.getRequestMethod();
            lastRequestPath = exchange.getRequestURI().toString();

            String responseJson = """
                    {
                      "total": 2,
                      "count": 2,
                      "start": 0,
                      "items": [
                        {
                          "Content": {
                            "Headers": {
                              "Subject": ["Welcome to TestFly"],
                              "From": ["noreply@testfly.io"],
                              "To": ["user@example.com"],
                              "Content-Type": ["text/plain"]
                            },
                            "Body": "Hello! Welcome to TestFly automation framework."
                          },
                          "Raw": { "To": ["user@example.com"] }
                        },
                        {
                          "Content": {
                            "Headers": {
                              "Subject": ["Verify your account"],
                              "From": ["noreply@testfly.io"],
                              "To": ["admin@example.com"],
                              "Content-Type": ["text/html"]
                            },
                            "Body": "<p>Click <a href=\\"https://example.com/verify\\">here</a> to verify.</p>"
                          },
                          "Raw": { "To": ["admin@example.com"] }
                        }
                      ]
                    }
                    """;
            byte[] bytes = responseJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        // Stub: DELETE /api/v1/messages — clears messages
        server.createContext("/api/v1/messages", exchange -> {
            lastRequestMethod = exchange.getRequestMethod();
            lastRequestPath = exchange.getRequestURI().toString();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
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

    // ── Tests ───────────────────────────────────────────────────────────

    @Test(groups = { "integration" })
    public void fetchAll_parsesMessagesFromStub() throws Exception {
        Object provider = createMailhogProvider("127.0.0.1", port);
        List<?> emails = invokeFetchAll(provider);

        assertNotNull(emails, "fetchAll should return a non-null list");
        assertEquals(emails.size(), 2, "Should parse 2 emails from stub response");

        Email first = (Email) emails.get(0);
        assertEquals(first.subject(), "Welcome to TestFly");
        assertEquals(first.from(), "noreply@testfly.io");
        assertEquals(first.to(), "user@example.com");
        assertTrue(first.body().contains("Welcome"), "Plain body should contain greeting");
    }

    @Test(groups = { "integration" })
    public void fetchAll_parsesHtmlBody() throws Exception {
        Object provider = createMailhogProvider("127.0.0.1", port);
        List<?> emails = invokeFetchAll(provider);

        Email second = (Email) emails.get(1);
        assertEquals(second.subject(), "Verify your account");
        assertTrue(second.htmlBody().contains("verify"), "HTML body should contain verify link");
    }

    @Test(groups = { "integration" })
    public void fetchAll_sendsCorrectRequestPath() throws Exception {
        Object provider = createMailhogProvider("127.0.0.1", port);
        invokeFetchAll(provider);

        assertEquals(lastRequestMethod, "GET");
        assertTrue(lastRequestPath.contains("/api/v2/messages"),
                "Request path should contain /api/v2/messages. Got: " + lastRequestPath);
        assertTrue(lastRequestPath.contains("limit=200"),
                "Request should include limit=200 param. Got: " + lastRequestPath);
    }

    @Test(groups = { "integration" })
    public void clear_sendsDeleteRequest() throws Exception {
        Object provider = createMailhogProvider("127.0.0.1", port);
        invokeClear(provider);

        assertEquals(lastRequestMethod, "DELETE");
        assertTrue(lastRequestPath.contains("/api/v1/messages"),
                "Clear should DELETE /api/v1/messages. Got: " + lastRequestPath);
    }

    @Test(groups = { "integration" })
    public void fetchAll_serverError_throwsClearException() throws Exception {
        // Start a separate server that returns 500
        HttpServer errorServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int errorPort = errorServer.getAddress().getPort();
        errorServer.createContext("/api/v2/messages", exchange -> {
            String body = "{\"error\":\"internal server error\"}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        errorServer.setExecutor(null);
        errorServer.start();

        try {
            Object provider = createMailhogProvider("127.0.0.1", errorPort);
            try {
                invokeFetchAll(provider);
                fail("Should have thrown for 500 response");
            } catch (Exception e) {
                // MailhogProvider wraps errors in IllegalStateException
                String msg = getRootCauseMessage(e);
                assertTrue(msg.length() > 0, "Exception should have a message");
            }
        } finally {
            errorServer.stop(0);
        }
    }

    @Test(groups = { "integration" })
    public void fetchAll_connectionRefused_throwsClearException() throws Exception {
        // Use a port where nothing is listening
        Object provider = createMailhogProvider("127.0.0.1", 19999);
        try {
            invokeFetchAll(provider);
            fail("Should have thrown when server is unreachable");
        } catch (Exception e) {
            String msg = getRootCauseMessage(e);
            assertTrue(msg.length() > 0, "Exception should have a descriptive message");
        }
    }

    // ── Reflection helpers (MailhogProvider is package-private) ─────────

    private Object createMailhogProvider(String host, int port) throws Exception {
        TestFlyConfig.Email.Mailhog cfg = new TestFlyConfig.Email.Mailhog();
        cfg.setHost(host);
        cfg.setPort(port);

        Class<?> clazz = Class.forName("io.testfly.email.MailhogProvider");
        Constructor<?> ctor = clazz.getDeclaredConstructor(TestFlyConfig.Email.Mailhog.class);
        ctor.setAccessible(true);
        return ctor.newInstance(cfg);
    }

    @SuppressWarnings("unchecked")
    private List<?> invokeFetchAll(Object provider) throws Exception {
        java.lang.reflect.Method m = provider.getClass().getDeclaredMethod("fetchAll");
        m.setAccessible(true);
        return (List<?>) m.invoke(provider);
    }

    private void invokeClear(Object provider) throws Exception {
        java.lang.reflect.Method m = provider.getClass().getDeclaredMethod("clear");
        m.setAccessible(true);
        m.invoke(provider);
    }

    private String getRootCauseMessage(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
    }
}
