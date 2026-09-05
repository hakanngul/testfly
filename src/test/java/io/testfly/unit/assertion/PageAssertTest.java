package io.testfly.unit.assertion;

import io.testfly.assertion.PageAssert;
import io.testfly.assertion.SeleniumAssert;
import io.testfly.assertion.SoftAssertions;
import io.testfly.config.TestFlyConfig;
import io.testfly.driver.DriverManager;
import io.testfly.internal.TestFlyContext;
import io.testfly.steps.StepLogger;
import org.mockito.MockedStatic;
import org.openqa.selenium.WebDriver;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.time.Duration;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertSame;
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
        mockDriver = mock(WebDriver.class);

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
        if (driverManagerMock != null) driverManagerMock.close();
        if (contextMock != null) contextMock.close();
        if (stepLoggerMock != null) stepLoggerMock.close();
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

    private static void assertEquals(Object actual, Object expected) {
        org.testng.Assert.assertEquals(actual, expected);
    }
}
