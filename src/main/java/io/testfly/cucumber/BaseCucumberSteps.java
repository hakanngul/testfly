package io.testfly.cucumber;

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
import io.cucumber.java.Scenario;

/**
 * Abstract base class for Cucumber step definition classes.
 *
 * <p>
 * Provides the same driver-access conveniences as {@code BaseTest}:
 * 
 * <pre>
 * public class LoginSteps extends BaseCucumberSteps {
 *
 *     {@literal @}Given("the user is on the login page")
 *     public void onLoginPage() { open(); }
 *
 *     {@literal @}When("they login as {string}")
 *     public void login(String username) {
 *         new LoginPage(getDriver()).login(username, "secret");
 *     }
 *
 *     {@literal @}Then("the dashboard is visible")
 *     public void dashboardVisible() {
 *         assertThat(By.id("dashboard")).isVisible();
 *     }
 * }
 * </pre>
 *
 * <p>
 * Thread-safe: all state access goes through {@code DriverManager} and
 * {@code CucumberContext} ThreadLocals.
 */
@TestFlyApi(since = "1.9.0")
public abstract class BaseCucumberSteps
        implements LocatorSupport, AssertionSupport, ActionSupport, SessionSupport, SoftAssertSupport,
        TestDataSupport, ApiSupport, ContextSupport, NavigationSupport,
        BrowserSupport, VisualSupport, DbSupport, EmailSupport, AccessibilitySupport,
        PerformanceSupport, ClockSupport {

    // ----------------------------------------------------------
    // Navigation (open / getDriver / getWait) — via NavigationSupport
    // Fluent Locator API (find / $), Accessibility locators (getBy*),
    // Web-First Assertions (assertThat) — via support interfaces
    // API helpers — via ApiSupport (apiClient, apiGet/Post/Put/Patch/Delete)
    // ----------------------------------------------------------
    // find(String/By), $(String/By),
    // getByRole/Text/Label/Placeholder/TestId/AltText/Title
    // assertThat(By/Locator), step(), open(), open(String), getDriver(), getWait()
    // apiClient(), apiGet(), apiPost(), apiPut(), apiPatch(), apiDelete()
    // are provided as default methods in io.testfly.test.support.*.

    /**
     * Returns the current Cucumber {@link Scenario} — useful for attaching
     * data or reading tags inside a step.
     */
    protected Scenario getScenario() {
        Scenario scenario = CucumberContext.getScenario();
        if (scenario == null) {
            throw new IllegalStateException(
                    "[BaseCucumberSteps] No active scenario on this thread. " +
                            "Ensure 'io.testfly.cucumber' is in the @CucumberOptions glue.");
        }
        return scenario;
    }
}
