package io.testfly.unit.loadtest;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.testfly.loadtest.LoadScenario;
import io.testfly.loadtest.LoadStep;
import io.testfly.loadtest.LoadTestMetrics;
import io.testfly.loadtest.LoadTestRunner;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.*;

/**
 * Regression tests for JDK load-engine check semantics and request building
 * (audit docs/audits/gatling-2026-10, BUG-02/04/09/14 and Gatling parity).
 *
 * <p>Every assertion compares TestFly's metrics with what the local
 * {@link HttpServer} actually observed (per-endpoint counters).
 */
@Test(singleThreaded = true)
public class JdkLoadEngineReliabilityTest {

    private HttpServer server;
    private ExecutorService serverExecutor;
    private String baseUrl;
    private final Map<String, AtomicInteger> hits = new ConcurrentHashMap<>();
    private final Queue<String> formBodies = new ConcurrentLinkedQueue<>();
    private final Queue<String> formContentTypes = new ConcurrentLinkedQueue<>();
    private final Queue<String> seenQueries = new ConcurrentLinkedQueue<>();

    @BeforeClass
    public void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serverExecutor = Executors.newCachedThreadPool();
        server.setExecutor(serverExecutor);

        fixed("/ok-status-mismatch", 200, "{\"status\":\"ok\"}");
        fixed("/error500", 500, "{\"error\":\"internal\"}");
        fixed("/created-in", 201, "{\"status\":\"ok\"}");
        fixed("/bizfail", 200, "{\"success\":false,\"error\":\"insufficient funds\"}");
        fixed("/id5", 200, "{\"id\":5}");
        fixed("/id7", 200, "{\"id\":7}");
        fixed("/no-token", 200, "{\"other\":\"x\"}");
        fixed("/", 200, "{}"); // catch-all so a mis-built path is still counted
        server.createContext("/form", ex -> {
            count("/form");
            formContentTypes.add(String.valueOf(ex.getRequestHeaders().getFirst("Content-Type")));
            formBodies.add(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            send(ex, 200, "{}");
        });
        server.createContext("/search", ex -> {
            count("/search");
            seenQueries.add(String.valueOf(ex.getRequestURI().getQuery()));
            send(ex, 200, "{}");
        });
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

    // ── BUG-02: a failing check must be counted as KO ────────────────────

    @Test
    public void failingStatusCheck_countsEveryRequestAsKo() {
        LoadTestMetrics m = run(scenario("JdkIs201")
                .step("mismatch").get("/ok-status-mismatch").check(LoadStep.status().is(201)).and());

        int served = served("/ok-status-mismatch");
        assertTrue(served > 0, "server received no requests");
        assertEquals(m.totalRequests(), served, "TestFly total vs server-observed count");
        assertEquals(m.failedRequests(), m.totalRequests(), "every request must be KO");
        assertEquals(m.errorRate(), 1.0);
    }

    @Test
    public void noChecks_500_isKoLikeGatlingDefaultCheck() {
        LoadTestMetrics m = run(scenario("JdkDefault500").step("err").get("/error500").and());

        assertTrue(served("/error500") > 0, "server received no requests");
        assertEquals(m.totalRequests(), served("/error500"), "TestFly total vs server-observed count");
        assertEquals(m.errorRate(), 1.0, "default status check: only 2xx (200..299) and 304 are OK");
    }

    @Test
    public void statusIn_notMatching_isKo() {
        LoadTestMetrics m = run(scenario("JdkIn")
                .step("in").get("/created-in").check(LoadStep.status().in(200, 204)).and());

        assertTrue(served("/created-in") > 0, "server received no requests");
        assertEquals(m.totalRequests(), served("/created-in"), "TestFly total vs server-observed count");
        assertEquals(m.errorRate(), 1.0, "status().in(200,204) against 201 must be KO");
    }

    @Test
    public void bodyRegex_notMatching_isKo() {
        LoadTestMetrics m = run(scenario("JdkRegex")
                .step("biz").get("/bizfail")
                .check(LoadStep.jsonPath("$.success").matches("\"success\"\\s*:\\s*true")).and());

        assertTrue(served("/bizfail") > 0, "server received no requests");
        assertEquals(m.totalRequests(), served("/bizfail"), "TestFly total vs server-observed count");
        assertEquals(m.errorRate(), 1.0, "body regex must fail against {\"success\":false}");
    }

    // ── BUG-04: jsonPath(..).is(..) compares the JSON value ──────────────

    @Test
    public void jsonPathIs_matchingValue_isOk() {
        LoadTestMetrics m = run(scenario("JdkJsonEq5")
                .step("id5").get("/id5").check(LoadStep.jsonPath("$.id").is(5)).and());

        assertTrue(served("/id5") > 0, "server received no requests");
        assertEquals(m.totalRequests(), served("/id5"), "TestFly total vs server-observed count");
        assertEquals(m.errorRate(), 0.0, "jsonPath(\"$.id\").is(5) against {\"id\":5} must be OK");
    }

    @Test
    public void jsonPathIs_otherValue_isKo() {
        LoadTestMetrics m = run(scenario("JdkJsonEq7")
                .step("id7").get("/id7").check(LoadStep.jsonPath("$.id").is(5)).and());

        assertTrue(served("/id7") > 0, "server received no requests");
        assertEquals(m.totalRequests(), served("/id7"), "TestFly total vs server-observed count");
        assertEquals(m.errorRate(), 1.0, "jsonPath(\"$.id\").is(5) against {\"id\":7} must be KO");
    }

    // ── extract() with a missing path fails the request (Gatling parity) ─

    @Test
    public void extract_missingPath_isKo() {
        LoadTestMetrics m = run(scenario("JdkExtractMissing")
                .step("extract").get("/no-token").extract("token", "$.missing").and());

        assertTrue(served("/no-token") > 0, "server received no requests");
        assertEquals(m.totalRequests(), served("/no-token"), "TestFly total vs server-observed count");
        LoadTestMetrics.StepMetrics step = m.step("extract");
        assertNotNull(step, "step metrics missing");
        assertEquals(step.failedRequests(), step.totalRequests(), "extraction of a missing path must be KO");
        assertEquals(m.errorRate(), 1.0);
    }

    // ── BUG-09: formParam is sent as urlencoded body ─────────────────────

    @Test
    public void formParam_sendsUrlEncodedBody() {
        LoadTestMetrics m = run(scenario("JdkForm")
                .step("form").post("/form").formParam("user", "alice").and());

        int served = served("/form");
        assertTrue(served > 0, "server received no requests");
        assertEquals(m.totalRequests(), served, "TestFly total vs server-observed count");
        assertEquals(m.errorRate(), 0.0);
        List<String> bodies = List.copyOf(formBodies);
        List<String> types = List.copyOf(formContentTypes);
        assertEquals(bodies.size(), served);
        assertTrue(bodies.stream().allMatch("user=alice"::equals), "form bodies seen by server: " + bodies);
        assertTrue(types.stream().allMatch(t -> t.startsWith("application/x-www-form-urlencoded")),
                "Content-Type seen by server: " + types);
    }

    // ── BUG-14: query param values are URL-encoded ───────────────────────

    @Test
    public void queryParam_withSpace_isEncodedAndReachesServer() {
        LoadTestMetrics m = run(scenario("JdkQuerySpace")
                .step("search").get("/search").queryParam("q", "a b").and());

        int served = served("/search");
        assertTrue(served > 0, "request with query value 'a b' never reached the server (total="
                + m.totalRequests() + ", ko=" + m.failedRequests() + ")");
        assertEquals(m.totalRequests(), served, "TestFly total vs server-observed count");
        assertEquals(m.errorRate(), 0.0);
        List<String> queries = List.copyOf(seenQueries);
        assertTrue(queries.stream().allMatch("q=a b"::equals), "decoded queries seen by server: " + queries);
    }

    // ── Programming/config errors abort the run instead of a silent KO ───

    @Test
    public void malformedPath_abortsRun() {
        int before = served("/");
        RuntimeException thrown = null;
        LoadTestMetrics m = null;
        try {
            m = run(scenario("JdkBadUri").step("bad").get("/a b").and());
        } catch (RuntimeException e) {
            thrown = e;
        }
        assertNotNull(thrown, "a malformed URI must make run() throw, but it returned metrics: "
                + (m == null ? null : "total=" + m.totalRequests() + " ko=" + m.failedRequests()));
        assertEquals(served("/"), before, "no request may reach the server for a malformed URI");
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private LoadScenario scenario(String name) {
        return LoadScenario.named(name)
                .baseUrl(baseUrl)
                .engine("jdk")
                .users(2)
                .rampUp(Duration.ofSeconds(0))
                .hold(Duration.ofSeconds(1))
                .cooldown(Duration.ofSeconds(0));
    }

    private static LoadTestMetrics run(LoadScenario scenario) {
        return LoadTestRunner.run(scenario).metrics();
    }

    private void fixed(String path, int status, String body) {
        server.createContext(path, ex -> {
            count(path);
            ex.getRequestBody().readAllBytes();
            send(ex, status, body);
        });
    }

    private void count(String path) {
        hits.computeIfAbsent(path, k -> new AtomicInteger()).incrementAndGet();
    }

    private int served(String path) {
        AtomicInteger c = hits.get(path);
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
