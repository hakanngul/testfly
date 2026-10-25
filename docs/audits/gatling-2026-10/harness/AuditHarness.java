import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.testfly.loadtest.CheckCondition;
import io.testfly.loadtest.LoadScenario;
import io.testfly.loadtest.LoadStep;
import io.testfly.loadtest.LoadTestAssert;
import io.testfly.loadtest.LoadTestFeeder;
import io.testfly.loadtest.LoadTestMetrics;

import java.io.File;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Supplier;

/**
 * Isolated audit harness: drives TestFly's real Gatling engine against a local
 * JDK HttpServer on 127.0.0.1. Does not touch repository sources.
 */
public class AuditHarness {

    static final Map<String, AtomicInteger> HITS = new ConcurrentHashMap<>();
    static final Set<String> SEEN_IDS = ConcurrentHashMap.newKeySet();
    static final Set<String> SEEN_USERS = ConcurrentHashMap.newKeySet();
    static final List<String> CONTENT_TYPES = Collections.synchronizedList(new ArrayList<>());
    static final List<String> BODIES = Collections.synchronizedList(new ArrayList<>());
    static final AtomicInteger AUTH_OK = new AtomicInteger(), AUTH_FAIL = new AtomicInteger();
    static final AtomicInteger INFLIGHT = new AtomicInteger(), PEAK = new AtomicInteger();
    static final AtomicInteger TOKEN_SEQ = new AtomicInteger();
    static final AtomicInteger FAILURES = new AtomicInteger();
    static String base;

    static void reset() {
        HITS.clear(); SEEN_IDS.clear(); SEEN_USERS.clear(); CONTENT_TYPES.clear(); BODIES.clear();
        AUTH_OK.set(0); AUTH_FAIL.set(0); INFLIGHT.set(0); PEAK.set(0);
    }

