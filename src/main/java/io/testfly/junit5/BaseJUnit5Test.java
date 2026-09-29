package io.testfly.junit5;

import io.testfly.api.TestFlyApi;
import io.testfly.test.support.AccessibilitySupport;
import io.testfly.test.support.ActionSupport;
import io.testfly.test.support.ApiSupport;
import io.testfly.test.support.AssertionSupport;
import io.testfly.test.support.BrowserSupport;
import io.testfly.test.support.ClockSupport;
import io.testfly.test.support.ContextSupport;
import io.testfly.test.support.DbSupport;
import io.testfly.test.support.EmailSupport;
import io.testfly.test.support.LocatorSupport;
import io.testfly.test.support.NavigationSupport;
import io.testfly.test.support.PerformanceSupport;
import io.testfly.test.support.SessionSupport;
import io.testfly.test.support.SoftAssertSupport;
import io.testfly.test.support.StepSupport;
import io.testfly.test.support.TestDataSupport;
import io.testfly.test.support.VisualSupport;
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
public abstract class BaseJUnit5Test implements LocatorSupport, AssertionSupport, ActionSupport, SessionSupport, SoftAssertSupport,
        TestDataSupport, ApiSupport, ContextSupport, NavigationSupport,
        BrowserSupport, VisualSupport, DbSupport, EmailSupport, AccessibilitySupport,
        PerformanceSupport, ClockSupport, StepSupport {

    // ----------------------------------------------------------
    // Navigation (open / getDriver / getWait) — via NavigationSupport
    // Fluent Locator API (find / $), Accessibility locators (getBy*),
    // Web-First Assertions (assertThat) — via support interfaces
    // Multi-session helpers session()/withSession() — via SessionSupport
    // Step logging — via StepSupport
    // Soft assertions, test data, API, context, browser, visual, DB, email, a11y —
    // via support interfaces
    // Performance (assertPerformance/collectPerformance) — via PerformanceSupport
    // Clock mocking (clock) — via ClockSupport
    // ----------------------------------------------------------
    // find(String/By), $(String/By),
    // getByRole/Text/Label/Placeholder/TestId/AltText/Title
    // assertThat(By/Locator), session()/withSession(), step(), softAssert(),
    // getTestData(), apiClient(), ctx()/suiteCtx(),
    // networkMock()/localStorage()/cookies()/mockLocation()/clipboard(),
    // assertScreenshot()/emulateDevice(), db(), mailbox()/to(), accessibility(),
    // open(), open(String), getDriver(), getWait(), assertPerformance(),
    // collectPerformance(), clock()
    // are provided as default methods in io.testfly.test.support.*.
}
