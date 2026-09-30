package io.testfly.api.mock;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.testfly.api.TestFlyApi;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Singleton wrapper around WireMock to provide API mocking for tests.
 * Requires {@code wiremock-standalone} or {@code wiremock-jre8} on the classpath.
 */
@TestFlyApi(since = "1.2.0")
public class ApiMockServer {

    private static final ApiMockServer INSTANCE = new ApiMockServer();
    private WireMockServer server;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    private ApiMockServer() {
    }

    public static ApiMockServer getInstance() {
        return INSTANCE;
    }

    /**
     * Starts the mock server on a random available port if not already started.
     */
    public synchronized void start() {
        if (isRunning.compareAndSet(false, true)) {
            ensureWireMockAvailable();
            server = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
            server.start();
            WireMock.configureFor("localhost", server.port());
        }
    }

    /**
     * Starts the mock server on a specific port.
     */
    public synchronized void start(int port) {
        if (isRunning.compareAndSet(false, true)) {
            ensureWireMockAvailable();
            server = new WireMockServer(WireMockConfiguration.wireMockConfig().port(port));
            server.start();
            WireMock.configureFor("localhost", port);
        }
    }

    /**
     * Returns the port the server is running on.
     */
    public int getPort() {
        if (!isRunning.get() || server == null) {
            throw new IllegalStateException("ApiMockServer is not running");
        }
        return server.port();
    }

    /**
     * Returns the base URL of the mock server (e.g. http://localhost:8080).
     */
    public String getBaseUrl() {
        return "http://localhost:" + getPort();
    }

    /**
     * Stops the mock server.
     */
    public synchronized void stop() {
        if (isRunning.compareAndSet(true, false)) {
            if (server != null) {
                server.stop();
                server = null;
            }
        }
    }

    /**
     * Clears all registered stubs.
     */
    public void reset() {
        if (isRunning.get() && server != null) {
            server.resetAll();
        }
    }

    /**
     * Registers a simple GET stub that returns a JSON body.
     */
    public void stubGet(String urlPattern, int status, String jsonBody) {
        if (!isRunning.get()) start();
        WireMock.stubFor(WireMock.get(WireMock.urlMatching(urlPattern))
                .willReturn(WireMock.aResponse()
                        .withStatus(status)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonBody)));
    }

    /**
     * Exposes the raw WireMock API for advanced stubbing.
     * @return The underlying WireMockServer instance.
     */
    public WireMockServer getRawServer() {
        return server;
    }

    private void ensureWireMockAvailable() {
        try {
            Class.forName("com.github.tomakehurst.wiremock.WireMockServer");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(
                    "[ApiMockServer] requires 'wiremock-standalone' on the classpath. "
                    + "Add it to your pom.xml:\n"
                    + "  <dependency>\n"
                    + "    <groupId>org.wiremock</groupId>\n"
                    + "    <artifactId>wiremock-standalone</artifactId>\n"
                    + "    <version>3.5.4</version>\n"
                    + "  </dependency>");
        }
    }
}
