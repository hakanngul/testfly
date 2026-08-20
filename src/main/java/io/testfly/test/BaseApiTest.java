package io.testfly.test;

import io.testfly.api.TestFlyApi;
import io.testfly.assertion.SoftAssertionCollector;
import io.testfly.assertion.SoftAssertions;
import io.testfly.client.ApiClient;
import io.testfly.context.ScenarioContext;
import io.testfly.context.SuiteContext;
import io.testfly.listeners.SuiteExecutionListener;
import io.testfly.listeners.TestExecutionListener;
import io.testfly.testdata.TestDataStore;
import org.testng.annotations.Listeners;

import java.util.Map;

/**
 * BaseApiTest is the mandatory superclass for pure API tests.
 *
 * Same framework lifecycle as {@link BaseTest} — reporting, {@code @TestData},
 * retry, CI gates — but no browser is started.
 *
 * <pre>
 * public class UserApiTest extends BaseApiTest {
 *
 *     {@literal @}Test
 *     public void createUser() {
 *         ApiResponse res = apiClient().post("/api/users")
 *                 .body(Map.of("name", "John", "email", "john@example.com"))
 *                 .send();
 *         res.assertStatus(201);
 *         suiteCtx().set("createdUserId", res.json("$.id"));
 *     }
 * }
 * </pre>
 */
@TestFlyApi(since = "1.1.0")
@Listeners({
        SuiteExecutionListener.class,
        TestExecutionListener.class
})
public abstract class BaseApiTest {

    private static final ScenarioContext SCENARIO_CTX = new ScenarioContext();
    private static final SuiteContext    SUITE_CTX    = new SuiteContext();

    /**
     * Returns a new {@link ApiClient} for making HTTP requests.
     * Base URL is read from {@code execution.baseUrl} or {@code api.baseUrl} in {@code testfly.yml}.
     */
    protected ApiClient apiClient() {
        return ApiClient.create();
    }

    /** Returns the in-test (thread-local) context store. Cleared after each test. */
    protected ScenarioContext ctx() {
        return SCENARIO_CTX;
    }

    /** Returns the suite-scoped (global) context store. Survives between tests. */
    protected SuiteContext suiteCtx() {
        return SUITE_CTX;
    }

    /**
     * Returns the test data loaded by {@code @TestData} for the current test.
     */
    protected Map<String, Object> getTestData() {
        return TestDataStore.get();
    }

    /**
     * Typed test data retrieval.
     *
     * <pre>
     * String username = getTestData("username", String.class);
     * int    age      = getTestData("age", Integer.class);
     * </pre>
     */
    @SuppressWarnings("unchecked")
    protected <T> T getTestData(String key, Class<T> type) {
        Object value = TestDataStore.get().get(key);
        if (value == null) throw new IllegalStateException(
            "[TestData] Key not found: '" + key + "'");
        return (T) value;
    }

    /** Soft assertion collector — failures are reported all-at-once at test end. */
    protected SoftAssertionCollector softAssert() {
        return SoftAssertions.get();
    }
}
