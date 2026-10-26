package io.testfly.unit.loadtest;

import io.testfly.loadtest.LoadScenario;
import io.testfly.loadtest.LoadSimulation;
import io.testfly.loadtest.internal.LoadScenarioValidator;
import io.testfly.loadtest.Workload;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.List;

import static io.testfly.unit.loadtest.WorkloadApi.atOnce;
import static io.testfly.unit.loadtest.WorkloadApi.closed;
import static io.testfly.unit.loadtest.WorkloadApi.constantConcurrent;
import static io.testfly.unit.loadtest.WorkloadApi.constantRate;
import static io.testfly.unit.loadtest.WorkloadApi.open;
import static org.testng.Assert.*;

/**
 * Simulation-level validation of ADR 0004 decision 4 (Phase 2), checked before any fork:
 * at least one scenario; unique, non-blank scenario names without {@code #{}, {@code ${} or {@code " :: "};
 * every scenario valid on its own; the combined closed peaks and the combined open peak rates each capped by
 * {@code maxUsers}; Gatling only.
 *
 * <p>Written before the API existed; it goes through {@link SimulationApi}, whose {@code validate} assumes
 * {@code LoadScenarioValidator.validateSimulation(LoadSimulation, String, int)}. A rejection may happen when
 * the scenario is added or when the simulation is validated, so each rejection wraps both in the
 * {@code expectThrows}. No test here runs a load test or opens a socket.
 */
public class LoadSimulationValidationTest {

    private static final String BASE = "http://127.0.0.1:9";

    // ── fluent API shape ─────────────────────────────────────────────────

    @Test
    public void named_keepsScenariosInInsertionOrder_andAccessors() {
        LoadScenario a = scenario("A");
        LoadScenario b = scenario("B");
        Object sim = SimulationApi.named("Shop");
        assertSame(SimulationApi.scenario(sim, a), sim, "scenario(..) is fluent");
        assertSame(SimulationApi.scenario(sim, b), sim);
        assertSame(SimulationApi.baseUrl(sim, BASE), sim, "baseUrl(..) is fluent");
        assertSame(SimulationApi.engine(sim, "gatling"), sim, "engine(..) is fluent");

        assertEquals(SimulationApi.name(sim), "Shop");
        assertEquals(SimulationApi.scenarios(sim), List.of(a, b), "insertion order");
        assertEquals(SimulationApi.baseUrlOf(sim), BASE);
        assertEquals(SimulationApi.engineOf(sim), "gatling");
    }

    @Test
    public void loadTestSupport_loadSimulation_isADefaultMethod() {
        assertTrue(SimulationApi.loadSimulationIsDefaultMethod(),
                "LoadTestSupport.loadSimulation(String) must be a default method (binary compatibility)");
        Object sim = SimulationApi.loadSimulation(new io.testfly.test.support.LoadTestSupport() { }, "ViaMixin");
        assertEquals(SimulationApi.name(sim), "ViaMixin");
    }

    // ── simulation shape ─────────────────────────────────────────────────

    @Test
    public void validSimulation_passes() {
        Object sim = sim(scenario("A"), scenario("B"));
        SimulationApi.validate(sim, "gatling", 1000);
    }

    @Test
    public void emptySimulation_isRejected() {
        Object sim = SimulationApi.named("Empty");
        IllegalArgumentException e = expectThrows(IllegalArgumentException.class,
                () -> SimulationApi.validate(sim, "gatling", 1000));
        assertMessageContains(e, "scenario");
    }

    @Test
    public void duplicateScenarioNames_areRejected() {
        IllegalArgumentException e = expectThrows(IllegalArgumentException.class,
                () -> SimulationApi.validate(sim(scenario("Same"), scenario("Same")), "gatling", 1000));
        assertMessageContains(e, "Same");
    }

    @Test
    public void blankScenarioName_isRejected() {
        expectThrows(IllegalArgumentException.class,
                () -> SimulationApi.validate(sim(scenario("A"), scenario("  ")), "gatling", 1000));
    }

    @Test
    public void nullScenarioName_isRejected() {
        expectThrows(IllegalArgumentException.class,
                () -> SimulationApi.validate(sim(scenario("A"), scenario(null)), "gatling", 1000));
    }