    static void send(HttpExchange ex, int code, String body) throws java.io.IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(code, b.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(b); }
    }

    static void ctx(HttpServer s, String path, int code, String body) {
        s.createContext(path, ex -> {
            HITS.computeIfAbsent(path, k -> new AtomicInteger()).incrementAndGet();
            String q = ex.getRequestURI().getQuery();
            if (q != null) {
                for (String kv : q.split("&")) {
                    if (kv.startsWith("id=")) SEEN_IDS.add(kv.substring(3));
                    if (kv.startsWith("u=")) SEEN_USERS.add(kv.substring(2));
                }
            }
            CONTENT_TYPES.add(String.valueOf(ex.getRequestHeaders().getFirst("Content-Type")));
            BODIES.add(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            send(ex, code, body);
        });
    }

    public static void main(String[] args) throws Exception {
        HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        s.setExecutor(Executors.newCachedThreadPool());
        ctx(s, "/ok", 200, "{\"status\":\"ok\"}");
        ctx(s, "/created", 201, "{\"id\":\"abc\"}");
        ctx(s, "/bizfail", 200, "{\"success\":false,\"error\":\"insufficient funds\"}");
        ctx(s, "/echo", 200, "{\"echo\":true}");
        ctx(s, "/form", 200, "{}");
        s.createContext("/login", ex -> {
            HITS.computeIfAbsent("/login", k -> new AtomicInteger()).incrementAndGet();
            send(ex, 200, "{\"accessToken\":\"tok-" + TOKEN_SEQ.incrementAndGet() + "\"}");
        });
        s.createContext("/secure", ex -> {
            String a = ex.getRequestHeaders().getFirst("Authorization");
            if (a != null && a.startsWith("Bearer tok-")) { AUTH_OK.incrementAndGet(); send(ex, 200, "{}"); }
            else { AUTH_FAIL.incrementAndGet(); send(ex, 401, "{}"); }
        });
        s.createContext("/conc", ex -> {
            int now = INFLIGHT.incrementAndGet();
            PEAK.accumulateAndGet(now, Math::max);
            try { Thread.sleep(200); } catch (InterruptedException ignored) {}
            INFLIGHT.decrementAndGet();
            send(ex, 200, "{}");
        });
        s.start();
        base = "http://127.0.0.1:" + s.getAddress().getPort();
        String only = args.length > 0 ? args[0] : "all";

        Map<String, Runnable> cases = new LinkedHashMap<>();

        cases.put("V01_smoke", () -> {
            LoadTestMetrics m = scenario("V01").step("ok").get("/ok").and()
                    .users(3).rampUp(Duration.ofSeconds(1)).hold(Duration.ofSeconds(2)).run().metrics();
            dump(m);
            report(m.totalRequests() > 0 && m.failedRequests() == 0 && "gatling".equals(m.engine()),
                    "requests=" + m.totalRequests() + " ko=" + m.failedRequests() + " engine=" + m.engine()
                            + " serverHits=" + HITS.get("/ok"));
        });

        cases.put("V02_statusCodeFidelity_201", () -> {
            LoadTestMetrics m = scenario("V02").step("create").post("/created").check(LoadStep.status().is(201)).and()
                    .users(2).rampUp(Duration.ofSeconds(1)).hold(Duration.ofSeconds(2)).run().metrics();
            dump(m);
            report(m.statusCodes().equals(Map.of(201, (long) m.totalRequests())),
                    "server returned only 201; expected {201=" + m.totalRequests() + "}, TestFly statusCodes="
                            + m.statusCodes());
        });

        cases.put("V03_correlation_extract_token", () -> {
            LoadTestMetrics m = scenario("V03")
                    .step("login").post("/login").extract("token", "$.accessToken").and()
                    .step("secure").get("/secure").header("Authorization", "Bearer ${token}").check(LoadStep.status().is(200)).and()
                    .users(3).rampUp(Duration.ofSeconds(1)).hold(Duration.ofSeconds(2)).run().metrics();
            dump(m);
            report(AUTH_FAIL.get() == 0 && AUTH_OK.get() > 0 && m.failedRequests() == 0,
                    "authOk=" + AUTH_OK + " authFail=" + AUTH_FAIL + " ko=" + m.failedRequests());
        });

        cases.put("V04_sequence_feeder", () -> {
            LoadTestMetrics m = scenario("V04").step("echo").get("/echo?id=${id}").and()
                    .feed(LoadTestFeeder.sequence("id", 1000, 1))
                    .users(3).rampUp(Duration.ofSeconds(1)).hold(Duration.ofSeconds(2)).run().metrics();
            boolean literal = SEEN_IDS.stream().anyMatch(x -> x.contains("{"));
            report(SEEN_IDS.size() > 3 && !literal, "distinctIds=" + SEEN_IDS.size() + " literalPlaceholderSeen=" + literal
                    + " requests=" + m.totalRequests());
        });

        cases.put("V05_csv_feeder", () -> {
            LoadTestMetrics m = scenario("V05").step("echo").get("/echo?u=${username}").and()
                    .feed(LoadTestFeeder.csv("loadtest/users.csv"))
                    .users(3).rampUp(Duration.ofSeconds(1)).hold(Duration.ofSeconds(2)).run().metrics();
            report(!SEEN_USERS.isEmpty() && SEEN_USERS.stream().noneMatch(x -> x.contains("{")),
                    "usersSeen=" + SEEN_USERS + " requests=" + m.totalRequests());
        });

        cases.put("V06_failing_status_check", () -> {
            LoadTestMetrics m = scenario("V06").step("ok").get("/ok").check(LoadStep.status().is(201)).and()
                    .users(2).rampUp(Duration.ofSeconds(1)).hold(Duration.ofSeconds(2)).run().metrics();
            dump(m);
            report(m.errorRate() == 1.0, "errorRate=" + m.errorRate() + " statusCodes=" + m.statusCodes()
                    + " (server only ever returned 200)");
        });

        cases.put("V07_status_in_check_dropped", () -> {
            for (String eng : List.of("gatling", "jdk")) {
                reset();
                LoadTestMetrics m = scenario("V07-" + eng).engine(eng).step("c").get("/created")
                        .check(LoadStep.status().in(200, 204)).and()
                        .users(2).rampUp(Duration.ofSeconds(1)).hold(Duration.ofSeconds(2)).run().metrics();
                report(m.errorRate() == 1.0, "[" + eng + "] status().in(200,204) vs actual 201 -> errorRate=" + m.errorRate());
            }
        });

        cases.put("V08_http200_business_failure", () -> {
            for (String eng : List.of("gatling", "jdk")) {
                LoadTestMetrics m = scenario("V08-" + eng).engine(eng).step("pay").get("/bizfail")
                        .check(LoadStep.jsonPath("$.success").matches("\"success\"\\s*:\\s*true")).and()
                        .users(2).rampUp(Duration.ofSeconds(1)).hold(Duration.ofSeconds(2)).run().metrics();
                report(m.errorRate() == 1.0, "[" + eng + "] body {success:false} with body-regex check -> errorRate=" + m.errorRate());
            }
        });

        cases.put("V09_jsonPath_is_becomes_status_check", () -> {
            CheckCondition check = LoadStep.jsonPath("$.id").is(5);
            LoadTestMetrics m = scenario("V09").step("c").get("/created")
                    .check(check).and()
                    .users(1).rampUp(Duration.ofSeconds(1)).hold(Duration.ofSeconds(2)).run().metrics();
            // A JSONPath value check must keep its target and must not become a status-code check.
            report(check.type() != CheckCondition.Type.STATUS_IS && "$.id".equals(check.target()),
                    "jsonPath(\"$.id\").is(5) -> type=" + check.type() + " target=" + check.target()
                            + "; run on body {id:'abc'}: errorRate=" + m.errorRate() + " statusCodes=" + m.statusCodes());
        });

        cases.put("V10_p90_fabricated", () -> {
            LoadTestMetrics m = scenario("V10").step("conc").get("/conc").and()
                    .users(3).rampUp(Duration.ofSeconds(1)).hold(Duration.ofSeconds(2)).run().metrics();
            double formula = m.p50LatencyMs() + (m.p95LatencyMs() - m.p50LatencyMs()) * 0.8;
            report(Math.abs(formula - m.p90LatencyMs()) > 0.0001,
                    "p50=" + m.p50LatencyMs() + " p90=" + m.p90LatencyMs() + " p95=" + m.p95LatencyMs()
                            + " p50+(p95-p50)*0.8=" + formula + " -> p90 is interpolated, not measured");
        });

        cases.put("V11_cooldown_ignored", () -> {
            for (String eng : List.of("gatling", "jdk")) {
                long t0 = System.nanoTime();
                LoadTestMetrics m = scenario("V11-" + eng).engine(eng).step("ok").get("/ok").and()
                        .users(2).rampUp(Duration.ofSeconds(1)).hold(Duration.ofSeconds(2)).cooldown(Duration.ofSeconds(8))
                        .run().metrics();
                long wall = (System.nanoTime() - t0) / 1_000_000;
                report(m.durationMs() >= 9000, "[" + eng + "] ramp=1s hold=2s cooldown=8s -> reported durationMs="
                        + m.durationMs() + " wallMs=" + wall);
            }
        });

        cases.put("V12_users_semantics_open_vs_closed", () -> {
            for (String eng : List.of("gatling", "jdk")) {
                reset();
                LoadTestMetrics m = scenario("V12-" + eng).engine(eng).step("conc").get("/conc").and()
                        .users(10).rampUp(Duration.ofSeconds(6)).hold(Duration.ofSeconds(2)).run().metrics();
                report(PEAK.get() >= 9, "[" + eng + "] users(10) rampUp=6s hold=2s -> server-observed peak concurrency="
                        + PEAK.get() + " (metrics.users=" + m.users() + ")");
            }
        });

        cases.put("V13_connection_refused_reported_as_500", () -> {
            LoadTestMetrics m = scenario("V13").baseUrl("http://127.0.0.1:1").step("down").get("/x").and()
                    .users(1).rampUp(Duration.ofSeconds(1)).hold(Duration.ofSeconds(1)).run().metrics();
            dump(m);
            // No response was ever received, so no real HTTP status (100-599) may appear.
            boolean noHttpStatus = m.statusCodes().keySet().stream().noneMatch(c -> c >= 100 && c <= 599);
            report(noHttpStatus, "no HTTP response at all; statusCodes=" + m.statusCodes()
                    + " ko=" + m.failedRequests());
        });

        cases.put("V14_simulation_build_failure_error_propagation", () -> {
            try {
                scenario("V14").step("bad").get("/ok?x=${unclosed").and()
                        .users(1).rampUp(Duration.ofSeconds(1)).hold(Duration.ofSeconds(1)).run();
                report(false, "no exception thrown");
            } catch (Throwable t) {
                String msg = String.valueOf(t.getMessage());
                report(msg.contains("unclosed") || msg.toLowerCase().contains("el") && msg.toLowerCase().contains("pars"),
                        "exception=" + t.getClass().getSimpleName() + ": " + msg);
            }
        });

        cases.put("V15_cancellation_orphan_process", () -> {
            Thread runner = new Thread(() -> {
                try {
                    scenario("V15").step("ok").get("/ok").and().users(2).rampUp(Duration.ofSeconds(1))
                            .hold(Duration.ofSeconds(25)).run();
                } catch (Throwable t) {
                    System.out.println("  runner ended: " + t.getClass().getSimpleName() + ": " + t.getMessage());
                }
            });
            runner.start();
            // Interrupt only once the forked Gatling JVM is actually running and producing load.
            long deadline = System.currentTimeMillis() + 30_000;
            List<ProcessHandle> children = List.of();
            while (System.currentTimeMillis() < deadline && (children.isEmpty() || HITS.get("/ok") == null)) {
                sleep(200);
                children = ProcessHandle.current().descendants().filter(ProcessHandle::isAlive).toList();
            }
            if (children.isEmpty()) {
                report(false, "Gatling child JVM never observed within 30s; cannot evaluate cancellation");
                runner.interrupt();
                return;
            }
            runner.interrupt();
            try { runner.join(10_000); } catch (InterruptedException ignored) {}
            long stopDeadline = System.currentTimeMillis() + 5_000;
            while (System.currentTimeMillis() < stopDeadline && children.stream().anyMatch(ProcessHandle::isAlive)) {
                sleep(200);
            }
            long alive = children.stream().filter(ProcessHandle::isAlive).count();
            report(alive == 0, "observed child pids=" + children.stream().map(ProcessHandle::pid).toList()
                    + "; still alive 5s after interrupt=" + alive);
            children.forEach(ProcessHandle::destroyForcibly);
        });

        cases.put("V16_concurrent_executions_serialized", () -> {
            ExecutorService ex = Executors.newFixedThreadPool(2);
            long t0 = System.nanoTime();
            List<Future<LoadTestMetrics>> fs = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                int n = i;
                fs.add(ex.submit(() -> scenario("V16-" + n).step("ok" + n).get("/ok").and().users(2)
                        .rampUp(Duration.ofSeconds(1)).hold(Duration.ofSeconds(3)).run().metrics()));
            }
            try {
                LoadTestMetrics a = fs.get(0).get(), b = fs.get(1).get();
                long wall = (System.nanoTime() - t0) / 1_000_000;
                boolean isolated = a.steps().containsKey("ok0") && !a.steps().containsKey("ok1")
                        && b.steps().containsKey("ok1") && !b.steps().containsKey("ok0");
                report(isolated, "results isolated=" + isolated + " steps0=" + a.steps().keySet() + " steps1="
                        + b.steps().keySet() + " totalWallMs=" + wall + " (serialized by static GATLING_LOCK)");
            } catch (Exception e) { report(false, e.toString()); }
            ex.shutdown();
        });

        cases.put("V17_html_report_generated", () -> {
            scenario("V17").step("ok").get("/ok").and().users(1).rampUp(Duration.ofSeconds(1)).hold(Duration.ofSeconds(1)).run();
            File[] runs = new File("target/loadtest").listFiles(f -> f.getName().startsWith("V17-"));
            File idx = null;
            if (runs != null && runs.length > 0) {
                File[] sims = runs[0].listFiles(File::isDirectory);
                if (sims != null && sims.length > 0) idx = new File(sims[0], "index.html");
            }
            report(idx != null && idx.length() > 0, "index.html=" + (idx == null ? null : idx.getPath() + " (" + idx.length() + " bytes)"));
        });

        cases.put("V18_xml_body_content_type", () -> {
            reset();
            scenario("V18").step("xml").post("/echo").header("X-T", "1").body("<order><id>1</id></order>").and()
                    .users(1).rampUp(Duration.ofSeconds(1)).hold(Duration.ofSeconds(1)).run();
            report(!CONTENT_TYPES.isEmpty() && CONTENT_TYPES.stream().noneMatch(c -> c.contains("json")),
                    "XML body sent with Content-Type=" + new HashSet<>(CONTENT_TYPES));
        });

        cases.put("V19_formParam_ignored", () -> {
            for (String eng : List.of("gatling", "jdk")) {
                reset();
                scenario("V19-" + eng).engine(eng).step("form").post("/form").formParam("user", "alice").and()
                        .users(1).rampUp(Duration.ofSeconds(1)).hold(Duration.ofSeconds(1)).run();
                report(BODIES.stream().anyMatch(b -> b.contains("user=alice")),
                        "[" + eng + "] formParam(user,alice) -> bodies seen=" + new HashSet<>(BODIES)
                                + " contentTypes=" + new HashSet<>(CONTENT_TYPES));
            }
        });

        cases.put("V20_assertion_failure_propagates", () -> {
            LoadTestAssert a = scenario("V20").step("conc").get("/conc").and().users(1)
                    .rampUp(Duration.ofSeconds(1)).hold(Duration.ofSeconds(2)).run();
            try {
                a.assertP95Below(50);
                report(false, "no AssertionError although p95=" + a.metrics().p95LatencyMs());
            } catch (AssertionError e) {
                report(true, "AssertionError: " + e.getMessage());
            }
        });

        for (var e : cases.entrySet()) {
            if (!"all".equals(only) && !e.getKey().startsWith(only)) continue;
            reset();
            System.out.println("\n##### " + e.getKey());
            try { e.getValue().run(); }
            catch (Throwable t) { FAILURES.incrementAndGet(); System.out.println("RESULT: ERROR " + t); }
        }
        s.stop(0);
        System.out.println("\nFAIL/ERROR results: " + FAILURES.get());
        System.exit(FAILURES.get() == 0 ? 0 : 1);
    }

    static LoadScenario scenario(String name) {
        return LoadScenario.named(name).baseUrl(base).engine("gatling");
    }

    static void dump(LoadTestMetrics m) {
        System.out.println("  metrics: total=" + m.totalRequests() + " ok=" + m.successfulRequests() + " ko=" + m.failedRequests()
                + " rps=" + String.format("%.2f", m.throughputRps()) + " p50=" + m.p50LatencyMs() + " p90=" + m.p90LatencyMs()
                + " p95=" + m.p95LatencyMs() + " p99=" + m.p99LatencyMs() + " durationMs=" + m.durationMs()
                + " status=" + m.statusCodes() + " steps=" + m.steps().keySet());
    }

    static void report(boolean expectedBehaviour, String evidence) {
        if (!expectedBehaviour) FAILURES.incrementAndGet();
        System.out.println("RESULT: " + (expectedBehaviour ? "PASS" : "FAIL") + " | " + evidence);
    }

    static void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException ignored) {} }
}
