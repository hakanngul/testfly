package io.testfly.integration.loadtest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.testfly.loadtest.BaseLoadTest;
import io.testfly.loadtest.LoadScenario;
import io.testfly.loadtest.LoadStep;
import io.testfly.loadtest.LoadTestConfig;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.testng.Assert.*;

/**
 * ADR 0004 decision 1 (Phase 1a): Gatling metrics owned by TestFly ({@code testfly-metrics.json}),
 * checked against a real Gatling run on a local {@link HttpServer}. The oracle is the server's own
 * per-endpoint counters: Gatling 3.14+ no longer writes a machine-readable {@code js/stats.js}, so the
 * cross-check against Gatling's statistics that ran while 3.13.5 was pinned is retired (ADR 0004).
 *
 * <p>No class-level {@code @Test}: on a {@link BaseLoadTest} subclass it would turn the inherited public
 * mixin methods into tests. Methods are parallel-safe: the shared run is memoized under a lock, every
 * scenario name carries a UUID, and every endpoint has its own counter.
 */
public class GatlingOwnedMetricsIntegrationTest extends BaseLoadTest {

    private static final String SIDECAR = "testfly-metrics.json";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String OK_STEP = "ownedOk";
    private static final String KO_STEP = "ownedKo";

    private HttpServer server;
    private ExecutorService serverExecutor;
    private String baseUrl;
    private final Map<String, AtomicInteger> hits = new ConcurrentHashMap<>();

    /** One shared mixed OK/KO run, used by several assertions. */
    private final Object runLock = new Object();
    private SharedRun sharedRun;

    private record SharedRun(LoadTestMetrics metrics, Path runDir, Path simDir, RuntimeException failure) {
    }

