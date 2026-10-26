package io.testfly.integration.loadtest;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.testfly.loadtest.BaseLoadTest;
import io.testfly.loadtest.LoadScenario;
import io.testfly.loadtest.LoadStep;
import io.testfly.loadtest.LoadTestAssert;
import io.testfly.loadtest.LoadTestConfig;
import io.testfly.loadtest.LoadTestMetrics;
import io.testfly.loadtest.Workload;
import io.testfly.loadtest.internal.GatlingBridge;
import io.testfly.metrics.ExecutionMetrics;
import io.testfly.unit.loadtest.SimulationApi;
import io.testfly.unit.loadtest.SlaApi;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static io.testfly.unit.loadtest.WorkloadApi.atOnce;
import static io.testfly.unit.loadtest.WorkloadApi.open;
import static org.testng.Assert.*;

/**
 * Load-test SLAs (ADR 0004 decision 5, Phase 3) against real runs on a local {@link HttpServer} bound to 127.0.0.1.
 * Every endpoint has its own request counter, so the server, not TestFly, witnesses how many requests were sent.
 *
 * <p>TestFly evaluates the SLA from its own metrics on both engines; on Gatling the same rules are also registered
 * as native assertions (report only), and Gatling's exit code 2 ("assertions failed") is not a crash.
 *
 * <p>Written before the API existed (red first); every SLA call goes through the {@link SlaApi} helper.
 *
 * <p>No class-level {@code @Test}: on a {@link BaseLoadTest} subclass it would turn the inherited public mixin
 * methods into tests. Methods are parallel-safe: each uses its own endpoints and a UUID-unique scenario or
 * simulation name, and Gatling runs are serialized by the engine itself. Gatling-dependent tests skip without
 * Gatling; the JDK half of the engine-parity test runs either way.
 */
public class LoadSlaIntegrationTest extends BaseLoadTest {

    private static final long SLOW_MS = 20;

    private HttpServer server;
    private ExecutorService serverExecutor;
    private String baseUrl;
    private final Map<String, AtomicInteger> hits = new ConcurrentHashMap<>();

