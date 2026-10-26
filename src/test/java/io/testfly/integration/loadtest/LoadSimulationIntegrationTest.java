package io.testfly.integration.loadtest;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.testfly.loadtest.BaseLoadTest;
import io.testfly.loadtest.LoadScenario;
import io.testfly.loadtest.LoadTestMetrics;
import io.testfly.loadtest.LoadTestRunner;
import io.testfly.loadtest.Workload;
import io.testfly.loadtest.internal.GatlingBridge;
import io.testfly.unit.loadtest.SimulationApi;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static io.testfly.unit.loadtest.WorkloadApi.atOnce;
import static io.testfly.unit.loadtest.WorkloadApi.closed;
import static io.testfly.unit.loadtest.WorkloadApi.constantConcurrent;
import static io.testfly.unit.loadtest.WorkloadApi.constantRate;
import static io.testfly.unit.loadtest.WorkloadApi.open;
import static org.testng.Assert.*;

/**
 * Multi-scenario simulations (ADR 0004 decision 4, Phase 2) against a real Gatling run on local
 * {@link HttpServer}s bound to 127.0.0.1. Every endpoint has its own request counter; the server, not
 * TestFly, is the witness of how many requests each scenario sent.
 *
 * <p>Written before the API existed (red first); every call to {@code LoadSimulation} /
 * {@code LoadSimulationResult} goes through the {@link SimulationApi} helper.
 *
 * <p>No class-level {@code @Test}: on a {@link BaseLoadTest} subclass it would turn the inherited public mixin
 * methods into tests. Methods are parallel-safe: each uses its own endpoints and simulation name, and Gatling
 * runs are serialized by the engine itself. Gatling-dependent tests skip without Gatling; the JDK rejection
 * runs either way.
 */
public class LoadSimulationIntegrationTest extends BaseLoadTest {

    private static final long BROWSE_MS = 100;

    private HttpServer server;
    private HttpServer secondary;
    private ExecutorService serverExecutor;
    private String baseUrl;
    private String secondaryUrl;
    private final Map<String, AtomicInteger> hits = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> secondaryHits = new ConcurrentHashMap<>();

    @BeforeClass
    public void startServers() throws IOException {
        serverExecutor = Executors.newCachedThreadPool();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(serverExecutor);
        endpoint(server, hits, "/sim-browse", 200, BROWSE_MS);
        endpoint(server, hits, "/sim-buy", 201, 0);
        endpoint(server, hits, "/sim-home-a", 200, 0);
        endpoint(server, hits, "/sim-home-b", 200, 0);
        endpoint(server, hits, "/sim-legacy", 200, 0);
        endpoint(server, hits, "/sim-workload", 200, 0);
        endpoint(server, hits, "/sim-base", 200, 0);
        endpoint(server, hits, "/sim-dup", 200, 0);
        endpoint(server, hits, "/sim-jdk", 200, 0);
        endpoint(server, hits, "/sim-empty", 200, 0);
        endpoint(server, hits, "/sim-conflict", 200, 0);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

        secondary = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        secondary.setExecutor(serverExecutor);
        endpoint(secondary, secondaryHits, "/sim-base", 200, 0);
        secondary.start();
        secondaryUrl = "http://127.0.0.1:" + secondary.getAddress().getPort();
    }

    @AfterClass(alwaysRun = true)
    public void stopServers() {
        if (server != null) {
            server.stop(0);
        }
        if (secondary != null) {
            secondary.stop(0);
        }
        if (serverExecutor != null) {
            serverExecutor.shutdownNow();
        }
    }

    // ── closed Browse + open Buy in one simulation ───────────────────────

