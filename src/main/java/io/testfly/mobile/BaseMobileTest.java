package io.testfly.mobile;

import io.testfly.listeners.SuiteExecutionListener;
import io.testfly.listeners.TestExecutionListener;
import io.testfly.test.support.ApiSupport;
import io.testfly.test.support.ContextSupport;
import io.testfly.test.support.DbSupport;
import io.testfly.test.support.EmailSupport;
import io.testfly.test.support.SoftAssertSupport;
import io.testfly.test.support.StepSupport;
import io.testfly.test.support.TestDataSupport;
import org.testng.annotations.Listeners;

/**
 * Superclass for native mobile tests on TestNG (incubating, ADR 0002). Same framework lifecycle as
 * {@code BaseTest} (reporting, {@code @TestData}, retry, CI gates), but instead of a browser the
 * device from {@code testfly.yml}'s {@code mobile:} section is opened before each test and closed
 * after it. One test can drive the app and check the backend.
 *
 * <pre>
 * public class LoginTest extends BaseMobileTest {
 *
 *     {@literal @}Test
 *     public void signsIn() {
 *         screen().getByTestId("username").fill("ada");
 *         screen().getByRole(MobileRole.BUTTON, "Sign in").tap();
 *         expect(screen().getByText("Welcome, ada")).toBeVisible();
 *         apiClient().get("/me").send().assertStatus(200);
 *     }
 * }
 * </pre>
 */
@MobileTest
@Listeners({
        SuiteExecutionListener.class,
        TestExecutionListener.class
})
public abstract class BaseMobileTest
        implements MobileSupport, SoftAssertSupport, TestDataSupport, ApiSupport, ContextSupport, StepSupport, DbSupport, EmailSupport {

    // device(), screen(), expect(...) via MobileSupport; the rest via io.testfly.test.support.*
}
