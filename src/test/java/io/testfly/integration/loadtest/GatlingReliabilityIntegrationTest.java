package io.testfly.integration.loadtest;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.testfly.loadtest.BaseLoadTest;
import io.testfly.loadtest.LoadScenario;
import io.testfly.loadtest.LoadStep;
import io.testfly.loadtest.LoadTestMetrics;
import io.testfly.loadtest.LoadTestRunner;
import io.testfly.loadtest.internal.GatlingBridge;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.*;

/**
 * Regression tests for the Gatling engine's metric integrity, check mapping and
 * process lifecycle (audit docs/audits/gatling-2026-10, BUG-01/03/04/05/06/08).
 *
 * <p>Every assertion compares TestFly's metrics with what the local
 * {@link HttpServer} actually observed (per-endpoint counters).
 *
 * <p>No class-level {@code @Test}: on a {@link BaseLoadTest} subclass it would turn the
 * inherited public mixin methods ({@code step(..)}, {@code stepWithScreenshot(..)}) into
 * tests. Methods are parallel-safe instead: each uses its own endpoint, counter and
 * scenario name, and Gatling runs are serialized by the engine itself.
 */
public class GatlingReliabilityIntegrationTest extends BaseLoadTest {

    private HttpServer server;
    private ExecutorService serverExecutor;
    private String baseUrl;
    private final Map<String, AtomicInteger> hits = new ConcurrentHashMap<>();
    private final java.util.Queue<String> formBodies = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private final java.util.Queue<String> formContentTypes = new java.util.concurrent.ConcurrentLinkedQueue<>();

