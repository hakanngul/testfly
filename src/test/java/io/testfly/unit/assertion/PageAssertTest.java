package io.testfly.unit.assertion;

import io.testfly.assertion.PageAssert;
import io.testfly.assertion.SeleniumAssert;
import io.testfly.assertion.SoftAssertions;
import io.testfly.config.TestFlyConfig;
import io.testfly.driver.DriverManager;
import io.testfly.internal.TestFlyContext;
import io.testfly.steps.StepLogger;
import org.mockito.MockedStatic;
import org.openqa.selenium.Alert;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoAlertPresentException;
import org.openqa.selenium.WebDriver;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.time.Duration;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Unit tests for {@link PageAssert} polling and assertion behaviour.
 * All WebDriver interactions are mocked — no real browser is required.
 */
@Test(singleThreaded = true)
public class PageAssertTest {

    private WebDriver mockDriver;
    private MockedStatic<DriverManager> driverManagerMock;
    private MockedStatic<TestFlyContext> contextMock;
    private MockedStatic<StepLogger> stepLoggerMock;

    @BeforeMethod
    public void setUp() {
        mockDriver = mock(WebDriver.class, withSettings().extraInterfaces(JavascriptExecutor.class));

        driverManagerMock = mockStatic(DriverManager.class);
        driverManagerMock.when(DriverManager::getDriver).thenReturn(mockDriver);

        TestFlyConfig.Timeouts timeouts = new TestFlyConfig.Timeouts();
        timeouts.setExplicit(1); // 1-second timeout for fast negative tests
        TestFlyConfig config = new TestFlyConfig();
        config.setTimeouts(timeouts);

        contextMock = mockStatic(TestFlyContext.class);
        contextMock.when(TestFlyContext::getConfig).thenReturn(config);

        stepLoggerMock = mockStatic(StepLogger.class);
        SoftAssertions.clear();
    }

    @AfterMethod
    public void tearDown() {
        if (driverManagerMock != null)
            driverManagerMock.close();
        if (contextMock != null)
            contextMock.close();
        if (stepLoggerMock != null)
            stepLoggerMock.close();
        SoftAssertions.clear();
    }

    // ------------------------------------------------------------------
    // Title Assertions
    // ------------------------------------------------------------------

    @Test
    public void hasTitle_passesWhenTitleMatches() {
        when(mockDriver.getTitle()).thenReturn("Dashboard");

        PageAssert assertion = SeleniumAssert.assertThat(mockDriver).hasTitle("Dashboard");
        assertNotNull(assertion);
    }

    @Test
    public void hasTitle_failsWhenTitleDoesNotMatch() {
        when(mockDriver.getTitle()).thenReturn("Login");

        try {
            SeleniumAssert.assertThat(mockDriver)
                    .within(Duration.ofMillis(200))
                    .hasTitle("Dashboard");
            fail("Expected AssertionError for mismatched title");
        } catch (AssertionError e) {
            assertTrue(e.getMessage().contains("Expected page title to be [Dashboard]"),
                    "Error message should mention expected title: " + e.getMessage());
            assertTrue(e.getMessage().contains("actual title: [Login]"),
                    "Error message should mention actual title: " + e.getMessage());
        }
    }

    @Test
    public void titleContains_passesWhenSubstringPresent() {
        when(mockDriver.getTitle()).thenReturn("TestFly — Automated Testing");

        PageAssert assertion = SeleniumAssert.assertThat(mockDriver).titleContains("Automated");
        assertNotNull(assertion);
    }

