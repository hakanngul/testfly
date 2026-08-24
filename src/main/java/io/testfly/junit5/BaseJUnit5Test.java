package io.testfly.junit5;

import io.testfly.api.TestFlyApi;
import io.testfly.test.support.AccessibilitySupport;
import io.testfly.test.support.ApiSupport;
import io.testfly.test.support.AssertionSupport;
import io.testfly.test.support.DbSupport;
import io.testfly.test.support.EmailSupport;
import io.testfly.test.support.LocatorSupport;
import io.testfly.test.support.NavigationSupport;
import io.testfly.test.support.SessionSupport;
import io.testfly.test.support.StepSupport;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Optional base class for JUnit 5 tests — the JUnit 5 equivalent of {@code BaseTest}.
 *
 * <p>Extend this class to get the same convenience API available in TestNG {@code BaseTest}:
 * <pre>
 * class LoginTest extends BaseJUnit5Test {
 *
 *     {@literal @}Test
 *     void validLogin() {
 *         open();
 *         find("input#username").type("admin");
 *         find("input#password").type("secret");
 *         find("button[type='submit']").click();
 *         assertThat(By.id("dashboard")).isVisible();
 *     }
 * }
 * </pre>
 *
 * <p>Alternatively use {@link EnableTestFly} on your own base class and inject
 * {@code WebDriver} as a test method parameter.
 */
@TestFlyApi(since = "1.9.0")
@ExtendWith(TestFlyExtension.class)
public abstract class BaseJUnit5Test implements LocatorSupport, AssertionSupport, SessionSupport, StepSupport,
        NavigationSupport, DbSupport, EmailSupport, AccessibilitySupport, ApiSupport {

    // ----------------------------------------------------------
    // Navigation (open / getDriver / getWait) — via NavigationSupport
    // Fluent Locator API (find / $), Accessibility locators (getBy*),
    // Web-First Assertions (assertThat) — via support interfaces
    // Multi-session helpers session()/withSession() — via SessionSupport
    // Step logging — via StepSupport
    // ----------------------------------------------------------
    // All locator/assertion/session/step/navigation methods are provided as default methods
    // in io.testfly.test.support.*. This gives BaseJUnit5Test full parity with
    // BaseTest (getByRole, getByText, etc.) while keeping the delegation in a single place.

    // db(), mailbox()/to(), accessibility() — via DbSupport, EmailSupport, AccessibilitySupport
    // apiClient(), apiGet/Post/Put/Patch/Delete() — via ApiSupport
}