    @Test
    public void mixedModels_perScenarioMetricsMatchServerCounters() {
        requireGatling();
        LoadScenario browse = LoadScenario.named("Browse")
                .baseUrl(baseUrl)
                .step("browse").get("/sim-browse").and()
                .workload((Workload) closed(constantConcurrent(3, Duration.ofSeconds(3))));
        LoadScenario buy = LoadScenario.named("Buy")
                .baseUrl(baseUrl)
                .step("buy").get("/sim-buy").and()
                .workload((Workload) open(constantRate(5, Duration.ofSeconds(3))));
        // through the LoadTestSupport mixin (BaseLoadTest implements it)
        Object sim = SimulationApi.loadSimulation(this, unique("SimMixed"));
        SimulationApi.scenario(sim, browse);
        SimulationApi.scenario(sim, buy);
        SimulationApi.engine(sim, "gatling");

        Object result = SimulationApi.run(sim);

        int browseHits = hits.get("/sim-browse").get();
        int buyHits = hits.get("/sim-buy").get();
        assertTrue(browseHits > 0 && buyHits > 0, "both populations must send requests: browse=" + browseHits
                + " buy=" + buyHits);
        assertEquals(SimulationApi.scenarioNames(result), List.of("Browse", "Buy"), "insertion order");

        LoadTestMetrics b = SimulationApi.scenarioMetrics(result, "Browse");
        LoadTestMetrics y = SimulationApi.scenarioMetrics(result, "Buy");
        LoadTestMetrics g = SimulationApi.globalMetrics(result);
        assertEquals(b.totalRequests(), browseHits, "Browse total vs /sim-browse counter; " + b);
        assertEquals(y.totalRequests(), buyHits, "Buy total vs /sim-buy counter; " + y);
        assertEquals(g.totalRequests(), browseHits + buyHits, "global total = sum of the server counters; " + g);

        assertEquals(y.statusCodes(), Map.of(201, (long) buyHits), "Buy sees only its own 201s");
        assertEquals(b.statusCodes(), Map.of(200, (long) browseHits), "Browse sees only its own 200s");
        assertEquals(g.statusCodes(), Map.of(200, (long) browseHits, 201, (long) buyHits), "global status codes");

        assertTrue(LoadTestMetrics.isMeasured(b.p95LatencyMs()), "Browse p95 must be measured: " + b);
        assertTrue(LoadTestMetrics.isMeasured(y.p95LatencyMs()), "Buy p95 must be measured: " + y);
        assertTrue(b.p95LatencyMs() >= BROWSE_MS - 5, "Browse p95 reflects its own " + BROWSE_MS
                + " ms endpoint, was " + b.p95LatencyMs());
        assertEquals(b.steps().keySet(), Set.of("browse"), "step metrics keyed by the unprefixed step name");
        assertEquals(y.steps().keySet(), Set.of("buy"), "step metrics keyed by the unprefixed step name");

        // assertion handles wrap the same metrics
        assertEquals(SimulationApi.global(result).metrics().totalRequests(), g.totalRequests());
        assertEquals(SimulationApi.scenarioAssert(result, "Buy").metrics().totalRequests(), buyHits);
        SimulationApi.scenarioAssert(result, "Buy").assertStatus(201);
        SimulationApi.scenarioAssert(result, "Browse").assertStatus(200);
        expectThrows(IllegalArgumentException.class, () -> SimulationApi.scenarioAssert(result, "Nope"));

        LoadTestMetrics last = LoadTestRunner.lastMetrics();
        assertNotNull(last, "lastMetrics() must be set after runSimulation");
        assertEquals(last.totalRequests(), browseHits + buyHits, "lastMetrics() returns the global metrics");
    }

    // ── the same step name in two scenarios ──────────────────────────────

    @Test
    public void sameStepName_inTwoScenarios_countsAreNotMixed() throws IOException {
        requireGatling();
        LoadScenario a = LoadScenario.named("Alpha")
                .step("home").get("/sim-home-a").and()
                .workload((Workload) open(atOnce(3)));
        LoadScenario b = LoadScenario.named("Beta")
                .step("home").get("/sim-home-b").and()
                .workload((Workload) open(atOnce(5)));
        Object sim = SimulationApi.named(unique("SimSameStep"));
        SimulationApi.baseUrl(sim, baseUrl);
        SimulationApi.engine(sim, "gatling");
        SimulationApi.scenario(sim, a);
        SimulationApi.scenario(sim, b);

        Object result = SimulationApi.run(sim);

        assertEquals(hits.get("/sim-home-a").get(), 3, "atOnce(3) on Alpha");
        assertEquals(hits.get("/sim-home-b").get(), 5, "atOnce(5) on Beta");
        LoadTestMetrics ma = SimulationApi.scenarioMetrics(result, "Alpha");
        LoadTestMetrics mb = SimulationApi.scenarioMetrics(result, "Beta");
        assertEquals(ma.totalRequests(), 3, "Alpha must not count Beta's 'home'; " + ma);
        assertEquals(mb.totalRequests(), 5, "Beta must not count Alpha's 'home'; " + mb);
        assertNotNull(ma.step("home"), "Alpha step 'home' (unprefixed); steps=" + ma.steps().keySet());
        assertNotNull(mb.step("home"), "Beta step 'home' (unprefixed); steps=" + mb.steps().keySet());
        assertEquals(ma.step("home").totalRequests(), 3);
        assertEquals(mb.step("home").totalRequests(), 5);
        assertEquals(SimulationApi.globalMetrics(result).totalRequests(), 8, "global = 3 + 5");

        // per-run artefacts of a multi-scenario run: the global file plus one file per scenario
        String simName = SimulationApi.name(sim);
        java.nio.file.Path runDir = runDir(a, simName);
        io.testfly.loadtest.LoadBaseline global = io.testfly.loadtest.LoadBaseline.load(
                runDir.resolve(io.testfly.loadtest.LoadBaseline.RESULT_FILE));
        assertEquals(global.scenarioName(), simName + " (global)");
        assertEquals(io.testfly.loadtest.LoadBaseline.load(runDir.resolve("testfly-loadtest-result-Alpha.json"))
                .scenarioName(), "Alpha");
        assertEquals(io.testfly.loadtest.LoadBaseline.load(runDir.resolve("testfly-loadtest-result-Beta.json"))
                .scenarioName(), "Beta");
    }

