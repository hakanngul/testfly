package io.testfly.api.mock;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.testfly.api.TestFlyApi;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Singleton wrapper around WireMock to provide API mocking for tests.
 * Requires {@code wiremock-standalone} or {@code wiremock-jre8} on the classpath.
 *
 * <p><b>Parallel tests:</b> register stubs through {@link #stubFor(MappingBuilder)},
 * {@link #stubJson(String, String, int, String)} or {@link #stubGet(String, int, String)} - they are
 * bound to this server instance and recorded per thread, so {@link #resetOwnStubs()} removes only
 * the stubs of the calling test. The static {@code WireMock.stubFor(...)} DSL still targets this
 * server, but it is JVM-global and therefore not safe for parallel tests.
 */
@TestFlyApi(since = "1.2.0")
public class ApiMockServer {

    private static final ApiMockServer INSTANCE = new ApiMockServer();
    private static final ThreadLocal<java.util.List<java.util.UUID>> OWN_STUBS =
            ThreadLocal.withInitial(java.util.ArrayList::new);
    private volatile WireMockServer server;
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
     * Clears all registered stubs and request journals (every thread's stubs).
     */
    public void reset() {
        OWN_STUBS.remove();
        WireMockServer current = server;
        if (isRunning.get() && current != null) {
            current.resetAll();
        }
    }

    /**
     * Removes only the stubs registered by the current thread through this class. Use it instead
     * of {@link #reset()} when tests run in parallel against the shared server.
     */
    @TestFlyApi(since = "1.1.0")
    public void resetOwnStubs() {
        java.util.List<java.util.UUID> own = OWN_STUBS.get();
        WireMockServer current = server;
        if (current != null) {
            for (java.util.UUID id : own) {
                try {
                    current.removeStub(id);
                } catch (RuntimeException alreadyRemoved) {
                    // removed by reset() or another cleanup
                }
            }
        }
        OWN_STUBS.remove();
    }

    /**
     * Registers a stub on this server (starting it if needed) and records it for
     * {@link #resetOwnStubs()}.
     *
     * <pre>
     * mockServer().stubFor(WireMock.post("/orders").willReturn(WireMock.created()));
     * </pre>
     */
    @TestFlyApi(since = "1.1.0")
    public StubMapping stubFor(MappingBuilder mapping) {
        if (!isRunning.get()) start();
        StubMapping stub = server.stubFor(mapping);
        OWN_STUBS.get().add(stub.getId());
        return stub;
    }

    /**
     * Registers a stub for any HTTP method that returns a JSON body. {@code urlPattern} is a regex.
     */
    @TestFlyApi(since = "1.1.0")
    public StubMapping stubJson(String method, String urlPattern, int status, String jsonBody) {
        return stubFor(WireMock.request(method.toUpperCase(java.util.Locale.ROOT), WireMock.urlMatching(urlPattern))
                .willReturn(WireMock.aResponse()
                        .withStatus(status)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonBody)));
    }

    /**
     * Registers a simple GET stub that returns a JSON body.
     */
    public void stubGet(String urlPattern, int status, String jsonBody) {
        stubJson("GET", urlPattern, status, jsonBody);
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