    @BeforeClass
    public void startServer() throws IOException {
        if (!GatlingBridge.isAvailable()) {
            throw new SkipException("Gatling is not available on classpath — skipping Gatling reliability tests");
        }
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serverExecutor = Executors.newCachedThreadPool();
        server.setExecutor(serverExecutor);

        fixed("/created", 201, "{\"id\":\"abc\"}");
        fixed("/ok-status-mismatch", 200, "{\"status\":\"ok\"}");
        fixed("/created-in", 201, "{\"status\":\"ok\"}");
        fixed("/bizfail", 200, "{\"success\":false,\"error\":\"insufficient funds\"}");
        fixed("/id5", 200, "{\"id\":5}");
        fixed("/id7", 200, "{\"id\":7}");
        fixed("/cancel", 200, "{}");
        fixed("/ok", 200, "{}");
        fixed("/iso-a", 200, "{}");
        fixed("/iso-b", 200, "{}");
        server.createContext("/form", ex -> {
            count("/form");
            formContentTypes.add(String.valueOf(ex.getRequestHeaders().getFirst("Content-Type")));
            formBodies.add(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            send(ex, 200, "{}");
        });
        server.createContext("/jitter", ex -> {
            count("/jitter");
            try {
                Thread.sleep(ThreadLocalRandom.current().nextLong(5, 61));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
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

    // ── BUG-01: status code fidelity ─────────────────────────────────────

    @Test
    public void statusCodes_reportRealStatus_201() {
        LoadTestMetrics m = scenario("Rel201").step("create").post("/created").and().run().metrics();

        assertTrue(m.totalRequests() > 0, "no requests recorded");
        assertEquals(m.totalRequests(), served("/created"), "TestFly total vs server-observed count");
        assertEquals(m.statusCodes(), Map.of(201, (long) m.totalRequests()),
                "server returned only 201; statusCodes must be {201=total}, was " + m.statusCodes());
    }

    @Test
    public void statusCodes_connectionRefused_hasNoHttpStatus() {
        LoadTestMetrics m = LoadScenario.named("RelRefused")
                .baseUrl("http://127.0.0.1:1")
                .engine("gatling")
                .step("refused").get("/ok").and()
                .users(1).rampUp(Duration.ofSeconds(1)).hold(Duration.ofSeconds(2))
                .run().metrics();

        assertTrue(m.totalRequests() > 0, "no requests recorded");
        assertEquals(m.failedRequests(), m.totalRequests(), "every refused request must be KO");
        assertTrue(m.statusCodes().keySet().stream().noneMatch(c -> c >= 100 && c <= 599),
                "no HTTP response was ever received, but statusCodes=" + m.statusCodes());
        assertTrue(m.statusCodesMeasured(), "status codes must be measured (sidecar written), was " + m.statusCodes());
        assertEquals(m.statusCodes(), Map.of(-1, (long) m.totalRequests()),
                "every request got no response: statusCodes must be {-1=total}, was " + m.statusCodes());
    }

    @Test
    public void failingStatusCheck_isKo_andKeepsRealStatus() {
        LoadTestMetrics m = scenario("RelIs201")
                .step("mismatch").get("/ok-status-mismatch").check(LoadStep.status().is(201)).and()
                .run().metrics();

        assertTrue(m.totalRequests() > 0, "no requests recorded");
        assertEquals(m.totalRequests(), served("/ok-status-mismatch"), "TestFly total vs server-observed count");
        assertEquals(m.errorRate(), 1.0, "status().is(201) against 200 must be KO");
        assertEquals(m.statusCodes(), Map.of(200, (long) m.totalRequests()),
                "server returned only 200; failed check must not rewrite the status, was " + m.statusCodes());
    }

    // ── BUG-03: unmapped checks ──────────────────────────────────────────

    @Test
    public void statusIn_notMatching_isKo() {
        LoadTestMetrics m = scenario("RelIn")
                .step("in").get("/created-in").check(LoadStep.status().in(200, 204)).and()
                .run().metrics();

        assertTrue(m.totalRequests() > 0, "no requests recorded");
        assertEquals(m.totalRequests(), served("/created-in"), "TestFly total vs server-observed count");
        assertEquals(m.errorRate(), 1.0, "status().in(200,204) against 201 must be KO");
    }

    @Test
    public void bodyRegex_notMatching_isKo() {
        LoadTestMetrics m = scenario("RelRegex")
                .step("biz").get("/bizfail")
                .check(LoadStep.jsonPath("$.success").matches("\"success\"\\s*:\\s*true")).and()
                .run().metrics();

        assertTrue(m.totalRequests() > 0, "no requests recorded");
        assertEquals(m.totalRequests(), served("/bizfail"), "TestFly total vs server-observed count");
        assertEquals(m.errorRate(), 1.0, "body regex must fail against {\"success\":false}");
    }

    // ── BUG-04: jsonPath(..).is(..) must compare the JSON value ──────────

    @Test
    public void jsonPathIs_matchingValue_isOk() {
        LoadTestMetrics m = scenario("RelJsonEq5")
                .step("id5").get("/id5").check(LoadStep.jsonPath("$.id").is(5)).and()
                .run().metrics();

        assertTrue(m.totalRequests() > 0, "no requests recorded");
        assertEquals(m.totalRequests(), served("/id5"), "TestFly total vs server-observed count");
        assertEquals(m.errorRate(), 0.0, "jsonPath(\"$.id\").is(5) against {\"id\":5} must be OK");
    }

    @Test
    public void jsonPathIs_otherValue_isKo() {
        LoadTestMetrics m = scenario("RelJsonEq7")
                .step("id7").get("/id7").check(LoadStep.jsonPath("$.id").is(5)).and()
                .run().metrics();

        assertTrue(m.totalRequests() > 0, "no requests recorded");
        assertEquals(m.totalRequests(), served("/id7"), "TestFly total vs server-observed count");
        assertEquals(m.errorRate(), 1.0, "jsonPath(\"$.id\").is(5) against {\"id\":7} must be KO");
    }

    // ── BUG-09: formParam is sent as an urlencoded body ──────────────────

    @Test
    public void formParam_sendsUrlEncodedBody() {
        LoadTestMetrics m = scenario("RelForm").step("form").post("/form").formParam("user", "alice").and()
                .run().metrics();

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

    // ── BUG-06: p90 must not be fabricated; since ADR 0004 it is measured ─

    @Test
    public void p90_isMeasured_notInterpolatedFromP50AndP95() {
        LoadTestMetrics m = LoadScenario.named("RelP90")
                .baseUrl(baseUrl)
                .engine("gatling")
                .step("jitter").get("/jitter").and()
                .users(3).rampUp(Duration.ofSeconds(1)).hold(Duration.ofSeconds(3))
                .run().metrics();

        assertTrue(m.totalRequests() > 0, "no requests recorded");
        assertEquals(m.totalRequests(), served("/jitter"), "TestFly total vs server-observed count");
        assertNotEquals(m.p50LatencyMs(), m.p95LatencyMs(),
                "precondition: jittered latencies must give p50 != p95 (p50=" + m.p50LatencyMs() + ")");
        // TestFly's histogram measures p90 (ADR 0004); it is no longer "not measured" (NaN). A measured
        // p90 may coincide with p50+(p95-p50)*0.8 by chance, so ordering is checked instead of inequality.
        assertTrue(LoadTestMetrics.isMeasured(m.p90LatencyMs()), "p90 must be measured on Gatling, was NaN");
        assertTrue(m.p50LatencyMs() <= m.p90LatencyMs() && m.p90LatencyMs() <= m.p95LatencyMs(),
                "p50=" + m.p50LatencyMs() + " p90=" + m.p90LatencyMs() + " p95=" + m.p95LatencyMs());
    }

    // ── BUG-05: cancellation must terminate the forked JVM ───────────────

    @Test
    public void interrupt_terminatesGatlingChildJvm() throws Exception {
        String scenarioName = "RelCancel_" + UUID.randomUUID().toString().replace("-", "");
        AtomicReference<Throwable> outcome = new AtomicReference<>();
        Thread runner = new Thread(() -> {
            try {
                LoadScenario.named(scenarioName)
                        .baseUrl(baseUrl)
                        .engine("gatling")
                        .step("cancel").get("/cancel").and()
                        .users(2).rampUp(Duration.ofSeconds(1)).hold(Duration.ofSeconds(30))
                        .run();
            } catch (Throwable t) {
                outcome.set(t);
            }
        }, "gatling-cancel-runner");

        List<ProcessHandle> children = new ArrayList<>();
        try {
            runner.start();
            // generous: under parallel methods this run may queue behind other Gatling runs
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(180);
            while (System.nanoTime() < deadline) {
                children.clear();
                children.addAll(gatlingChildren(scenarioName));
                if (!children.isEmpty() && served("/cancel") > 0) {
                    break;
                }
                Thread.sleep(200);
            }
            assertFalse(children.isEmpty(), "Gatling child JVM never observed within 180s");
            assertTrue(served("/cancel") > 0, "Gatling child never produced traffic within 180s");

            runner.interrupt();
            runner.join(TimeUnit.SECONDS.toMillis(15));
            assertFalse(runner.isAlive(), "runner thread did not finish within 15s of interrupt");

            long stopDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            while (System.nanoTime() < stopDeadline && children.stream().anyMatch(ProcessHandle::isAlive)) {
                Thread.sleep(200);
            }
            List<Long> alive = children.stream().filter(ProcessHandle::isAlive).map(ProcessHandle::pid).toList();
            assertTrue(alive.isEmpty(), "Gatling child JVM(s) still alive 15s after interrupt: " + alive
                    + " (runner outcome: " + outcome.get() + ")");

            int before = served("/cancel");
            Thread.sleep(2000);
            assertEquals(served("/cancel"), before, "server traffic continued after cancellation");
        } finally {
            runner.interrupt();
            for (ProcessHandle child : children) {
                child.descendants().forEach(ProcessHandle::destroyForcibly);
                child.destroyForcibly();
            }
            for (ProcessHandle leftover : gatlingChildren(scenarioName)) {
                leftover.destroyForcibly();
            }
            runner.join(TimeUnit.SECONDS.toMillis(15));
        }
    }

    // ── BUG-08: child exit code must surface ─────────────────────────────

    @Test
    public void simulationBuildFailure_reportsExitCode() {
        Throwable thrown = null;
        try {
            scenario("RelBadEl").step("bad").get("/ok?x=${unclosed").and()
                    .users(1).hold(Duration.ofSeconds(1))
                    .run();
        } catch (Throwable t) {
            thrown = t;
        }
        assertNotNull(thrown, "a malformed Gatling EL must make run() fail");
        String msg = String.valueOf(thrown.getMessage());
        assertTrue(msg.toLowerCase(Locale.ROOT).contains("exit code"),
                "exception message must include the Gatling child exit code, was: " + msg);
        // the root cause must survive Gatling's long stack traces (3.16 pushed it out of the 20-line tail)
        assertTrue(msg.contains("unclosed") || msg.contains("ElParserException"),
                "exception message must include the root cause (the EL parse error), was: " + msg);
    }

    // ── Concurrent runs stay isolated ────────────────────────────────────

    @Test
    public void concurrentRuns_areIsolated() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<LoadTestMetrics> fa = pool.submit(() -> LoadTestRunner.run(
                    scenario("RelIsoA").step("isoStepA").get("/iso-a").and()).metrics());
            Future<LoadTestMetrics> fb = pool.submit(() -> LoadTestRunner.run(
                    scenario("RelIsoB").step("isoStepB").get("/iso-b").and()).metrics());
            LoadTestMetrics a = fa.get(120, TimeUnit.SECONDS);
            LoadTestMetrics b = fb.get(120, TimeUnit.SECONDS);

            assertEquals(a.steps().keySet(), Set.of("isoStepA"), "run A leaked steps");
            assertEquals(b.steps().keySet(), Set.of("isoStepB"), "run B leaked steps");
            assertTrue(a.totalRequests() > 0 && b.totalRequests() > 0, "both runs must record requests");
            assertEquals(a.totalRequests(), served("/iso-a"), "run A total vs server-observed count");
            assertEquals(b.totalRequests(), served("/iso-b"), "run B total vs server-observed count");
        } finally {
            pool.shutdownNow();
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private LoadScenario scenario(String name) {
        return LoadScenario.named(name)
                .baseUrl(baseUrl)
                .engine("gatling")
                .users(2)
                .rampUp(Duration.ofSeconds(1))
                .hold(Duration.ofSeconds(2));
    }

    /**
     * Live descendants that are the forked Gatling JVM for {@code scenarioName} (passed as {@code -rd}).
     * Reads {@code /proc/<pid>/cmdline} because {@link ProcessHandle.Info#commandLine()} is truncated
     * to 4 KiB on Linux and the long test classpath hides the trailing Gatling arguments.
     */
    private static List<ProcessHandle> gatlingChildren(String scenarioName) {
        return ProcessHandle.current().descendants()
                .filter(ProcessHandle::isAlive)
                .filter(p -> {
                    String cl = fullCommandLine(p);
                    return cl.contains("io.gatling.app.Gatling") && cl.contains(scenarioName);
                })
                .toList();
    }

    private static String fullCommandLine(ProcessHandle p) {
        Path proc = Path.of("/proc", Long.toString(p.pid()), "cmdline");
        try {
            if (Files.isReadable(proc)) {
                return new String(Files.readAllBytes(proc), StandardCharsets.UTF_8).replace('\0', ' ');
            }
        } catch (IOException ignored) {
            // process exited or /proc unavailable; fall back below
        }
        return p.info().commandLine().orElse("");
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