    /** The simulation's run dir {@code <resultsDir>/<simulation-slug>-<millis>} (UUID-unique name). */
    private java.nio.file.Path runDir(LoadScenario anyScenario, String simulationName) throws IOException {
        java.nio.file.Path resultsDir = java.nio.file.Path.of(io.testfly.loadtest.LoadTestConfig
                .resolveFor(anyScenario, getClass(), null).getResultsDir()).toAbsolutePath();
        String prefix = simulationName.replaceAll("[^a-zA-Z0-9_-]", "_") + "-";
        try (java.util.stream.Stream<java.nio.file.Path> dirs = java.nio.file.Files.list(resultsDir)) {
            List<java.nio.file.Path> found = dirs.filter(java.nio.file.Files::isDirectory)
                    .filter(d -> d.getFileName().toString().startsWith(prefix)).toList();
            assertEquals(found.size(), 1, "one run dir for " + simulationName + ": " + found);
            return found.get(0);
        }
    }

    // ── a legacy-shape scenario next to a workload scenario ──────────────

    @Test
    public void legacyShape_andWorkload_inOneSimulation_bothRun() {
        requireGatling();
        LoadScenario legacy = LoadScenario.named("Legacy")
                .users(2).rampUp(Duration.ofSeconds(1)).hold(Duration.ofSeconds(1))
                .step("legacy").get("/sim-legacy").and();
        LoadScenario modern = LoadScenario.named("Modern")
                .step("modern").get("/sim-workload").and()
                .workload((Workload) open(atOnce(4)));
        Object sim = SimulationApi.named(unique("SimLegacyMix"));
        SimulationApi.baseUrl(sim, baseUrl);
        SimulationApi.engine(sim, "gatling");
        SimulationApi.scenario(sim, legacy);
        SimulationApi.scenario(sim, modern);

        Object result = SimulationApi.run(sim);

        int legacyHits = hits.get("/sim-legacy").get();
        int modernHits = hits.get("/sim-workload").get();
        assertTrue(legacyHits > 0, "the legacy-shape scenario must run");
        assertEquals(modernHits, 4, "atOnce(4) on the workload scenario");
        assertEquals(SimulationApi.scenarioMetrics(result, "Legacy").totalRequests(), legacyHits);
        assertEquals(SimulationApi.scenarioMetrics(result, "Modern").totalRequests(), modernHits);
        assertEquals(SimulationApi.globalMetrics(result).totalRequests(), legacyHits + modernHits);
    }

    // ── base URL: scenario > simulation ──────────────────────────────────

    @Test
    public void scenarioBaseUrl_winsOverSimulationBaseUrl() {
        requireGatling();
        LoadScenario own = LoadScenario.named("Own")
                .baseUrl(secondaryUrl)
                .step("base").get("/sim-base").and()
                .workload((Workload) open(atOnce(2)));
        LoadScenario inherit = LoadScenario.named("Inherit")
                .step("base").get("/sim-base").and()
                .workload((Workload) open(atOnce(3)));
        Object sim = SimulationApi.named(unique("SimBaseUrl"));
        SimulationApi.baseUrl(sim, baseUrl);
        SimulationApi.engine(sim, "gatling");
        SimulationApi.scenario(sim, own);
        SimulationApi.scenario(sim, inherit);

        Object result = SimulationApi.run(sim);

        assertEquals(secondaryHits.get("/sim-base").get(), 2, "'Own' uses its own baseUrl");
        assertEquals(hits.get("/sim-base").get(), 3, "'Inherit' uses the simulation's baseUrl");
        assertEquals(SimulationApi.scenarioMetrics(result, "Own").totalRequests(), 2);
        assertEquals(SimulationApi.scenarioMetrics(result, "Inherit").totalRequests(), 3);
    }

