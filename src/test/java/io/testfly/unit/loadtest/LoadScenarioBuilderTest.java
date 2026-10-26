package io.testfly.unit.loadtest;

import io.testfly.loadtest.LoadScenario;
import io.testfly.loadtest.LoadStep;
import io.testfly.loadtest.LoadTestFeeder;
import io.testfly.loadtest.CheckCondition;
import org.testng.annotations.Test;

import java.time.Duration;

import static org.testng.Assert.*;

/**
 * Tests the {@link LoadScenario} fluent builder API.
 */
@Test(singleThreaded = true)
public class LoadScenarioBuilderTest {

    @Test
    public void testSingleStepScenario() {
        LoadScenario s = LoadScenario.single("/api/health");

        assertEquals(s.name(), "/api/health");
        assertEquals(s.steps().size(), 1);
        assertEquals(s.steps().get(0).method(), "GET");
        assertEquals(s.steps().get(0).path(), "/api/health");
    }

    @Test
    public void testNamedScenarioWithSteps() {
        LoadScenario s = LoadScenario.named("Checkout")
                .step("Login").post("/api/auth/login").and()
                .step("Order").post("/api/orders").and();

        assertEquals(s.name(), "Checkout");
        assertEquals(s.steps().size(), 2);
        assertEquals(s.steps().get(0).name(), "Login");
        assertEquals(s.steps().get(0).method(), "POST");
        assertEquals(s.steps().get(1).name(), "Order");
    }

    @Test
    public void testConfigOverrides() {
        LoadScenario s = LoadScenario.named("Test")
                .users(500)
                .rampUp(Duration.ofSeconds(30))
                .hold(Duration.ofMinutes(2))
                .cooldown(Duration.ofSeconds(10))
                .engine("gatling")
                .baseUrl("https://api.test.com");

        assertEquals(s.users(), 500);
        assertEquals(s.rampUp(), Duration.ofSeconds(30));
        assertEquals(s.hold(), Duration.ofMinutes(2));
        assertEquals(s.cooldown(), Duration.ofSeconds(10));
        assertEquals(s.engine(), "gatling");
        assertEquals(s.baseUrl(), "https://api.test.com");
    }

    @Test
    public void testShortcutHttpMethods() {
        LoadScenario s = LoadScenario.named("Multi")
                .get("/api/a")
                .post("/api/b")
                .put("/api/c")
                .delete("/api/d")
                .patch("/api/e");

        assertEquals(s.steps().size(), 5);
        assertEquals(s.steps().get(0).method(), "GET");
        assertEquals(s.steps().get(1).method(), "POST");
        assertEquals(s.steps().get(2).method(), "PUT");
        assertEquals(s.steps().get(3).method(), "DELETE");
        assertEquals(s.steps().get(4).method(), "PATCH");
    }

    @Test
    public void testStepConfiguration() {
        LoadScenario s = LoadScenario.named("Test");
        LoadStep step = s.step("Create User")
                .post("/api/users")
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer token123")
                .body("{\"name\": \"John\"}")
                .queryParam("notify", true)
                .extract("userId", "$.id")
                .check(LoadStep.status().is(201));

        assertEquals(step.name(), "Create User");
        assertEquals(step.method(), "POST");
        assertEquals(step.path(), "/api/users");
        assertEquals(step.headers().size(), 2);
        assertEquals(step.headers().get("Authorization"), "Bearer token123");
        assertEquals(step.body(), "{\"name\": \"John\"}");
        assertEquals(step.queryParams().get("notify"), true);
        assertEquals(step.extractions().get("userId"), "$.id");
        assertEquals(step.checks().size(), 1);
        assertEquals(step.checks().get(0).type(), CheckCondition.Type.STATUS_IS);
    }

    @Test
    public void testFeederAttachment() {
        LoadTestFeeder feeder = LoadTestFeeder.random("userId", 1, 100);
        LoadScenario s = LoadScenario.named("Test").feed(feeder);

        assertSame(s.feeder(), feeder);
    }

