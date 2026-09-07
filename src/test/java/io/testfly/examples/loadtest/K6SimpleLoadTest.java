package io.testfly.examples.loadtest;

import io.testfly.loadtest.BaseLoadTest;
import io.testfly.loadtest.LoadTest;
import org.testng.annotations.Test;

/**
 * Simple load test against https://test.k6.io — the public k6 demo site.
 *
 * <p>
 * Demonstrates the simplest usage: a single GET endpoint with annotation-driven
 * configuration. No fluent overrides needed.
 *
 * <h3>Prerequisites</h3>
 * <ul>
 * <li>Sprint 2 (JDK Engine) must be implemented for {@code run()} to work</li>
 * <li>Or add Gatling to the classpath for Sprint 3</li>
 * </ul>
 *
 * <h3>testfly.yml</h3>
 * 
 * <pre>
 * loadtest:
 *   baseUrl: https://test.k6.io
 *   users: 10
 *   rampUp: 5s
 *   hold: 15s
 *   cooldown: 3s
 *   engine: auto
 * </pre>
 */
@LoadTest(baseUrl = "https://test.k6.io", users = 10, rampUp = "5s", hold = "15s")
public class K6SimpleLoadTest extends BaseLoadTest {

    /**
     * Loads the homepage under moderate traffic.
     * Asserts the site responds quickly and without errors.
     */
    @Test(enabled = true) // Enable after Sprint 2 (JDK Engine) is implemented
    public void homepageUnderLoad() {
        load("/")
                .run()
                .assertP95Below(2000) // p95 < 2s (public demo site)
                .assertErrorRateBelow(0.05) // < 5% errors (public site may throttle)
                .assertNoStatus(500); // no server errors
    }

    /**
     * Loads a deeper page — the cart page.
     * Uses method-level annotation to override user count.
     */
    @Test(enabled = true)
    @LoadTest(users = 20, hold = "10s")
    public void cartPageUnderLoad() {
        load("/cart.php")
                .run()
                .assertP95Below(3000)
                .assertErrorRateBelow(0.10);
    }

    /**
     * Functional smoke test coexisting with load tests in the same class.
     * Uses the standard apiClient() — single request, no concurrency.
     */
    @Test(enabled = true)
    public void functionalSmokeTest() {
        apiClient()
                .to("https://test.k6.io")
                .get("/")
                .send()
                .assertStatus(200);
    }
}
