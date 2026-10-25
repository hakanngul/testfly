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
    public void testRunnerRejectsBeforeAnyRequest() {
        int before = hits.get();
        LoadScenario s = LoadScenario.named("runnerReject").baseUrl(baseUrl).engine("jdk")
                .users(1).rampUp(Duration.ZERO).hold(Duration.ofSeconds(1))
                .step("s").get("/x").check(LoadStep.jsonPath("$..id").exists()).and();

        expectThrows(IllegalArgumentException.class, () -> LoadTestRunner.run(s));
        assertEquals(hits.get(), before, "no request may be sent for an invalid scenario");
    }
}