    @Test
    public void scenarioNameWithGatlingExpression_isRejected() {
        expectThrows(IllegalArgumentException.class,
                () -> SimulationApi.validate(sim(scenario("A"), scenario("B#{x}")), "gatling", 1000));
        expectThrows(IllegalArgumentException.class,
                () -> SimulationApi.validate(sim(scenario("A"), scenario("B${x}")), "gatling", 1000));
    }

    @Test
    public void scenarioNameWithRequestNameSeparator_isRejected() {
        IllegalArgumentException e = expectThrows(IllegalArgumentException.class,
                () -> SimulationApi.validate(sim(scenario("A"), scenario("Shop :: Buy")), "gatling", 1000));
        assertMessageContains(e, " :: ");
    }

    @Test
    public void scenarioNameWithColonsButNotTheSeparator_isAccepted() {
        // only the exact " :: " separator is reserved
        SimulationApi.validate(sim(scenario("A"), scenario("ns::Buy")), "gatling", 1000);
    }

    // ── per-scenario rules still apply ───────────────────────────────────

    @Test
    public void scenarioWithoutSteps_isRejected() {
        LoadScenario noSteps = LoadScenario.named("NoSteps").baseUrl(BASE)
                .workload((Workload) open(atOnce(1)));
        IllegalArgumentException e = expectThrows(IllegalArgumentException.class,
                () -> SimulationApi.validate(sim(scenario("A"), noSteps), "gatling", 1000));
        assertMessageContains(e, "NoSteps");
    }

    @Test
    public void perScenarioUserCountStep_isStillCapped() {
        LoadScenario big = scenario("Big", (Workload) open(atOnce(1001)));
        IllegalArgumentException e = expectThrows(IllegalArgumentException.class,
                () -> SimulationApi.validate(sim(scenario("A"), big), "gatling", 1000));
        assertMessageContains(e, "maxUsers");
        assertMessageContains(e, "1000");
    }

    // ── combined maxUsers ────────────────────────────────────────────────

    @Test
    public void combinedClosedPeaks_overMaxUsers_areRejected() {
        LoadScenario a = scenario("ClosedA", (Workload) closed(constantConcurrent(60, Duration.ofSeconds(5))));
        LoadScenario b = scenario("ClosedB", (Workload) closed(constantConcurrent(50, Duration.ofSeconds(5))));
        IllegalArgumentException e = expectThrows(IllegalArgumentException.class,
                () -> SimulationApi.validate(sim(a, b), "gatling", 100));
        assertMessageContains(e, "maxUsers");
        assertMessageContains(e, "100");
    }

    @Test
    public void combinedClosedPeaks_atMaxUsers_pass() {
        LoadScenario a = scenario("ClosedA", (Workload) closed(constantConcurrent(60, Duration.ofSeconds(5))));
        LoadScenario b = scenario("ClosedB", (Workload) closed(constantConcurrent(40, Duration.ofSeconds(5))));
        SimulationApi.validate(sim(a, b), "gatling", 100);
    }

    @Test
    public void combinedOpenPeakRates_overMaxUsers_areRejected() {
        LoadScenario a = scenario("OpenA", (Workload) open(constantRate(60, Duration.ofSeconds(5))));
        LoadScenario b = scenario("OpenB", (Workload) open(constantRate(50, Duration.ofSeconds(5))));
        IllegalArgumentException e = expectThrows(IllegalArgumentException.class,
                () -> SimulationApi.validate(sim(a, b), "gatling", 100));
        assertMessageContains(e, "maxUsers");
        assertMessageContains(e, "100");
    }

    @Test
    public void combinedOpenPeakRates_atMaxUsers_pass() {
        LoadScenario a = scenario("OpenA", (Workload) open(constantRate(60, Duration.ofSeconds(5))));
        LoadScenario b = scenario("OpenB", (Workload) open(constantRate(40, Duration.ofSeconds(5))));
        SimulationApi.validate(sim(a, b), "gatling", 100);
    }

    @Test
    public void closedAndOpenPeaks_areCappedSeparately() {
        // 80 concurrent + 80 users/s: each sum is <= 100, so the mix is accepted
        LoadScenario c = scenario("Closed", (Workload) closed(constantConcurrent(80, Duration.ofSeconds(5))));
        LoadScenario o = scenario("Open", (Workload) open(constantRate(80, Duration.ofSeconds(5))));
        SimulationApi.validate(sim(c, o), "gatling", 100);
    }