    @Test
    public void testThinkTimeRandom() {
        LoadScenario s = LoadScenario.named("Test").thinkTime(500, 2000);

        assertEquals(s.thinkTimeMinMs(), 500);
        assertEquals(s.thinkTimeMaxMs(), 2000);
        assertNull(s.thinkTimeFixed());
    }

    @Test
    public void testThinkTimeFixed() {
        LoadScenario s = LoadScenario.named("Test").thinkTime(Duration.ofSeconds(1));

        assertEquals(s.thinkTimeFixed(), Duration.ofSeconds(1));
        assertEquals(s.thinkTimeMinMs(), -1);
    }

    @Test
    public void testThinkTimeRandomOverridesFixed() {
        LoadScenario s = LoadScenario.named("Test")
                .thinkTime(Duration.ofSeconds(1))
                .thinkTime(100, 500);

        assertNull(s.thinkTimeFixed());
        assertEquals(s.thinkTimeMinMs(), 100);
    }

    @Test
    public void testStepParentLink() {
        LoadScenario s = LoadScenario.named("Test");
        LoadStep step = s.step("A").get("/a");

        // step.run() should delegate to scenario.run()
        assertSame(step.and(), s);
    }

    @Test
    public void testStepsUnmodifiable() {
        LoadScenario s = LoadScenario.named("Test").get("/a");

        assertThrows(UnsupportedOperationException.class, () ->
                s.steps().add(new LoadStep("hack")));
    }

    // ── CheckCondition builder kinds (1.1.0) ─────────────────────────────

    @Test
    public void testStatusBuilderKinds() {
        CheckCondition is = LoadStep.status().is(201);
        assertEquals(is.type(), CheckCondition.Type.STATUS_IS);
        assertEquals(is.expected(), 201);

        CheckCondition in = LoadStep.status().in(200, 204);
        assertEquals(in.type(), CheckCondition.Type.STATUS_IN);
        assertEquals((int[]) in.expected(), new int[] {200, 204});

        CheckCondition re = LoadStep.status().matches("ok");
        assertEquals(re.type(), CheckCondition.Type.BODY_MATCHES);
        assertEquals(re.expected(), "ok");
    }

    @Test(expectedExceptions = IllegalStateException.class,
            expectedExceptionsMessageRegExp = "exists\\(\\) is only valid for jsonPath\\(\\.\\.\\.\\)")
    public void testStatusExistsThrows() {
        LoadStep.status().exists();
    }

    @Test
    public void testJsonPathBuilderKinds() {
        CheckCondition eq = LoadStep.jsonPath("$.id").is(5);
        assertEquals(eq.type(), CheckCondition.Type.JSON_EQUALS);
        assertEquals(eq.expected(), 5);
        assertEquals(eq.target(), "$.id");

        CheckCondition in = LoadStep.jsonPath("$.code").in(1, 2);
        assertEquals(in.type(), CheckCondition.Type.JSON_IN);
        assertEquals((int[]) in.expected(), new int[] {1, 2});
        assertEquals(in.target(), "$.code");

        CheckCondition ex = LoadStep.jsonPath("$.id").exists();
        assertEquals(ex.type(), CheckCondition.Type.JSON_EXISTS);
        assertEquals(ex.target(), "$.id");

        // documented contract: matches() is a whole-body regex on both builders
        CheckCondition re = LoadStep.jsonPath("$.ok").matches("true");
        assertEquals(re.type(), CheckCondition.Type.BODY_MATCHES);
    }

    @Test
    public void testCheckTypeConstantsAreAppendedNotReordered() {
        CheckCondition.Type[] t = CheckCondition.Type.values();
        assertEquals(java.util.Arrays.stream(t).map(Enum::name).toList(), java.util.List.of(
                "STATUS_IS", "STATUS_IN", "JSON_EXISTS", "HEADER_PRESENT", "BODY_MATCHES", "JSON_EQUALS", "JSON_IN"));
    }
}
