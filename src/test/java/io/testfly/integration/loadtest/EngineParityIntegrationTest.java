package io.testfly.integration.loadtest;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.testfly.loadtest.CheckCondition;
import io.testfly.loadtest.LoadScenario;
import io.testfly.loadtest.LoadStep;
import io.testfly.loadtest.LoadTestMetrics;
import io.testfly.loadtest.LoadTestRunner;
import io.testfly.loadtest.internal.GatlingBridge;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.testng.Assert.*;

/**
 * Engine parity: the same scenario must be classified identically (OK/KO) by
 * the JDK and Gatling engines, and both must report exactly the number of
 * requests the local server observed (audit BUG-02/03/04/12).
 */
@Test(singleThreaded = true)
public class EngineParityIntegrationTest {

    private HttpServer server;
    private ExecutorService serverExecutor;
    private String baseUrl;
    private final Map<String, AtomicInteger> hits = new ConcurrentHashMap<>();

    @BeforeClass
    public void startServer() throws IOException {
        if (!GatlingBridge.isAvailable()) {
            throw new SkipException("Gatling is not available on classpath — skipping engine parity tests");
        }
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serverExecutor = Executors.newCachedThreadPool();
        server.setExecutor(serverExecutor);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterClass(alwaysRun = true)
    public void stopServer() {
        if (server != null) {
            server.stop(0);
        }
        if (serverExecutor != null) {
            serverExecutor.shutdownNow();
        }
    }

    @DataProvider(name = "checkKinds")
    public Object[][] checkKinds() {
        String ok = "{\"status\":\"ok\"}";
        String bizFail = "{\"success\":false,\"error\":\"insufficient funds\"}";
        String id5 = "{\"id\":5}";
        String id7 = "{\"id\":7}";
        String idNull = "{\"id\":null}";
        return new Object[][] {
                {"statusIs_mismatch", 200, ok, check(() -> LoadStep.status().is(201)), 1.0},
                {"statusIs_match", 201, ok, check(() -> LoadStep.status().is(201)), 0.0},
                {"statusIn_mismatch", 201, ok, check(() -> LoadStep.status().in(200, 204)), 1.0},
                {"bodyRegex_mismatch", 200, bizFail,
                        check(() -> LoadStep.jsonPath("$.success").matches("\"success\"\\s*:\\s*true")), 1.0},
                {"jsonExists_present", 200, id5, check(() -> LoadStep.jsonPath("$.id").exists()), 0.0},
                {"jsonExists_absent", 200, id5, check(() -> LoadStep.jsonPath("$.missing").exists()), 1.0},
                {"jsonEquals_match", 200, id5, check(() -> LoadStep.jsonPath("$.id").is(5)), 0.0},
                {"jsonEquals_mismatch", 200, id7, check(() -> LoadStep.jsonPath("$.id").is(5)), 1.0},
                {"noCheck_500", 500, "{\"error\":\"internal\"}", null, 1.0},
                // JSON null: observed with real Gatling 3.13.5 — a null value exists, but is(..) does not match
                {"jsonExists_null", 200, idNull, check(() -> LoadStep.jsonPath("$.id").exists()), 0.0},
                {"jsonEquals_null", 200, idNull, check(() -> LoadStep.jsonPath("$.id").is(5)), 1.0},
                {"jsonIn_match", 200, id5, check(() -> LoadStep.jsonPath("$.id").in(5, 6)), 0.0},
                {"jsonIn_mismatch", 200, id7, check(() -> LoadStep.jsonPath("$.id").in(5, 6)), 1.0},
                {"jsonIn_null", 200, idNull, check(() -> LoadStep.jsonPath("$.id").in(5, 6)), 1.0},
                // bare path (pre-1.1.0 JDK form) is normalised to "$.id" for both engines
                {"jsonExists_barePath", 200, id5, check(() -> LoadStep.jsonPath("id").exists()), 0.0},
                // HEADER_PRESENT: not reachable through the public builders, so no row
        };
    }

    private static Supplier<CheckCondition> check(Supplier<CheckCondition> s) {
        return s;
    }

    @Test(dataProvider = "checkKinds")
    public void enginesClassifyIdentically(String kind, int status, String body, Supplier<CheckCondition> check,
                                           double expectedErrorRate) {
        String endpoint = kind;
        fixed("/jdk/" + endpoint, status, body);
        fixed("/gatling/" + endpoint, status, body);

        LoadTestMetrics jdk = runOn("jdk", kind, endpoint, check);
        LoadTestMetrics gatling = runOn("gatling", kind, endpoint, check);

        String summary = kind + ": jdk{total=" + jdk.totalRequests() + ", served=" + served("jdk", endpoint)
                + ", errorRate=" + jdk.errorRate() + "} gatling{total=" + gatling.totalRequests()
                + ", served=" + served("gatling", endpoint) + ", errorRate=" + gatling.errorRate() + "}";

        assertTrue(served("jdk", endpoint) > 0, "jdk sent nothing — " + summary);
        assertTrue(served("gatling", endpoint) > 0, "gatling sent nothing — " + summary);
        assertEquals(jdk.totalRequests(), served("jdk", endpoint), "jdk total vs server count — " + summary);
        assertEquals(gatling.totalRequests(), served("gatling", endpoint),
                "gatling total vs server count — " + summary);
        assertEquals(jdk.errorRate(), gatling.errorRate(), "engines disagree — " + summary);
        assertEquals(jdk.errorRate(), expectedErrorRate, "jdk classification — " + summary);
        assertEquals(gatling.errorRate(), expectedErrorRate, "gatling classification — " + summary);
    }

    @DataProvider(name = "extractKinds")
    public Object[][] extractKinds() {
        return new Object[][] {
                // observed with real Gatling 3.13.5: extracting a JSON null succeeds
                {"extract_null", "{\"id\":null}", "$.id", 0.0},
                {"extract_present", "{\"id\":5}", "$.id", 0.0},
                {"extract_missing", "{\"id\":5}", "$.missing", 1.0},
                {"extract_barePath", "{\"id\":5}", "id", 0.0},
        };
    }

    @Test(dataProvider = "extractKinds")
    public void enginesClassifyExtractionsIdentically(String kind, String body, String path,
                                                      double expectedErrorRate) {
        fixed("/jdk/" + kind, 200, body);
        fixed("/gatling/" + kind, 200, body);

        LoadTestMetrics jdk = runExtractOn("jdk", kind, path);
        LoadTestMetrics gatling = runExtractOn("gatling", kind, path);

        String summary = kind + ": jdk{total=" + jdk.totalRequests() + ", served=" + served("jdk", kind)
                + ", errorRate=" + jdk.errorRate() + "} gatling{total=" + gatling.totalRequests()
                + ", served=" + served("gatling", kind) + ", errorRate=" + gatling.errorRate() + "}";

        assertTrue(served("jdk", kind) > 0, "jdk sent nothing — " + summary);
        assertTrue(served("gatling", kind) > 0, "gatling sent nothing — " + summary);
        assertEquals(jdk.totalRequests(), served("jdk", kind), "jdk total vs server count — " + summary);
        assertEquals(gatling.totalRequests(), served("gatling", kind), "gatling total vs server count — " + summary);
        assertEquals(jdk.errorRate(), gatling.errorRate(), "engines disagree — " + summary);
        assertEquals(jdk.errorRate(), expectedErrorRate, "jdk classification — " + summary);
        assertEquals(gatling.errorRate(), expectedErrorRate, "gatling classification — " + summary);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private LoadTestMetrics runExtractOn(String engine, String kind, String path) {
        LoadStep step = LoadScenario.named("Parity_" + kind + "_" + engine)
                .baseUrl(baseUrl)
                .engine(engine)
                .users(2)
                .rampUp(Duration.ofSeconds(1))
                .hold(Duration.ofSeconds(2))
                .step("parity")
                .get("/" + engine + "/" + kind)
                .extract("v", path);
        return LoadTestRunner.run(step.and()).metrics();
    }

    private LoadTestMetrics runOn(String engine, String kind, String endpoint, Supplier<CheckCondition> check) {
        LoadStep step = LoadScenario.named("Parity_" + kind + "_" + engine)
                .baseUrl(baseUrl)
                .engine(engine)
                .users(2)
                .rampUp(Duration.ofSeconds(1))
                .hold(Duration.ofSeconds(2))
                .step("parity")
                .get("/" + engine + "/" + endpoint);
        if (check != null) {
            step.check(check.get());
        }
        return LoadTestRunner.run(step.and()).metrics();
    }

    private void fixed(String path, int status, String body) {
        server.createContext(path, ex -> {
            hits.computeIfAbsent(path, k -> new AtomicInteger()).incrementAndGet();
            ex.getRequestBody().readAllBytes();
            send(ex, status, body);
        });
    }

    private int served(String engine, String endpoint) {
        AtomicInteger c = hits.get("/" + engine + "/" + endpoint);
        return c == null ? 0 : c.get();
    }

    private static void send(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
