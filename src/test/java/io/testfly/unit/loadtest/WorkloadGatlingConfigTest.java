package io.testfly.unit.loadtest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.testfly.loadtest.ClosedStep;
import io.testfly.loadtest.LoadScenario;
import io.testfly.loadtest.LoadTestConfig;
import io.testfly.loadtest.OpenStep;
import io.testfly.loadtest.Workload;
import org.testng.annotations.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;

import static org.testng.Assert.*;

/**
 * The serialised workload of the package-private {@code GatlingRunConfig} (parent JVM to forked Gatling JVM):
 * JSON round trip, planned users ({@code LoadTestMetrics.users()}) and {@code maxDuration}. Reached through
 * reflection because the class is internal and package-private.
 */
public class WorkloadGatlingConfigTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String RUN_CONFIG = "io.testfly.loadtest.internal.GatlingRunConfig";

    @Test
    public void openWorkload_roundTripsEveryStepType_withMillisecondDurations() throws Exception {
        Workload w = Workload.open(OpenStep.atOnce(3), OpenStep.ramp(5, Duration.ofSeconds(2)),
                OpenStep.constantRate(10, Duration.ofSeconds(4)),
                OpenStep.rampRate(2, 6, Duration.ofMillis(1500)),
                OpenStep.stressPeak(7, Duration.ofSeconds(3)), OpenStep.nothingFor(Duration.ofMillis(250)));
        Object cfg = roundTrip(runConfig(w));
        JsonNode json = MAPPER.valueToTree(cfg).path("scenarios").get(0).path("workload");

        assertEquals(json.path("model").asText(), "OPEN");
        JsonNode steps = json.path("steps");
        assertEquals(steps.size(), 6);
        assertStep(steps.get(0), "atOnce", "users", 3, 0);
        assertStep(steps.get(1), "ramp", "users", 5, 2);
        assertStep(steps.get(2), "constantRate", "rate", 10, 4);
        assertStep(steps.get(3), "rampRate", "fromRate", 2, 1.5);
        assertEquals(steps.get(3).path("toRate").asDouble(), 6.0, 1e-9);
        assertStep(steps.get(4), "stressPeak", "users", 7, 3);
        assertStep(steps.get(5), "nothingFor", "users", 0, 0.25);

        // users 3 + 5 + 7, arrivals ceil(10 x 4 + (2 + 6) / 2 x 1.5) = 46
        assertEquals(call(cfg, "reportedUsers"), 61);
        // ceil(0 + 2 + 4 + 1.5 + 3 + 0.25) = 11, one step x 5 s timeout, 30 s grace
        assertEquals(call(cfg, "maxDurationSeconds"), 46L);
    }

    @Test
    public void openWorkload_rateArrivalsAreNotOvercountedByFloatingPoint() throws Exception {
        Object cfg = roundTrip(runConfig(Workload.open(OpenStep.constantRate(10, Duration.ofSeconds(4)))));
        assertEquals(call(cfg, "reportedUsers"), 40);
        Object third = roundTrip(runConfig(Workload.open(OpenStep.constantRate(0.1, Duration.ofSeconds(3)))));
        assertEquals(call(third, "reportedUsers"), 1, "0.3 planned arrivals round up to 1");
    }

    @Test
    public void closedWorkload_roundTrips_peakConcurrentIsTheReportedUsers() throws Exception {
        Workload w = Workload.closed(ClosedStep.constantConcurrent(5, Duration.ofSeconds(3)),
                ClosedStep.rampConcurrent(5, 8, Duration.ofSeconds(2)),
                ClosedStep.rampConcurrent(8, 0, Duration.ofSeconds(1)));
        Object cfg = roundTrip(runConfig(w));
        JsonNode json = MAPPER.valueToTree(cfg).path("scenarios").get(0).path("workload");

        assertEquals(json.path("model").asText(), "CLOSED");
        assertStep(json.path("steps").get(0), "constantConcurrent", "users", 5, 3);
        assertStep(json.path("steps").get(1), "rampConcurrent", "fromUsers", 5, 2);
        assertEquals(json.path("steps").get(1).path("toUsers").asInt(), 8);
        assertEquals(json.path("steps").get(2).path("toUsers").asInt(), 0);
        assertEquals(call(cfg, "reportedUsers"), 8);
        assertEquals(call(cfg, "maxDurationSeconds"), 6L + 5 + 30);
    }

    @Test
    public void legacyScenario_hasNoWorkload_andReportsConfiguredUsers() throws Exception {
        LoadScenario s = LoadScenario.named("Legacy").baseUrl("http://127.0.0.1:1").users(4)
                .rampUp(Duration.ofSeconds(2)).hold(Duration.ofSeconds(3)).get("/a");
        Object cfg = roundTrip(newRunConfig(s));

        assertTrue(MAPPER.valueToTree(cfg).path("scenarios").get(0).path("workload").isNull(), "legacy run config has no workload");
        assertEquals(call(cfg, "reportedUsers"), 4);
        assertEquals(call(cfg, "maxDurationSeconds"), 2L + 3 + 5 + 30);
    }

    @Test
    public void simulation_prefixesRequestNames_andMaxDurationIsTheLargestScenarioBudget() throws Exception {
        LoadScenario legacy = LoadScenario.named("Legacy").baseUrl("http://127.0.0.1:1").users(2)
                .rampUp(Duration.ofSeconds(4)).hold(Duration.ofSeconds(50)).get("/a");
        LoadScenario modern = LoadScenario.named("Modern").baseUrl("http://127.0.0.1:2").get("/b")
                .workload(Workload.open(OpenStep.atOnce(3)));
        Class<?> type = Class.forName(RUN_CONFIG);
        Class<?> scenarioType = Class.forName(RUN_CONFIG + "$ScenarioConfig");
        Constructor<?> ctor = scenarioType.getDeclaredConstructor(LoadScenario.class, LoadTestConfig.class,
                String.class);
        ctor.setAccessible(true);
        Object a = ctor.newInstance(legacy, LoadTestConfig.resolveFor(legacy, null, null), "http://127.0.0.1:1");
        Object b = ctor.newInstance(modern, LoadTestConfig.resolveFor(modern, null, null), "http://127.0.0.1:2");
        for (Object sc : new Object[] {a, b}) {
            Field timeout = scenarioType.getField("requestTimeoutSeconds");
            timeout.setAccessible(true);
            timeout.setInt(sc, 5);
        }
        Method forSimulation = type.getDeclaredMethod("forSimulation", String.class, String.class, java.util.List.class);
        forSimulation.setAccessible(true);
        Object cfg = roundTrip(forSimulation.invoke(null, "Shop", "target/loadtest", java.util.List.of(a, b)));
        JsonNode json = MAPPER.valueToTree(cfg);

        assertEquals(json.path("simulationName").asText(), "Shop");
        assertEquals(json.path("scenarios").size(), 2);
        assertEquals(json.path("scenarios").get(0).path("requestPrefix").asText(), "Legacy :: ");
        assertEquals(json.path("scenarios").get(1).path("requestPrefix").asText(), "Modern :: ");
        assertEquals(json.path("scenarios").get(1).path("baseUrl").asText(), "http://127.0.0.1:2");
        // Legacy: 4 + 50 + 1 step x 5 s + 30 = 89; Modern: 0 + 5 + 30 = 35 → the larger one
        assertEquals(call(cfg, "maxDurationSeconds"), 89L);
        assertEquals(call(cfg, "reportedUsers"), 2 + 3, "users of all scenarios together");

        // a single-scenario run keeps unprefixed names
        Object single = roundTrip(forSimulation.invoke(null, "Solo", "target/loadtest", java.util.List.of(b)));
        assertEquals(MAPPER.valueToTree(single).path("scenarios").get(0).path("requestPrefix").asText(), "");
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private static void assertStep(JsonNode step, String type, String field, double value, double seconds) {
        assertEquals(step.path("type").asText(), type, "step " + step);
        assertEquals(step.path(field).asDouble(), value, 1e-9, type + "." + field);
        assertEquals(step.path("seconds").asDouble(), seconds, 1e-9, type + ".seconds");
    }

    private static Object runConfig(Workload w) throws Exception {
        LoadScenario s = LoadScenario.named("Wl").baseUrl("http://127.0.0.1:1").get("/a").workload(w);
        return newRunConfig(s);
    }

    /** {@code new GatlingRunConfig(scenario, config, baseUrl)} with a 5 s request timeout. */
    private static Object newRunConfig(LoadScenario s) throws Exception {
        Class<?> type = Class.forName(RUN_CONFIG);
        Constructor<?> ctor = type.getDeclaredConstructor(LoadScenario.class, LoadTestConfig.class, String.class);
        ctor.setAccessible(true);
        Object cfg = ctor.newInstance(s, LoadTestConfig.resolveFor(s, null, null), "http://127.0.0.1:1");
        Field scenarios = type.getField("scenarios");
        scenarios.setAccessible(true);
        Object scenario = ((java.util.List<?>) scenarios.get(cfg)).get(0);
        Field timeout = scenario.getClass().getField("requestTimeoutSeconds");
        timeout.setAccessible(true);
        timeout.setInt(scenario, 5);
        return cfg;
    }

    /** Serialises and deserialises the run config the way the forked JVM receives it. */
    private static Object roundTrip(Object cfg) throws Exception {
        return MAPPER.readValue(MAPPER.writeValueAsString(cfg), cfg.getClass());
    }

    private static Object call(Object target, String method) throws Exception {
        Method m = target.getClass().getDeclaredMethod(method);
        m.setAccessible(true);
        return m.invoke(target);
    }
}
