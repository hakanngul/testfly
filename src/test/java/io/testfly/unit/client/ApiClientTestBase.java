package io.testfly.unit.client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.testfly.client.ApiAuth;
import io.testfly.client.ApiClient;
import io.testfly.config.TestFlyConfig;
import io.testfly.internal.TestFlyContext;
import io.testfly.internal.api.ApiExecution;
import io.testfly.metrics.ExecutionMetrics;
import org.mockito.MockedStatic;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

/**
 * Shared scaffolding for API client unit tests: a mocked {@link TestFlyContext} with a fresh
 * {@link TestFlyConfig}, loopback {@link HttpServer}s and full API state cleanup.
 */
public abstract class ApiClientTestBase {

    protected TestFlyConfig config;
    protected String testId;
    private MockedStatic<TestFlyContext> context;
    private final List<HttpServer> servers = new ArrayList<>();
    private final List<ExecutorService> executors = new ArrayList<>();

    @BeforeMethod
    public void setUpApiContext(Method method) {
        ApiExecution.shutdown();
        ApiExecution.cleanupTestContext();
        config = new TestFlyConfig();
        config.setApi(new TestFlyConfig.Api());
        context = mockStatic(TestFlyContext.class, CALLS_REAL_METHODS);
        context.when(TestFlyContext::isInitialized).thenReturn(true);
        context.when(TestFlyContext::getConfig).thenReturn(config);
        testId = getClass().getSimpleName() + "-" + method.getName();
        TestFlyContext.setCurrentTestId(testId);
        TestFlyContext.setCurrentTest(getClass(), method);
        ExecutionMetrics.markStart(testId);
        ExecutionMetrics.clearSteps(testId);
    }

    @AfterMethod(alwaysRun = true)
    public void tearDownApiContext() {
        try {
            ApiExecution.cleanupTestContext();
            ApiExecution.shutdown();
            ApiClient.clearInterceptors();
            ApiClient.clearGlobalSpec();
            ApiAuth.clearTokenCache();
            TestFlyContext.clearCurrentTest();
        } finally {
            if (context != null) context.close();
            servers.forEach(s -> s.stop(0));
            servers.clear();
            executors.forEach(ExecutorService::shutdownNow);
            executors.clear();
        }
    }

    /** Starts a loopback server on 127.0.0.1 and returns its base URL. */
    protected String server(HttpHandler handler) throws IOException {
        return server("127.0.0.1", handler);
    }

    /** Starts a loopback server and returns its base URL using {@code host} as the URL host. */
    protected String server(String host, HttpHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.createContext("/", handler);
        server.start();
        servers.add(server);
        executors.add(executor);
        return "http://" + host + ":" + server.getAddress().getPort();
    }

    protected static void reply(HttpExchange exchange, int status, String body) throws IOException {
        reply(exchange, status, body, Map.of());
    }

    protected static void reply(HttpExchange exchange, int status, String body, Map<String, String> headers)
            throws IOException {
        reply(exchange, status, body.getBytes(StandardCharsets.UTF_8), headers);
    }

    protected static void reply(HttpExchange exchange, int status, byte[] body, Map<String, String> headers)
            throws IOException {
        headers.forEach((k, v) -> exchange.getResponseHeaders().add(k, v));
        boolean empty = body.length == 0 || "HEAD".equals(exchange.getRequestMethod());
        exchange.sendResponseHeaders(status, empty ? -1 : body.length);
        if (!empty) exchange.getResponseBody().write(body);
        exchange.close();
    }

    protected static byte[] readBody(HttpExchange exchange) throws IOException {
        return exchange.getRequestBody().readAllBytes();
    }

    /** All step names logged for the current test, joined by newlines. */
    protected String steps() {
        return ExecutionMetrics.getTimings().stream()
                .filter(t -> t.getTestId().equals(testId))
                .flatMap(t -> t.getSteps().stream())
                .map(io.testfly.steps.StepRecord::getName)
                .collect(Collectors.joining("\n"));
    }
}
