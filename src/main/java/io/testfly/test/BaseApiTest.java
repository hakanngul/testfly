package io.testfly.test;

import io.testfly.api.TestFlyApi;
import io.testfly.listeners.SuiteExecutionListener;
import io.testfly.listeners.TestExecutionListener;
import io.testfly.test.support.ApiSupport;
import io.testfly.test.support.ContextSupport;
import io.testfly.test.support.SoftAssertSupport;
import io.testfly.test.support.TestDataSupport;
import org.testng.annotations.Listeners;

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
public abstract class BaseApiTest implements SoftAssertSupport, TestDataSupport, ApiSupport, ContextSupport {

    // softAssert(), getTestData(), apiClient(), ctx()/suiteCtx() — via io.testfly.test.support.*
}
