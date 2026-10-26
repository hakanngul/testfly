package io.testfly.unit.loadtest;

import com.sun.net.httpserver.HttpServer;
import io.testfly.loadtest.LoadScenario;
import io.testfly.loadtest.LoadStep;
import io.testfly.loadtest.LoadTestRunner;
import io.testfly.loadtest.internal.LoadScenarioValidator;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.*;

/**
 * Tests the fail-fast rules of {@link LoadScenarioValidator}: invalid scenarios are rejected with
 * {@link IllegalArgumentException} before any request is sent.
 */
@Test(singleThreaded = true)
public class LoadScenarioValidatorTest {

    private HttpServer server;
    private final AtomicInteger hits = new AtomicInteger();
    private String baseUrl;

    @BeforeClass
    public void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            hits.incrementAndGet();
            ex.sendResponseHeaders(200, -1);
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
    }

    private static LoadScenario named(String name) {
        return LoadScenario.named(name);
    }

    private static IllegalArgumentException rejects(LoadScenario s, String engine) {
        return expectThrows(IllegalArgumentException.class, () -> LoadScenarioValidator.validate(s, engine));
    }

    @Test
    public void testValidScenarioPasses() {
        LoadScenario s = named("ok").step("a").post("/a").body(Map.of("x", 1))
                .check(LoadStep.status().in(200, 201))
                .check(LoadStep.jsonPath("$.items[0].id").exists())
                .check(LoadStep.jsonPath("$.id").is(5))
                .check(LoadStep.jsonPath("$.code").in(1, 2))
                .check(LoadStep.jsonPath("$.x").matches("\"ok\"\\s*:"))
                .extract("id", "$[0]").and();
        LoadScenarioValidator.validate(s, "jdk");
        LoadScenarioValidator.validate(s, "gatling");
    }

    @Test
    public void testNoStepsRejected() {
        assertTrue(rejects(named("empty"), "jdk").getMessage().contains("has no steps"));
    }

    @Test
    public void testBodyAndFormParamsRejectedForBothEngines() {
        LoadScenario s = named("both").step("s").post("/x").body("{}").formParam("a", "b").and();
        for (String engine : new String[] {"jdk", "gatling"}) {
            assertTrue(rejects(s, engine).getMessage().contains("both a body and form parameters"), engine);
        }
    }

    @Test
    public void testBlankJsonTargetRejectedForBothEngines() {
        for (String engine : new String[] {"jdk", "gatling"}) {
            rejects(named("nullPath").step("s").get("/x").check(LoadStep.jsonPath(null).exists()).and(), engine);
            rejects(named("blankPath").step("s").get("/x").check(LoadStep.jsonPath(" ").is(1)).and(), engine);
            rejects(named("blankIn").step("s").get("/x").check(LoadStep.jsonPath("").in(1)).and(), engine);
        }
    }

    @Test
    public void testInvalidRegexRejected() {
        rejects(named("regex").step("s").get("/x").check(LoadStep.status().matches("(unclosed")).and(), "gatling");
    }

    @Test
    public void testExtractNeedsPath() {
        rejects(named("extract").step("s").get("/x").extract("v", " ").and(), "gatling");
    }

    @Test
    public void testUnsupportedJsonPathRejectedOnlyForJdk() {
        String[] unsupported = {"$..id", "$.items[*].id", "$.items[?(@.id>1)]", "$.items[0:2]", "$['x']", "$", "..id"};
        for (String path : unsupported) {
            LoadScenario s = named("jp").step("s").get("/x").check(LoadStep.jsonPath(path).exists()).and();
            IllegalArgumentException e = rejects(s, "jdk");
            assertTrue(e.getMessage().contains("not supported by the JDK engine")
                    && e.getMessage().toLowerCase().contains("gatling"), path + " → " + e.getMessage());
        }
        // Gatling supports full JSONPath
        LoadScenarioValidator.validate(
                named("jp").step("s").get("/x").check(LoadStep.jsonPath("$..id").exists()).and(), "gatling");
        // extract() paths are validated too
        rejects(named("jpx").step("s").get("/x").extract("v", "$..token").and(), "jdk");
    }

    @Test
    public void testBarePathsAreNormalisedInOnePlace() {
        assertEquals(LoadScenarioValidator.normalizeJsonPath("token"), "$.token");
        assertEquals(LoadScenarioValidator.normalizeJsonPath("data.token"), "$.data.token");
        assertEquals(LoadScenarioValidator.normalizeJsonPath("[0].id"), "$[0].id");
        assertEquals(LoadScenarioValidator.normalizeJsonPath("$.a"), "$.a");
        assertNull(LoadScenarioValidator.normalizeJsonPath(null));
        // bare paths are accepted for both engines
        LoadScenario s = named("bare").step("s").get("/x")
                .check(LoadStep.jsonPath("id").exists()).extract("v", "data.token").and();
        LoadScenarioValidator.validate(s, "jdk");
        LoadScenarioValidator.validate(s, "gatling");
    }

    @Test
    public void testExpressionInStepNameRejectedForBothEngines() {
        // http(name) evaluates "#{..}" as Gatling EL, while TestFly's step markers use the literal name
        for (String name : new String[] {"get #{id}", "get ${id}", "#{x}"}) {
            for (String engine : new String[] {"jdk", "gatling"}) {
                IllegalArgumentException e = rejects(named("elName").step(name).get("/x").and(), engine);
                assertTrue(e.getMessage().contains("step name cannot contain"), name + "/" + engine + ": " + e.getMessage());
                assertTrue(e.getMessage().contains("'" + name + "'"), "message names the step: " + e.getMessage());
            }
        }
        LoadScenarioValidator.validate(named("literal").step("get {id} #1 $").get("/x?v=${id}").and(), "gatling");
    }

    @Test
    public void testRunnerRejectsExpressionStepNameBeforeAnyRequest() {
        int before = hits.get();
        LoadScenario s = LoadScenario.named("runnerRejectName").baseUrl(baseUrl).engine("jdk")
                .users(1).rampUp(Duration.ZERO).hold(Duration.ofSeconds(1))
                .step("s ${id}").get("/x").and();

        expectThrows(IllegalArgumentException.class, () -> LoadTestRunner.run(s));
        assertEquals(hits.get(), before, "no request may be sent for an invalid scenario");
    }

    @Test
    public void testWorkloadCapUsesExplicitMaxUsers() {
        LoadScenario closed = named("cap").get("/x").workload(io.testfly.loadtest.Workload.closed(
                io.testfly.loadtest.ClosedStep.constantConcurrent(11, Duration.ofSeconds(1))));
        IllegalArgumentException e = expectThrows(IllegalArgumentException.class,
                () -> LoadScenarioValidator.validate(closed, "gatling", 10));
        assertTrue(e.getMessage().contains("maxUsers=10"), e.getMessage());
        LoadScenarioValidator.validate(closed, "gatling", 11);

        LoadScenario open = named("capOpen").get("/x").workload(io.testfly.loadtest.Workload.open(
                io.testfly.loadtest.OpenStep.rampRate(1, 10.5, Duration.ofSeconds(1))));
        e = expectThrows(IllegalArgumentException.class, () -> LoadScenarioValidator.validate(open, "gatling", 10));
        assertTrue(e.getMessage().contains("maxUsers=10") && e.getMessage().contains("10.5 users/s"), e.getMessage());
    }

    @Test
    public void testWorkloadRejectedOnAnyNonGatlingEngine() {
        LoadScenario s = named("auto").get("/x").workload(io.testfly.loadtest.Workload.open(
                io.testfly.loadtest.OpenStep.atOnce(1)));
        // "auto" resolves to "jdk" without Gatling: the validator sees the resolved name
        assertTrue(rejects(s, "jdk").getMessage().contains("requires the Gatling engine"));
        expectThrows(IllegalArgumentException.class, () -> named("null").workload(null));
    }

    @Test
    public void testWorkloadThatInjectsNobodyIsRejected() {
        java.util.List<io.testfly.loadtest.Workload> empty = java.util.List.of(
                io.testfly.loadtest.Workload.open(io.testfly.loadtest.OpenStep.nothingFor(Duration.ofSeconds(1))),
                io.testfly.loadtest.Workload.open(io.testfly.loadtest.OpenStep.constantRate(0, Duration.ofSeconds(2)),
                        io.testfly.loadtest.OpenStep.rampRate(0, 0, Duration.ofSeconds(1)),
                        io.testfly.loadtest.OpenStep.nothingFor(Duration.ofSeconds(1))),
                io.testfly.loadtest.Workload.closed(
                        io.testfly.loadtest.ClosedStep.rampConcurrent(0, 0, Duration.ofSeconds(1))));
        for (io.testfly.loadtest.Workload w : empty) {
            IllegalArgumentException e = rejects(named("empty").get("/x").workload(w), "gatling");
            assertTrue(e.getMessage().contains("injects no virtual user"), w + ": " + e.getMessage());
        }
        // a single positive rate or a ramp up from 0 injects someone
        LoadScenarioValidator.validate(named("tiny").get("/x").workload(io.testfly.loadtest.Workload.open(
                io.testfly.loadtest.OpenStep.nothingFor(Duration.ofSeconds(1)),
                io.testfly.loadtest.OpenStep.rampRate(0, 0.5, Duration.ofSeconds(1)))), "gatling");
        LoadScenarioValidator.validate(named("rampUp").get("/x").workload(io.testfly.loadtest.Workload.closed(
                io.testfly.loadtest.ClosedStep.rampConcurrent(0, 1, Duration.ofSeconds(1)))), "gatling");
    }

    @Test
    public void testWorkloadShapeAndEngineAreCheckedBeforeStepDetails() {
        io.testfly.loadtest.Workload w = io.testfly.loadtest.Workload.open(io.testfly.loadtest.OpenStep.atOnce(1));
        // "$..id" is an unsupported JSONPath on the JDK engine; the workload/engine error must win
        LoadScenario badPathJdk = named("order").step("s").get("/x").check(LoadStep.jsonPath("$..id").exists()).and()
                .workload(w);
        String msg = rejects(badPathJdk, "jdk").getMessage();
        assertTrue(msg.contains("requires the Gatling engine"), msg);

        LoadScenario combinedBadPath = named("order2").step("s").get("/x").check(LoadStep.jsonPath("$..id").exists())
                .and().workload(w).users(3);
        msg = rejects(combinedBadPath, "jdk").getMessage();
        assertTrue(msg.contains("cannot be combined with users/rampUp/hold/cooldown"), msg);

        msg = rejects(named("noSteps").workload(w), "jdk").getMessage();
        assertTrue(msg.contains("requires the Gatling engine"), "engine before 'no steps': " + msg);
    }

    @Test
    public void testSimulationRejectsAConflictingScenarioEngine_butAcceptsAuto() {
        io.testfly.loadtest.Workload w = io.testfly.loadtest.Workload.open(io.testfly.loadtest.OpenStep.atOnce(1));
        io.testfly.loadtest.LoadSimulation conflict = io.testfly.loadtest.LoadSimulation.named("Conflict")
                .scenario(named("A").get("/a").workload(w))
                .scenario(named("B").engine("jdk").get("/b").workload(w));
        IllegalArgumentException e = expectThrows(IllegalArgumentException.class,
                () -> LoadScenarioValidator.validateSimulation(conflict, "gatling", 1000));
        assertTrue(e.getMessage().contains("'B'") && e.getMessage().contains("conflicts"), e.getMessage());

        io.testfly.loadtest.LoadSimulation auto = io.testfly.loadtest.LoadSimulation.named("Auto")
                .scenario(named("A").engine("auto").get("/a").workload(w))
                .scenario(named("B").engine("gatling").get("/b").workload(w));
        LoadScenarioValidator.validateSimulation(auto, "gatling", 1000);
    }

    @Test
    public void testSimulationLegacyScenariosCountTheirUsersTowardTheClosedCap() {
        io.testfly.loadtest.LoadSimulation sim = io.testfly.loadtest.LoadSimulation.named("Mix")
                .scenario(named("Legacy").users(40).get("/a"))
                .scenario(named("Closed").get("/b").workload(io.testfly.loadtest.Workload.closed(
                        io.testfly.loadtest.ClosedStep.constantConcurrent(61, Duration.ofSeconds(1)))));
        IllegalArgumentException e = expectThrows(IllegalArgumentException.class,
                () -> LoadScenarioValidator.validateSimulation(sim, "gatling", 100));
        assertTrue(e.getMessage().contains("maxUsers=100") && e.getMessage().contains("101"), e.getMessage());
    }

    @Test
    public void testRunnerRejectsBeforeAnyRequest() {
        int before = hits.get();
        LoadScenario s = LoadScenario.named("runnerReject").baseUrl(baseUrl).engine("jdk")
                .users(1).rampUp(Duration.ZERO).hold(Duration.ofSeconds(1))
                .step("s").get("/x").check(LoadStep.jsonPath("$..id").exists()).and();

        expectThrows(IllegalArgumentException.class, () -> LoadTestRunner.run(s));
        assertEquals(hits.get(), before, "no request may be sent for an invalid scenario");
    }
}
