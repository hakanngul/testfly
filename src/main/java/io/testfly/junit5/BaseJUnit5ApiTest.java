package io.testfly.junit5;

import io.testfly.api.TestFlyApi;
import io.testfly.test.NoBrowser;
import io.testfly.test.support.ApiSupport;
import io.testfly.test.support.ContextSupport;
import io.testfly.test.support.DbSupport;
import io.testfly.test.support.EmailSupport;
import io.testfly.test.support.SoftAssertSupport;
import io.testfly.test.support.StepSupport;
import io.testfly.test.support.TestDataSupport;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Base class for pure REST API tests with JUnit 5 — the JUnit 5 equivalent of {@code BaseApiTest}.
 *
 * <p>Annotated with {@code @NoBrowser} so that no WebDriver session is ever created.
 * Provides {@code apiClient()}, {@code apiGet/Post/Put/Patch/Delete}, {@code softAssert()},
 * {@code getTestData()}, {@code ctx()}/{@code suiteCtx()}, {@code db()}, {@code mailbox()}/{@code to()},
 * and {@code step()}.
 *
 * <pre>
 * class UserApiTest extends BaseJUnit5ApiTest {
 *
 *     {@literal @}Test
 *     void createUser() {
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
@ExtendWith(TestFlyExtension.class)
@NoBrowser
public abstract class BaseJUnit5ApiTest
        implements SoftAssertSupport, TestDataSupport, ApiSupport, ContextSupport, StepSupport, DbSupport, EmailSupport {

    // softAssert(), getTestData(), apiClient(), ctx()/suiteCtx(), step(), db(), mailbox()/to() — via io.testfly.test.support.*
}