    // ── rejected before any request ──────────────────────────────────────

    @Test
    public void duplicateScenarioNames_rejectedBeforeAnyRequest() {
        requireGatling();
        String dup = "Dup" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        Object sim = SimulationApi.named(unique("SimDup"));
        SimulationApi.baseUrl(sim, baseUrl);
        SimulationApi.engine(sim, "gatling");

        // the rejection may come from scenario(..) or from run(); both are before any request
        IllegalArgumentException e = expectThrows(IllegalArgumentException.class, () -> {
            SimulationApi.scenario(sim, LoadScenario.named(dup).step("d1").get("/sim-dup").and()
                    .workload((Workload) open(atOnce(1))));
            SimulationApi.scenario(sim, LoadScenario.named(dup).step("d2").get("/sim-dup").and()
                    .workload((Workload) open(atOnce(1))));
            SimulationApi.run(sim);
        });

        assertTrue(e.getMessage() != null && e.getMessage().contains(dup),
                "message must name the duplicate scenario '" + dup + "', was: " + e.getMessage());
        assertEquals(hits.get("/sim-dup").get(), 0, "duplicate names must be rejected before any request");
    }

    @Test
    public void jdkEngine_rejectedBeforeAnyRequest() {
        Object sim = SimulationApi.named(unique("SimJdk"));
        SimulationApi.baseUrl(sim, baseUrl);
        SimulationApi.engine(sim, "jdk");
        SimulationApi.scenario(sim, LoadScenario.named("J1").users(1).step("j1").get("/sim-jdk").and());
        SimulationApi.scenario(sim, LoadScenario.named("J2").users(1).step("j2").get("/sim-jdk").and());

        IllegalArgumentException e = expectThrows(IllegalArgumentException.class, () -> SimulationApi.run(sim));

        assertTrue(e.getMessage() != null && e.getMessage().contains("loadSimulation(...) requires the Gatling engine"),
                "message must say the simulation needs Gatling, was: " + e.getMessage());
        assertEquals(hits.get("/sim-jdk").get(), 0, "the JDK engine must be rejected before any request");
    }

    @Test
    public void emptySimulation_rejectedBeforeAnyRequest() {
        requireGatling();
        Object sim = SimulationApi.named(unique("SimEmpty"));
        SimulationApi.baseUrl(sim, baseUrl);
        SimulationApi.engine(sim, "gatling");

        IllegalArgumentException e = expectThrows(IllegalArgumentException.class, () -> SimulationApi.run(sim));

        assertTrue(e.getMessage() != null && e.getMessage().toLowerCase(Locale.ROOT).contains("scenario"),
                "message must say a scenario is needed, was: " + e.getMessage());
        assertEquals(hits.get("/sim-empty").get(), 0, "an empty simulation must not send anything");
    }

    @Test
    public void scenarioEngineConflictingWithSimulation_rejectedBeforeAnyRequest() {
        requireGatling();
        Object sim = SimulationApi.named(unique("SimConflict"));
        SimulationApi.baseUrl(sim, baseUrl);
        SimulationApi.engine(sim, "gatling");

        IllegalArgumentException e = expectThrows(IllegalArgumentException.class, () -> {
            SimulationApi.scenario(sim, LoadScenario.named("C1").engine("jdk").users(1)
                    .step("c1").get("/sim-conflict").and());
            SimulationApi.scenario(sim, LoadScenario.named("C2").users(1)
                    .step("c2").get("/sim-conflict").and());
            SimulationApi.run(sim);
        });

        assertTrue(e.getMessage() != null && e.getMessage().contains("scenario 'C1' sets engine 'jdk'")
                        && e.getMessage().contains("conflicts with the simulation's engine 'gatling'"),
                "message must name the conflicting scenario engine, was: " + e.getMessage());

        assertEquals(hits.get("/sim-conflict").get(), 0,
                "a scenario engine that conflicts with the simulation's must be rejected before any request");
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private static void requireGatling() {
        if (!GatlingBridge.isAvailable()) {
            throw new SkipException("Gatling is not available on classpath — skipping LoadSimulation Gatling tests");
        }
    }

    private static String unique(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }

    private static void endpoint(HttpServer srv, Map<String, AtomicInteger> counters, String path, int status,
            long sleepMs) {
        AtomicInteger counter = new AtomicInteger();
        counters.put(path, counter);
        srv.createContext(path, ex -> {
            counter.incrementAndGet();
            try {
                ex.getRequestBody().readAllBytes();
                if (sleepMs > 0) {
                    Thread.sleep(sleepMs);
                }
                send(ex, status, "{}");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
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