    @BeforeClass
    public void startServer() throws IOException {
        serverExecutor = Executors.newCachedThreadPool();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(serverExecutor);
        endpoint("/sla-violated", 200, SLOW_MS);
        endpoint("/sla-pass-gatling", 200, 0);
        endpoint("/sla-pass-jdk", 200, 0);
        endpoint("/sla-p90", 200, SLOW_MS);
        endpoint("/sla-step-ok", 200, 0);
        endpoint("/sla-step-ko", 200, 0);
        endpoint("/sla-sim-browse", 200, 0);
        endpoint("/sla-sim-buy", 201, 0);
        endpoint("/sla-sim2-browse", 200, 0);
        endpoint("/sla-sim2-buy", 201, 0);
        endpoint("/sla-sim1-buy", 200, 0);
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

    // ── a violated SLA on Gatling ────────────────────────────────────────

    @Test
    public void violatedP95_onGatling_failsRun_afterRecordingIt_andShowsInGatlingReport() throws IOException {
        requireGatling();
        String name = unique("SlaViolated");
        LoadScenario scenario = LoadScenario.named(name)
                .baseUrl(baseUrl)
                .engine("gatling")
                .step("slow").get("/sla-violated").and()
                .workload((Workload) open(atOnce(10)));
        SlaApi.scenarioSla(scenario, SlaApi.p95Below(SlaApi.sla(), 1));

        Throwable thrown = null;
        try {
            scenario.run();
        } catch (Throwable t) {
            thrown = t;
        }

        // the verdict: TestFly's AssertionError, not the crash path of a non-zero exit code
        assertNotNull(thrown, "a violated p95Below(1) on a ~" + SLOW_MS + " ms endpoint must fail run()");
        assertTrue(thrown instanceof AssertionError,
                "run() must throw AssertionError (SLA verdict), not the crash exception; was: " + thrown);
        String msg = String.valueOf(thrown.getMessage());
        assertTrue(msg.toLowerCase(Locale.ROOT).contains("p95"), "the SLA failure must mention p95: " + msg);
        assertFalse(msg.toLowerCase(Locale.ROOT).contains("exit code"),
                "exit code 2 must not surface as a Gatling crash: " + msg);
        for (Throwable c = thrown.getCause(); c != null; c = c.getCause()) {
            assertFalse(c instanceof IllegalStateException, "no crash exception in the cause chain: " + c);
        }

        // the run is recorded first: metrics parsed despite exit code 2, with the failed SLA result
        int served = hits.get("/sla-violated").get();
        assertEquals(served, 10, "atOnce(10)");
        LoadTestMetrics recorded = recorded(name);
        assertNotNull(recorded, "ExecutionMetrics must hold the run even though its SLA failed");
        assertEquals(recorded.totalRequests(), served, "recorded total vs server counter; " + recorded);
        List<?> results = SlaApi.slaResults(recorded);
        assertEquals(results.size(), 1, "one rule, one result: " + results);
        Object p95 = results.get(0);
        assertEquals(SlaApi.metric(SlaApi.resultRule(p95)), "P95");
        assertEquals(SlaApi.scope(SlaApi.resultRule(p95)), "global");
        assertFalse(SlaApi.passed(p95), "p95Below(1) must fail: " + p95);
        assertTrue(SlaApi.actual(p95) >= 1, "actual p95 is measured and >= 1 ms: " + p95);
        assertEquals(SlaApi.actual(p95), recorded.p95LatencyMs(), 0.0, "actual = the metrics' p95");
        List<?> slaMap = (List<?>) ExecutionMetrics.loadTestMetricsToMap(recorded).get("sla");
        assertEquals(((Map<?, ?>) slaMap.get(0)).get("passed"), false, "the report entry carries the failure");

        // the same rule was registered as a native Gatling assertion: Gatling's own report shows it
        Path runDir = newestRunDir(scenario, name);
        String indexHtml = gatlingIndexHtml(runDir).toLowerCase(Locale.ROOT);
        assertTrue(indexHtml.contains("95th percentile of response time is less than"),
                "Gatling's index.html must list the native p95 assertion; run dir " + runDir);
    }

    // ── a satisfiable SLA passes on both engines ─────────────────────────

    @DataProvider(name = "engines")
    public Object[][] engines() {
        return new Object[][] {{"gatling"}, {"jdk"}};
    }

    @Test(dataProvider = "engines")
    public void satisfiableSla_passes_onBothEngines(String engine) {
        if ("gatling".equals(engine)) {
            requireGatling();
        }
        String name = unique("SlaPass_" + engine);
        String path = "/sla-pass-" + engine;
        LoadScenario scenario = LoadScenario.named(name)
                .baseUrl(baseUrl)
                .engine(engine)
                .users(2).rampUp(Duration.ofSeconds(1)).hold(Duration.ofSeconds(2))
                .step("pass").get(path).and();
        Object sla = SlaApi.sla();
        sla = SlaApi.p95Below(sla, 10_000);
        sla = SlaApi.errorRateBelow(sla, 0.5);
        SlaApi.scenarioSla(scenario, sla);

        LoadTestAssert result = scenario.run();

        LoadTestMetrics m = result.metrics();
        assertTrue(m.totalRequests() > 0, "the run must send requests");
        assertEquals(m.totalRequests(), hits.get(path).get(), "total vs server counter; " + m);
        List<?> results = SlaApi.slaResults(result);
        assertEquals(results.size(), 2, engine + ": one result per rule: " + results);
        assertEquals(SlaApi.metric(SlaApi.resultRule(results.get(0))), "P95", "declaration order");
        assertEquals(SlaApi.metric(SlaApi.resultRule(results.get(1))), "ERROR_RATE", "declaration order");
        for (Object r : results) {
            assertTrue(SlaApi.passed(r), engine + ": a generous SLA must pass: " + r);
            assertNull(SlaApi.reason(r), "a passed rule has no reason: " + r);
            assertTrue(LoadTestMetrics.isMeasured(SlaApi.actual(r)), engine + ": actual is measured: " + r);
        }
        assertEquals(SlaApi.actual(results.get(0)), m.p95LatencyMs(), 0.0);
        assertEquals(SlaApi.actual(results.get(1)), m.errorRate(), 0.0);
        assertEquals(SlaApi.slaResults(m), results, "the metrics carry the same results");
        SlaApi.assertSlaPassed(result);

        LoadTestMetrics recorded = recorded(name);
        assertNotNull(recorded, "ExecutionMetrics must hold the run");
        assertEquals(SlaApi.slaResults(recorded), results, "the recorded metrics carry the SLA results");
    }

    // ── p90 on Gatling ───────────────────────────────────────────────────

    @Test
    public void p90Sla_isEvaluated_onGatling() {
        requireGatling();
        String name = unique("SlaP90");
        LoadScenario scenario = LoadScenario.named(name)
                .baseUrl(baseUrl)
                .engine("gatling")
                .step("p90").get("/sla-p90").and()
                .workload((Workload) open(atOnce(10)));
        SlaApi.scenarioSla(scenario, SlaApi.p90Below(SlaApi.sla(), 10_000));

        LoadTestAssert result = scenario.run();

        List<?> results = SlaApi.slaResults(result);
        assertEquals(results.size(), 1, results.toString());
        Object p90 = results.get(0);
        assertEquals(SlaApi.metric(SlaApi.resultRule(p90)), "P90");
        assertTrue(LoadTestMetrics.isMeasured(SlaApi.actual(p90)), "p90 must be measured on Gatling: " + p90);
        assertEquals(SlaApi.actual(p90), result.metrics().p90LatencyMs(), 0.0, "actual = the metrics' p90");
        assertTrue(SlaApi.actual(p90) >= SLOW_MS - 5,
                "p90 reflects the ~" + SLOW_MS + " ms endpoint, was " + SlaApi.actual(p90));
        assertTrue(SlaApi.passed(p90), "p90Below(10000) must pass: " + p90);
    }

    // ── a step SLA on a failing step ─────────────────────────────────────

    @Test
    public void stepSla_onFailingStep_fails_whileGlobalSlaOnOtherMetricsPasses() {
        requireGatling();
        String name = unique("SlaStep");
        LoadScenario scenario = LoadScenario.named(name)
                .baseUrl(baseUrl)
                .engine("gatling")
                .step("ok").get("/sla-step-ok").and()
                // the server answers 200, the check wants 201: every "ko" request fails
                .step("ko").get("/sla-step-ko").check(LoadStep.status().is(201)).and()
                .workload((Workload) open(atOnce(5)));
        Object sla = SlaApi.sla();
        sla = SlaApi.p95Below(sla, 10_000);
        sla = SlaApi.throughputAbove(sla, 0.001);
        sla = SlaApi.stepErrorRateBelow(SlaApi.step(sla, "ok"), 0.1);
        sla = SlaApi.stepErrorRateBelow(SlaApi.step(sla, "ko"), 0.1);
        SlaApi.scenarioSla(scenario, sla);

        AssertionError e = expectThrows(AssertionError.class, scenario::run);

        assertTrue(String.valueOf(e.getMessage()).contains("ko"),
                "the failure must name the failing step 'ko': " + e.getMessage());
        assertEquals(hits.get("/sla-step-ko").get(), 5, "atOnce(5) reaches the ko step");
        LoadTestMetrics recorded = recorded(name);
        assertNotNull(recorded, "ExecutionMetrics must hold the run even though its SLA failed");
        assertEquals(recorded.step("ko").failedRequests(), 5, "every ko request failed its check");

        List<?> results = SlaApi.slaResults(recorded);
        assertEquals(results.size(), 4, results.toString());
        assertPassed(results.get(0), "global", "P95", true);
        assertPassed(results.get(1), "global", "THROUGHPUT", true);
        assertPassed(results.get(2), "ok", "ERROR_RATE", true);
        assertPassed(results.get(3), "ko", "ERROR_RATE", false);
        assertEquals(SlaApi.actual(results.get(3)), 1.0, 1e-9, "the ko step's error rate is 100%");
        assertEquals(SlaApi.actual(results.get(2)), 0.0, 1e-9, "the ok step's error rate is 0%");
    }

    // ── simulation-level SLAs ────────────────────────────────────────────

    @Test
    public void simulationSla_onQualifiedStep_appliesToThatScenarioStepOnly() {
        requireGatling();
        String simName = unique("SlaSim");
        // Browse fails every request; Buy succeeds. A global error-rate rule would fail; "Buy :: buy" passes.
        LoadScenario browse = LoadScenario.named("Browse")
                .step("browse").get("/sla-sim-browse").check(LoadStep.status().is(201)).and()
                .workload((Workload) open(atOnce(4)));
        LoadScenario buy = LoadScenario.named("Buy")
                .step("buy").get("/sla-sim-buy").check(LoadStep.status().is(201)).and()
                .workload((Workload) open(atOnce(3)));
        Object sim = SimulationApi.named(simName);
        SimulationApi.baseUrl(sim, baseUrl);
        SimulationApi.engine(sim, "gatling");
        SimulationApi.scenario(sim, browse);
        SimulationApi.scenario(sim, buy);
        Object sla = SlaApi.sla();
        sla = SlaApi.stepErrorRateBelow(SlaApi.step(sla, "Buy :: buy"), 0.1);
        sla = SlaApi.stepP95Below(SlaApi.step(sla, "Buy :: buy"), 10_000);
        SlaApi.simulationSla(sim, sla);

        Object result = SimulationApi.run(sim);

        assertEquals(hits.get("/sla-sim-browse").get(), 4);
        assertEquals(hits.get("/sla-sim-buy").get(), 3);
        assertTrue(SimulationApi.globalMetrics(result).errorRate() > 0.1,
                "precondition: the global error rate is high, so only a step-scoped rule can pass");
        List<?> results = SlaApi.slaResults(result);
        assertEquals(results.size(), 2, results.toString());
        assertPassed(results.get(0), "Buy :: buy", "ERROR_RATE", true);
        assertEquals(SlaApi.actual(results.get(0)), 0.0, 1e-9, "Buy's step error rate");
        assertPassed(results.get(1), "Buy :: buy", "P95", true);
        assertTrue(LoadTestMetrics.isMeasured(SlaApi.actual(results.get(1))), "Buy's step p95 is measured");
    }

    @Test
    public void simulationSla_onUniqueBareStep_failsRun_afterRecordingIt() {
        requireGatling();
        String simName = unique("SlaSimFail");
        LoadScenario browse = LoadScenario.named("Browse")
                .step("browse").get("/sla-sim2-browse").check(LoadStep.status().is(201)).and()
                .workload((Workload) open(atOnce(4)));
        LoadScenario buy = LoadScenario.named("Buy")
                .step("buy").get("/sla-sim2-buy").check(LoadStep.status().is(201)).and()
                .workload((Workload) open(atOnce(3)));
        Object sim = SimulationApi.named(simName);
        SimulationApi.baseUrl(sim, baseUrl);
        SimulationApi.engine(sim, "gatling");
        SimulationApi.scenario(sim, browse);
        SimulationApi.scenario(sim, buy);
        // bare "browse" is unique across the scenarios, so it resolves to "Browse :: browse"
        SlaApi.simulationSla(sim, SlaApi.stepErrorRateBelow(SlaApi.step(SlaApi.sla(), "browse"), 0.1));

        AssertionError e = expectThrows(AssertionError.class, () -> SimulationApi.run(sim));

        assertTrue(String.valueOf(e.getMessage()).contains("browse"),
                "the failure must name the step: " + e.getMessage());
        assertEquals(hits.get("/sla-sim2-browse").get(), 4, "the run happened");
        LoadTestMetrics global = recorded(simName + " (global)");
        assertNotNull(global, "ExecutionMetrics must hold the simulation's global entry before the verdict");
        assertEquals(global.totalRequests(), 7, "4 + 3");
    }

    @Test
    public void simulationSla_qualifiedStep_inAOneScenarioSimulation_resolvesToTheUnprefixedStep() throws IOException {
        requireGatling();
        String simName = unique("SlaSim1");
        // one scenario: request names are unprefixed ("buy"), the SLA names "Buy :: buy"
        LoadScenario buy = LoadScenario.named("Buy")
                .step("buy").get("/sla-sim1-buy").and()
                .workload((Workload) open(atOnce(3)));
        Object sim = SimulationApi.named(simName);
        SimulationApi.baseUrl(sim, baseUrl);
        SimulationApi.engine(sim, "gatling");
        SimulationApi.scenario(sim, buy);
        SlaApi.simulationSla(sim, SlaApi.stepErrorRateBelow(SlaApi.step(SlaApi.sla(), "Buy :: buy"), 0.1));

        Object result = SimulationApi.run(sim);

        assertEquals(hits.get("/sla-sim1-buy").get(), 3, "atOnce(3)");
        List<?> results = SlaApi.slaResults(result);
        assertEquals(results.size(), 1, results.toString());
        assertPassed(results.get(0), "Buy :: buy", "ERROR_RATE", true);
        assertEquals(SlaApi.actual(results.get(0)), 0.0, 1e-9, "the step ran cleanly: not 'step not found'");

        // the same rule was registered natively on the unprefixed request name
        Path runDir = newestRunDir(buy, simName);
        String indexHtml = gatlingIndexHtml(runDir).toLowerCase(Locale.ROOT);
        assertTrue(indexHtml.contains("buy: percentage of failed events is less than 10.0"),
                "Gatling's index.html must list the native step assertion on 'buy'; run dir " + runDir);
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private static void assertPassed(Object result, String scope, String metric, boolean passed) {
        Object rule = SlaApi.resultRule(result);
        assertEquals(SlaApi.scope(rule), scope, result.toString());
        assertEquals(SlaApi.metric(rule), metric, result.toString());
        assertEquals(SlaApi.passed(result), passed, result.toString());
    }

    /** The last recorded load-test metrics with this (UUID-unique) scenario name, or {@code null}. */
    private static LoadTestMetrics recorded(String scenarioName) {
        LoadTestMetrics found = null;
        for (LoadTestMetrics m : new ArrayList<>(ExecutionMetrics.getLoadTests())) {
            if (scenarioName.equals(m.scenarioName())) {
                found = m;
            }
        }
        return found;
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

    /** Gatling's {@code index.html} somewhere under the run dir. */
    private static String gatlingIndexHtml(Path runDir) throws IOException {
        try (Stream<Path> files = Files.walk(runDir)) {
            Path index = files.filter(p -> p.getFileName().toString().equals("index.html"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Gatling must still write index.html after exit code 2; "
                            + "none under " + runDir));
            return Files.readString(index, StandardCharsets.UTF_8);
        }
    }

    private static void requireGatling() {
        if (!GatlingBridge.isAvailable()) {
            throw new SkipException("Gatling is not available on classpath — skipping Gatling SLA tests");
        }
    }

    private static String unique(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }

    private void endpoint(String path, int status, long sleepMs) {
        AtomicInteger counter = new AtomicInteger();
        hits.put(path, counter);
        server.createContext(path, ex -> {
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
