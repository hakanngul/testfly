package io.testfly.loadtest;

import io.testfly.api.TestFlyApi;
import io.testfly.listeners.SuiteExecutionListener;
import io.testfly.listeners.TestExecutionListener;
import io.testfly.test.support.ApiSupport;
import io.testfly.test.support.ContextSupport;
import io.testfly.test.support.LoadTestSupport;
import io.testfly.test.support.SoftAssertSupport;
import io.testfly.test.support.StepSupport;
import io.testfly.test.support.TestDataSupport;
import org.testng.annotations.Listeners;

/**
 * Base class for load tests — the load-testing counterpart of {@code BaseApiTest}.
 *
 * <p>Provides the same framework lifecycle (reporting, {@code @TestData}, retry,
 * CI gates, hooks) but <b>no browser is started</b>. All load-test capabilities
 * come from the {@link LoadTestSupport} mixin.
 *
 * <h3>Three configuration layers</h3>
 * <ol>
 *   <li>{@code testfly.yml → loadtest:} — project-wide defaults</li>
 *   <li>{@code @LoadTest} on the class — test-class overrides</li>
 *   <li>{@code @LoadTest} on the method or fluent {@code .users()} — per-test overrides</li>
 * </ol>
 *
 * <pre>
 * {@literal @}LoadTest(users = 200, rampUp = "30s", hold = "2m")
 * public class ProductLoadTest extends BaseLoadTest {
 *
 *     {@literal @}Test
 *     public void searchUnderLoad() {
 *         load("/api/products?search=laptop")
 *             .assertP95Below(300)
 *             .assertErrorRateBelow(0.02);
 *     }
 *
 *     {@literal @}Test
 *     {@literal @}LoadTest(users = 500)
 *     public void heavySearch() {
 *         load("/api/products?search=phone")
 *             .assertThroughputAbove(1000);
 *     }
 *
 *     {@literal @}Test
 *     public void checkoutFlow() {
 *         loadScenario("Checkout Flow")
 *             .users(300)
 *             .step("Login")
 *                 .post("/api/auth/login")
 *                 .body(Map.of("user", "${username}"))
 *                 .extract("token", "$.accessToken")
 *             .step("Order")
 *                 .post("/api/orders")
 *                 .header("Authorization", "Bearer ${token}")
 *             .feed(LoadTestFeeder.csv("users.csv"))
 *             .thinkTime(500, 2000)
 *             .run()
 *             .assertP95Below(500);
 *     }
 * }
 * </pre>
 *
 * <p>Functional API tests can coexist in the same class via {@link ApiSupport}:
 *
 * <pre>
 * {@literal @}Test
 * public void healthCheck() {
 *     apiClient().get("/api/health").send().assertStatus(200);
 * }
 * </pre>
 */
@TestFlyApi(since = "1.1.0")
@Listeners({
        SuiteExecutionListener.class,
        TestExecutionListener.class
})
public abstract class BaseLoadTest implements
        LoadTestSupport,
        ApiSupport,
        ContextSupport,
        SoftAssertSupport,
        StepSupport,
        TestDataSupport {

    // load(), loadScenario(), lastLoadMetrics() — via LoadTestSupport
    // apiClient(), apiGet/Post/Put/Patch/Delete — via ApiSupport
    // ctx(), suiteCtx() — via ContextSupport
    // softAssert() — via SoftAssertSupport
    // step() — via StepSupport
    // getTestData() — via TestDataSupport
}