    @BeforeClass
    public void startServer() throws IOException {
        if (!GatlingBridge.isAvailable()) {
            throw new SkipException("Gatling is not available on classpath — skipping owned-metrics tests");
        }
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serverExecutor = Executors.newCachedThreadPool();
        server.setExecutor(serverExecutor);
        jitter("/owned-ok");
        jitter("/owned-ko");
        jitter("/mv-ok");
        jitter("/mv-none");
        jitter("/redir-to");
        server.createContext("/redir-from", ex -> {
            hits.computeIfAbsent("/redir-from", k -> new AtomicInteger()).incrementAndGet();
            ex.getRequestBody().readAllBytes();
            ex.getResponseHeaders().set("Location", "/redir-to");
            ex.sendResponseHeaders(302, -1);
            ex.close();
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

    // ── p90 measured, percentiles ordered ────────────────────────────────

    @Test
    public void p90_isMeasured_andPercentilesAreOrdered() {
        LoadTestMetrics m = shared().metrics();

        assertTrue(LoadTestMetrics.isMeasured(m.p90LatencyMs()), "p90 must be measured on Gatling, was NaN");
        assertTrue(LoadTestMetrics.isMeasured(m.p75LatencyMs()), "p75 must be measured, was NaN");
        String all = "p50=" + m.p50LatencyMs() + " p75=" + m.p75LatencyMs() + " p90=" + m.p90LatencyMs()
                + " p95=" + m.p95LatencyMs() + " p99=" + m.p99LatencyMs() + " max=" + m.maxLatencyMs();
        assertTrue(m.p50LatencyMs() <= m.p75LatencyMs(), all);
        assertTrue(m.p75LatencyMs() <= m.p90LatencyMs(), all);
        assertTrue(m.p90LatencyMs() <= m.p95LatencyMs(), all);
        assertTrue(m.p95LatencyMs() <= m.p99LatencyMs(), all);
        assertTrue(m.p99LatencyMs() <= m.maxLatencyMs(), all);
    }

    // ── Gatling's own HTML report is still written ───────────────────────

    @Test
    public void runDir_containsGatlingHtmlReport() {
        SharedRun run = shared();
        assertTrue(Files.isRegularFile(run.simDir().resolve("index.html")),
                "Gatling HTML report missing in " + run.simDir());
    }

    // ── per-step counts vs the local server ──────────────────────────────

    @Test
    public void stepCounts_equalServerCounters() {
        LoadTestMetrics m = shared().metrics();

        assertStepEqualsServer(m, OK_STEP, "/owned-ok");
        assertStepEqualsServer(m, KO_STEP, "/owned-ko");
        assertEquals(m.step(OK_STEP).failedRequests(), 0, OK_STEP + " must be all OK");
        assertEquals(m.step(KO_STEP).failedRequests(), m.step(KO_STEP).totalRequests(),
                KO_STEP + " (status is 201 against 200) must be all KO");
        assertEquals(m.totalRequests(), served("/owned-ok") + served("/owned-ko"), "global total vs server");
    }

    // ── the run writes the versioned sidecar ─────────────────────────────

    @Test
    public void runDir_containsVersionedMetricsSidecar() throws IOException {
        SharedRun run = shared();
        Path sidecar = run.runDir().resolve(SIDECAR);

        assertTrue(Files.isRegularFile(sidecar), "missing " + sidecar + "; run dir holds "
                + listing(run.runDir()));
        JsonNode root = MAPPER.readTree(sidecar.toFile());
        // schema 2 (ADR 0004 decision 4): a single-scenario run maps its scenario to unprefixed request names
        assertEquals(root.path("schemaVersion").asInt(-1), 2, "schemaVersion in " + sidecar);
        JsonNode scenarios = root.path("scenarios");
        assertEquals(scenarios.size(), 1, "one scenario in " + sidecar);
        assertEquals(scenarios.elements().next().toString(), "[\"" + OK_STEP + "\",\"" + KO_STEP + "\"]",
                "single-scenario request names stay unprefixed");
        for (String step : List.of(OK_STEP, KO_STEP)) {
            JsonNode e = root.path("requests").path(step);
            assertTrue(e.isObject(), "sidecar has no entry for step " + step);
            assertEquals(e.path("attempts").asLong(-1), e.path("ok").asLong(0) + e.path("ko").asLong(0),
                    step + ": attempts must equal ok + ko in a complete run");
        }
    }

    // ── a request that cannot be built is KO with no response ───────────

    @Test
    public void missingVariable_isAllKoWithMinusOne_andRunDoesNotThrow() {
        String name = "OwnedMissingVar_" + UUID.randomUUID().toString().replace("-", "");
        LoadTestMetrics m;
        try {
            m = LoadScenario.named(name)
                    .baseUrl(baseUrl)
                    .engine("gatling")
                    .step("mvFine").get("/mv-ok").and()
                    .step("mvMissing").get("/mv-ok?v=${nope}").and()
                    .users(2).rampUp(Duration.ofSeconds(1)).hold(Duration.ofSeconds(2))
                    .run().metrics();
        } catch (RuntimeException e) {
            throw new AssertionError("a step with a missing variable must not crash the run", e);
        }

        LoadTestMetrics.StepMetrics missing = m.step("mvMissing");
        assertNotNull(missing, "step with a missing variable must be reported; steps=" + m.steps().keySet());
        assertTrue(missing.totalRequests() > 0, "mvMissing must have attempts");
        assertEquals(missing.failedRequests(), missing.totalRequests(), "mvMissing must be all KO");
        assertEquals(m.statusCodeCount(-1), (long) missing.totalRequests(),
                "unbuildable requests got no response → status -1; statusCodes=" + m.statusCodes());
        assertStepEqualsServer(m, "mvFine", "/mv-ok");
    }

    /**
     * R-18 (plan item 2): when every step is unbuildable, Gatling 3.16 sends no request and exits with code 1
     * ("no requests sent, reports won't be generated") after the sidecar is complete. TestFly must treat that as a
     * run in which every request is KO with no response (ADR 0004: requests that cannot be built are KO), not as a
     * crash.
     */
    @Test
    public void allStepsUnbuildable_runDoesNotThrow_everyRequestIsKoWithMinusOne() {
        String name = "OwnedNoneSent_" + UUID.randomUUID().toString().replace("-", "");
        LoadTestMetrics m;
        try {
            m = LoadScenario.named(name)
                    .baseUrl(baseUrl)
                    .engine("gatling")
                    .step("noneBuilt").get("/mv-none?v=${nope}").and()
                    .users(2).rampUp(Duration.ofSeconds(1)).hold(Duration.ofSeconds(2))
                    .run().metrics();
        } catch (RuntimeException e) {
            throw new AssertionError("a run in which no request could be built must not fail with Gatling's "
                    + "exit code 1; it is an all-KO run", e);
        }

        assertTrue(m.totalRequests() > 0, "the unbuildable requests must be counted; metrics=" + m);
        assertEquals(m.failedRequests(), m.totalRequests(), "every request must be KO");
        assertEquals(m.successfulRequests(), 0);
        assertEquals(m.statusCodeCount(-1), (long) m.totalRequests(),
                "no request got a response → status -1 for each; statusCodes=" + m.statusCodes());
        assertEquals(m.statusCodes().keySet(), java.util.Set.of(-1), "statusCodes=" + m.statusCodes());
        LoadTestMetrics.StepMetrics step = m.step("noneBuilt");
        assertNotNull(step, "steps=" + m.steps().keySet());
        assertEquals(step.failedRequests(), step.totalRequests(), "the step must be all KO");
        assertEquals(served("/mv-none"), 0, "no request may reach the server");
    }

    // ── a real 302 → 200 redirect ────────────────────────────────────────

    /**
     * Gatling follows the 302 and names the hop {@code "<step> Redirect 1"}: the step entry gets the first
     * (302) response, the hop entry the 200. Hops count in status codes but are neither steps nor latency
     * samples.
     */
    @Test
    public void redirect_countsBothHops_stepIsFirstResponse_noMinusOne() throws IOException {
        String name = "OwnedRedirect_" + UUID.randomUUID().toString().replace("-", "");
        LoadScenario scenario = LoadScenario.named(name)
                .baseUrl(baseUrl)
                .engine("gatling")
                .step("redir").get("/redir-from").and()
                .users(2).rampUp(Duration.ofSeconds(1)).hold(Duration.ofSeconds(2));
        LoadTestMetrics m = scenario.run().metrics();

        LoadTestMetrics.StepMetrics step = m.step("redir");
        assertNotNull(step, "steps=" + m.steps().keySet());
        assertEquals(m.steps().keySet(), java.util.Set.of("redir"), "a redirect hop is not a step");
        assertTrue(step.totalRequests() > 0, "no redirect requests recorded");
        assertEquals(step.failedRequests(), 0, "a followed redirect ending in 200 is OK");
        assertEquals(step.totalRequests(), served("/redir-from"), "step total vs server count of /redir-from");
        assertEquals(served("/redir-to"), served("/redir-from"), "every 302 was followed exactly once");
        assertTrue(LoadTestMetrics.isMeasured(step.p95LatencyMs()), "step p95 must be measured");
        assertFalse(m.statusCodes().containsKey(-1), "every request got a response; statusCodes=" + m.statusCodes());
        assertEquals(m.statusCodeCount(302), (long) served("/redir-from"), "statusCodes=" + m.statusCodes());
        assertEquals(m.statusCodeCount(200), (long) served("/redir-to"), "statusCodes=" + m.statusCodes());
        assertEquals(m.totalRequests(), step.totalRequests(), "the hop is not counted as a request");
        assertFalse(m.partial());

        // what Gatling actually recorded: the hop under "<step> Redirect 1", status only
        JsonNode requests = MAPPER.readTree(newestRunDir(scenario, name).resolve(SIDECAR).toFile()).path("requests");
        assertEquals(requests.path("redir").path("status").path("302").asLong(-1), (long) served("/redir-from"),
                "the step entry holds the first (302) response; sidecar=" + requests);
        JsonNode hop = requests.path("redir Redirect 1");
        assertTrue(hop.isObject() && !hop.has("attempts"), "hop entry 'redir Redirect 1' without attempts; sidecar="
                + requests);
        assertEquals(hop.path("status").path("200").asLong(-1), (long) served("/redir-to"), "sidecar=" + requests);
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private SharedRun shared() {
        synchronized (runLock) {
            if (sharedRun == null) {
                sharedRun = runShared();
            }
        }
        if (sharedRun.failure() != null) {
            throw new AssertionError("shared Gatling run failed: " + sharedRun.failure(), sharedRun.failure());
        }
        return sharedRun;
    }

    private SharedRun runShared() {
        String name = "OwnedMetrics_" + UUID.randomUUID().toString().replace("-", "");
        LoadScenario scenario = LoadScenario.named(name)
                .baseUrl(baseUrl)
                .engine("gatling")
                .step(OK_STEP).get("/owned-ok").and()
                .step(KO_STEP).get("/owned-ko").check(LoadStep.status().is(201)).and()
                .users(3).rampUp(Duration.ofSeconds(1)).hold(Duration.ofSeconds(4));
        try {
            LoadTestMetrics m = scenario.run().metrics();
            Path runDir = newestRunDir(scenario, name);
            Path simDir = simDir(runDir);
            return new SharedRun(m, runDir, simDir, null);
        } catch (RuntimeException | IOException e) {
            return new SharedRun(null, null, null,
                    e instanceof RuntimeException re ? re : new IllegalStateException(e));
        }
    }

    /** {@code <resultsDir>/<scenario-slug>-<millis>}, the newest one for this (UUID-unique) scenario. */
    private Path newestRunDir(LoadScenario scenario, String name) throws IOException {
        Path resultsDir = Path.of(LoadTestConfig.resolveFor(scenario, getClass(), null).getResultsDir())
                .toAbsolutePath();
        String prefix = name.replaceAll("[^a-zA-Z0-9_-]", "_") + "-";
        try (Stream<Path> dirs = Files.list(resultsDir)) {
            return dirs.filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().startsWith(prefix))
                    .filter(p -> p.getFileName().toString().substring(prefix.length()).matches("\\d+"))
                    .max(Comparator.comparingLong(p -> Long.parseLong(
                            p.getFileName().toString().substring(prefix.length()))))
                    .orElseThrow(() -> new IllegalStateException("no run dir " + prefix + "* in " + resultsDir));
        }
    }

    /** The Gatling simulation dir inside a run dir: the subdirectory holding {@code index.html}. */
    private static Path simDir(Path runDir) throws IOException {
        try (Stream<Path> dirs = Files.list(runDir)) {
            return dirs.filter(p -> Files.isRegularFile(p.resolve("index.html")))
                    .max(Comparator.comparingLong(p -> p.toFile().lastModified()))
                    .orElseThrow(() -> new IllegalStateException("no <sim-dir>/index.html in " + runDir));
        }
    }

    private void assertStepEqualsServer(LoadTestMetrics m, String step, String path) {
        LoadTestMetrics.StepMetrics s = m.step(step);
        assertNotNull(s, "step " + step + " missing; steps=" + m.steps().keySet());
        assertTrue(s.totalRequests() > 0, step + " recorded no requests");
        assertEquals(s.totalRequests(), served(path), step + " total vs server-observed count of " + path);
    }

    private static String listing(Path dir) throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            return s.map(p -> p.getFileName().toString()).sorted().toList().toString();
        }
    }

    private void jitter(String path) {
        server.createContext(path, ex -> {
            hits.computeIfAbsent(path, k -> new AtomicInteger()).incrementAndGet();
            ex.getRequestBody().readAllBytes();
            try {
                Thread.sleep(ThreadLocalRandom.current().nextLong(5, 61));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            send(ex, 200, "{}");
        });
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
