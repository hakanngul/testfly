package io.testfly.test;

import io.testfly.api.TestFlyApi;
import io.testfly.listeners.SuiteExecutionListener;
import io.testfly.listeners.TestExecutionListener;
import io.testfly.test.support.AccessibilitySupport;
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
import io.testfly.test.support.TestDataSupport;
import io.testfly.test.support.VisualSupport;
import org.testng.annotations.Listeners;

/**
 * BaseTest is the mandatory superclass for all TestFly tests.
 *
 * Responsibilities:
 * - Provide access to the framework-managed WebDriver
 *
 * Rules:
 * - Tests must NOT create or quit WebDriver
 * - Tests must NOT manage waits or retries
 */
@TestFlyApi(since = "0.1.0")
@Listeners({
                SuiteExecutionListener.class,
                TestExecutionListener.class
})
public abstract class BaseTest implements LocatorSupport, AssertionSupport, SessionSupport, SoftAssertSupport,
                TestDataSupport, ApiSupport, ContextSupport, NavigationSupport,
                BrowserSupport, VisualSupport, DbSupport, EmailSupport, AccessibilitySupport,
                PerformanceSupport, ClockSupport {

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