    @Test
    public void titleContains_failsWhenSubstringAbsent() {
        when(mockDriver.getTitle()).thenReturn("Login Page");

        try {
            SeleniumAssert.assertThat(mockDriver)
                    .within(Duration.ofMillis(200))
                    .titleContains("Dashboard");
            fail("Expected AssertionError for missing title substring");
        } catch (AssertionError e) {
            assertTrue(e.getMessage().contains("Expected page title to contain [Dashboard]"),
                    "Error message: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // URL Assertions
    // ------------------------------------------------------------------

    @Test
    public void hasUrl_passesWhenUrlMatches() {
        when(mockDriver.getCurrentUrl()).thenReturn("https://example.com/products");

        PageAssert assertion = SeleniumAssert.assertThat(mockDriver).hasUrl("https://example.com/products");
        assertNotNull(assertion);
    }

    @Test
    public void hasUrl_failsWhenUrlDoesNotMatch() {
        when(mockDriver.getCurrentUrl()).thenReturn("https://example.com/login");

        try {
            SeleniumAssert.assertThat(mockDriver)
                    .within(Duration.ofMillis(200))
                    .hasUrl("https://example.com/products");
            fail("Expected AssertionError for mismatched URL");
        } catch (AssertionError e) {
            assertTrue(e.getMessage().contains("Expected page URL to be [https://example.com/products]"),
                    "Error message: " + e.getMessage());
        }
    }

    @Test
    public void urlContains_passesWhenFragmentPresent() {
        when(mockDriver.getCurrentUrl()).thenReturn("https://example.com/inventory/cart");

        PageAssert assertion = SeleniumAssert.assertThat(mockDriver).urlContains("cart");
        assertNotNull(assertion);
    }

    @Test
    public void urlContains_failsWhenFragmentAbsent() {
        when(mockDriver.getCurrentUrl()).thenReturn("https://example.com/inventory");

        try {
            SeleniumAssert.assertThat(mockDriver)
                    .within(Duration.ofMillis(200))
                    .urlContains("cart");
            fail("Expected AssertionError for missing URL fragment");
        } catch (AssertionError e) {
            assertTrue(e.getMessage().contains("Expected page URL to contain [cart]"),
                    "Error message: " + e.getMessage());
        }
    }

    @Test
    public void urlMatches_passesWhenRegexMatches() {
        when(mockDriver.getCurrentUrl()).thenReturn("https://example.com/orders/12345");

        PageAssert assertion = SeleniumAssert.assertThat(mockDriver).urlMatches(".*orders/\\d+");
        assertNotNull(assertion);
    }

    // ------------------------------------------------------------------
    // Modifiers & Soft Assertions
    // ------------------------------------------------------------------

    @Test
    public void as_includesCustomDescriptionInFailure() {
        when(mockDriver.getTitle()).thenReturn("Login");

        try {
            SeleniumAssert.assertThat(mockDriver)
                    .as("Main page header check")
                    .within(Duration.ofMillis(200))
                    .hasTitle("Dashboard");
            fail("Expected AssertionError");
        } catch (AssertionError e) {
            assertTrue(e.getMessage().contains("[Main page header check]"),
                    "Error message should contain custom description: " + e.getMessage());
        }
    }

    @Test
    public void softAssert_collectsFailureWithoutThrowing() {
        when(mockDriver.getTitle()).thenReturn("Login");

        // Should not throw immediately
        SeleniumAssert.softAssert(mockDriver)
                .within(Duration.ofMillis(200))
                .hasTitle("Dashboard");

        assertTrue(SoftAssertions.get().hasFailed(), "Soft assertion should be recorded as failed");
        assertEquals(SoftAssertions.get().getFailures().size(), 1);
        assertTrue(SoftAssertions.get().getFailures().get(0).contains("Expected page title to be [Dashboard]"));
    }

    @Test
    public void softly_chainModifierCollectsFailure() {
        when(mockDriver.getCurrentUrl()).thenReturn("https://example.com/login");

        // Should not throw immediately
        SeleniumAssert.assertThat(mockDriver)
                .within(Duration.ofMillis(200))
                .softly()
                .urlContains("dashboard");

        assertTrue(SoftAssertions.get().hasFailed());
    }

    @Test
    public void assertThatPage_usesCurrentDriver() {
        when(mockDriver.getTitle()).thenReturn("Sauce Demo");

        PageAssert assertion = SeleniumAssert.assertThatPage().hasTitle("Sauce Demo");
        assertNotNull(assertion);
    }

    // ------------------------------------------------------------------
    // Alerts, cookies, storage, windows
    // ------------------------------------------------------------------

    private PageAssert expectPage() {
        return SeleniumAssert.assertThat(mockDriver).within(Duration.ofMillis(300));
    }

    private WebDriver.TargetLocator targetLocator() {
        WebDriver.TargetLocator target = mock(WebDriver.TargetLocator.class);
        when(mockDriver.switchTo()).thenReturn(target);
        return target;
    }

    private WebDriver.Options options() {
        WebDriver.Options options = mock(WebDriver.Options.class);
        when(mockDriver.manage()).thenReturn(options);
        return options;
    }

    @Test
    public void hasAlert_and_hasAlertText_passWhenAlertOpen() {
        Alert alert = mock(Alert.class);
        when(alert.getText()).thenReturn("Saved!");
        when(targetLocator().alert()).thenReturn(alert);

        expectPage().hasAlert().hasAlertText("Saved!");
    }

    @Test(expectedExceptions = AssertionError.class)
    public void hasAlert_failsWhenNoAlert() {
        when(targetLocator().alert()).thenThrow(new NoAlertPresentException());
        expectPage().hasAlert();
    }

    @Test
    public void hasNoAlert_passesWhenNoAlert() {
        when(targetLocator().alert()).thenThrow(new NoAlertPresentException());
        expectPage().hasNoAlert();
    }

    @Test(expectedExceptions = AssertionError.class)
    public void hasNoAlert_failsWhenAlertOpen() {
        when(targetLocator().alert()).thenReturn(mock(Alert.class));
        expectPage().hasNoAlert();
    }

    @Test
    public void cookieAssertions_pass() {
        WebDriver.Options options = options();
        when(options.getCookieNamed("session")).thenReturn(new Cookie("session", "abc"));
        when(options.getCookieNamed("tracking")).thenReturn(null);

        expectPage().hasCookie("session").hasCookie("session", "abc").doesNotHaveCookie("tracking");
    }

    @Test(expectedExceptions = AssertionError.class)
    public void hasCookie_wrongValue_fails() {
        when(options().getCookieNamed("session")).thenReturn(new Cookie("session", "abc"));
        expectPage().hasCookie("session", "xyz");
    }

    @Test
    public void hasLocalStorageItem_passesWhenValueMatches() {
        when(((JavascriptExecutor) mockDriver).executeScript(anyString(), org.mockito.ArgumentMatchers.<Object>any()))
                .thenReturn("dark");
        expectPage().hasLocalStorageItem("theme").hasLocalStorageItem("theme", "dark");
    }

    @Test(expectedExceptions = AssertionError.class)
    public void hasLocalStorageItem_failsWhenMissing() {
        when(((JavascriptExecutor) mockDriver).executeScript(anyString(), org.mockito.ArgumentMatchers.<Object>any()))
                .thenReturn(null);
        expectPage().hasLocalStorageItem("theme");
    }

    @Test
    public void hasWindowCount_passesWhenCountMatches() {
        when(mockDriver.getWindowHandles()).thenReturn(java.util.Set.of("a", "b"));
        expectPage().hasWindowCount(2);
    }

    @Test(expectedExceptions = AssertionError.class)
    public void hasWindowCount_failsWhenCountDiffers() {
        when(mockDriver.getWindowHandles()).thenReturn(java.util.Set.of("a"));
        expectPage().hasWindowCount(2);
    }

    // ------------------------------------------------------------------
    // Negation
    // ------------------------------------------------------------------

    @Test
    public void not_urlContains_passesWhenFragmentAbsent() {
        when(mockDriver.getCurrentUrl()).thenReturn("https://example.com/inventory");
        expectPage().not().urlContains("login");
    }

    @Test
    public void not_urlContains_failsWhenFragmentPresent() {
        when(mockDriver.getCurrentUrl()).thenReturn("https://example.com/login");
        when(mockDriver.getTitle()).thenReturn("Login");

        AssertionError e = org.testng.Assert.expectThrows(AssertionError.class,
                () -> expectPage().not().urlContains("login"));
        assertTrue(e.getMessage().contains("Expected NOT page URL to contain [login]"), e.getMessage());
        assertTrue(e.getMessage().contains("condition still met"), e.getMessage());
    }

    @Test
    public void not_hasTitle_passesWhenTitleDiffers() {
        when(mockDriver.getTitle()).thenReturn("Dashboard");
        expectPage().not().hasTitle("Login");
    }

    @Test
    public void not_negatesOnlyTheNextAssertion() {
        when(mockDriver.getTitle()).thenReturn("Dashboard");
        when(mockDriver.getCurrentUrl()).thenReturn("https://example.com/inventory");

        expectPage().not().hasTitle("Login").urlContains("inventory");
        org.testng.Assert.expectThrows(AssertionError.class,
                () -> expectPage().not().hasTitle("Login").hasTitle("Login"));
    }

    @Test
    public void not_hasAlert_passesWhenNoAlert() {
        when(targetLocator().alert()).thenThrow(new NoAlertPresentException());
        expectPage().not().hasAlert();
    }

    @Test
    public void not_hasCookie_failsWhenCookiePresent() {
        when(options().getCookieNamed("session")).thenReturn(new Cookie("session", "abc"));
        org.testng.Assert.expectThrows(AssertionError.class, () -> expectPage().not().hasCookie("session"));
    }

    @Test
    public void not_softMode_collectsNegatedFailure() {
        when(mockDriver.getCurrentUrl()).thenReturn("https://example.com/login");

        expectPage().softly().not().urlContains("login");

        assertTrue(SoftAssertions.get().hasFailed());
        assertTrue(SoftAssertions.get().getFailures().get(0).contains("Expected NOT page URL to contain [login]"));
    }

    @Test
    public void not_marksStepAsNegated() {
        when(mockDriver.getCurrentUrl()).thenReturn("https://example.com/inventory");

        expectPage().not().urlContains("login");

        stepLoggerMock.verify(() -> StepLogger.step("[not] Assert page URL contains: login"));
    }

    private static void assertEquals(Object actual, Object expected) {
        org.testng.Assert.assertEquals(actual, expected);
    }
}
