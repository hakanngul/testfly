package io.testfly.examples.loadtest;

import io.testfly.loadtest.BaseLoadTest;
import io.testfly.loadtest.LoadTest;
import io.testfly.loadtest.LoadTestFeeder;
import org.testng.annotations.Test;

import java.time.Duration;

/**
 * Multi-step load test scenario against https://test.k6.io.
 *
 * <p>
 * Demonstrates the full fluent API: named scenarios, multiple steps,
 * data feeders, think time, and per-step assertions.
 *
 * <h3>Scenario flow</h3>
 * <ol>
 * <li>Browse homepage</li>
 * <li>View a product detail page</li>
 * <li>Visit the cart</li>
 * </ol>
 *
 * <p>
 * Each virtual user follows this flow with random think-time between steps,
 * simulating realistic user behavior.
 */
@LoadTest(baseUrl = "https://test.k6.io")
public class K6MultiStepLoadTest extends BaseLoadTest {

    /**
     * Simulates a browsing user flow: homepage → product → cart.
     */
    @Test(enabled = true) // (JDK Engine) is implemented
    public void browsingFlowUnderLoad() {
        loadScenario("Browsing Flow")
                .users(15)
                .rampUp(Duration.ofSeconds(5))
                .hold(Duration.ofSeconds(20))
                .cooldown(Duration.ofSeconds(3))

                .step("Homepage")
                .get("/")
                .check(status().is(200))
                .and()

                .step("Contacts")
                .get("/contacts.php")
                .check(status().is(200))
                .and()

                .step("News")
                .get("/news.php")
                .check(status().is(200))
                .and()

                .thinkTime(500, 2000) // 0.5–2s pause between steps (human-like)

                .run()
                .assertP95Below(3000)
                .assertErrorRateBelow(0.10)
                .assertThroughputAbove(5);
    }

    /**
     * Same flow but with a data feeder — each virtual user gets a different
     * product ID from the sequence.
     */
    @Test(enabled = true)
    public void browsingFlowWithFeeder() {
        loadScenario("Feeder Browsing")
                .users(10)
                .rampUp(Duration.ofSeconds(3))
                .hold(Duration.ofSeconds(15))

                .step("Homepage")
                .get("/")
                .and()

                .step("Product")
                .get("/product.php?id=${productId}")
                .and()

                .feed(LoadTestFeeder.sequence("productId", 1, 1))
                .thinkTime(Duration.ofSeconds(1))

                .run()
                .assertP95Below(3000)
                .assertNoStatus(500);
    }

    /**
     * Heavy load variant — more users, longer hold, stricter assertions.
     * Method-level @LoadTest overrides the class-level baseUrl.
     */
    @Test(enabled = true)
    @LoadTest(users = 30, rampUp = "10s", hold = "30s")
    public void stressTest() {
        loadScenario("Stress Test")
                .step("Homepage")
                .get("/")
                .and()
                .step("News")
                .get("/news.php")
                .and()

                .run()
                .assertP95Below(5000) // relaxed for stress
                .assertErrorRateBelow(0.15) // relaxed for stress
                .assertThroughputAbove(10);
    }
}