    // ── Gatling only ─────────────────────────────────────────────────────

    @Test
    public void jdkEngine_isRejected() {
        Object sim = sim(LoadScenario.named("L1").baseUrl(BASE).users(1).step("l1").get("/l1").and(),
                LoadScenario.named("L2").baseUrl(BASE).users(1).step("l2").get("/l2").and());
        IllegalArgumentException e = expectThrows(IllegalArgumentException.class,
                () -> SimulationApi.validate(sim, "jdk", 1000));
        assertMessageContains(e, "loadSimulation(...) requires the Gatling engine");
    }

    @Test
    public void scenarioEngineConflictingWithSimulation_isRejectedNamingBothEngines() {
        LoadScenario conflicting = scenario("C1");
        conflicting.engine("jdk");
        IllegalArgumentException e = expectThrows(IllegalArgumentException.class,
                () -> SimulationApi.validate(sim(scenario("A"), conflicting), "gatling", 1000));
        assertMessageContains(e, "scenario 'C1' sets engine 'jdk'");
        assertMessageContains(e, "conflicts with the simulation's engine 'gatling'");
    }

    // ── review fixes: names, legacy users, simulation name ───────────────

    @Test
    public void scenarioNameEndingWithSeparatorColons_isRejected() {
        // "A ::" would give the prefix "A :: :: ", which collides with scenario "A"'s request names
        IllegalArgumentException e = expectThrows(IllegalArgumentException.class,
                () -> SimulationApi.validate(sim(scenario("A"), scenario("A ::")), "gatling", 1000));
        assertMessageContains(e, "A ::");
        assertMessageContains(e, "end with ' ::'");
    }

    @Test
    public void nullOrBlankSimulationName_isRejected() {
        for (String name : new String[] {null, "", "  "}) {
            LoadSimulation s = LoadSimulation.named(name).scenario(scenario("A"));
            IllegalArgumentException e = expectThrows(IllegalArgumentException.class,
                    () -> LoadScenarioValidator.validateSimulation(s, "gatling", 1000));
            assertMessageContains(e, "non-blank name");
        }
    }

    @Test
    public void legacyScenarios_resolvedUsersAboveTheCombinedCap_areRejected() {
        // no users(..) on the scenarios: the runner passes the users it resolved (e.g. @LoadTest(users = 60))
        LoadScenario a = LoadScenario.named("LegacyA").baseUrl(BASE).step("a").get("/a").and();
        LoadScenario b = LoadScenario.named("LegacyB").baseUrl(BASE).step("b").get("/b").and();
        LoadSimulation s = LoadSimulation.named("Legacy").scenario(a).scenario(b);

        IllegalArgumentException e = expectThrows(IllegalArgumentException.class,
                () -> LoadScenarioValidator.validateSimulation(s, "gatling", 100, sc -> 60));
        assertMessageContains(e, "maxUsers=100");
        assertMessageContains(e, "120 concurrent users");
        LoadScenarioValidator.validateSimulation(s, "gatling", 100, sc -> 50);
    }

    @Test
    public void legacyScenario_explicitUsersAboveMaxUsers_isRejectedNotClamped() {
        LoadScenario big = LoadScenario.named("Big").baseUrl(BASE).users(101).step("a").get("/a").and();
        IllegalArgumentException e = expectThrows(IllegalArgumentException.class,
                () -> SimulationApi.validate(sim(scenario("A"), big), "gatling", 100));
        assertMessageContains(e, "users(101)");
        assertMessageContains(e, "maxUsers=100");
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private static LoadScenario scenario(String name) {
        return scenario(name, (Workload) open(atOnce(1)));
    }

    private static LoadScenario scenario(String name, Workload workload) {
        return LoadScenario.named(name).baseUrl(BASE).step("home").get("/home").and().workload(workload);
    }

    private static Object sim(LoadScenario... scenarios) {
        Object sim = SimulationApi.named("Sim");
        for (LoadScenario s : scenarios) {
            SimulationApi.scenario(sim, s);
        }
        return sim;
    }

    private static void assertMessageContains(Throwable e, String part) {
        assertTrue(e.getMessage() != null && e.getMessage().contains(part),
                "message must contain '" + part + "', was: " + e.getMessage());
    }
}
