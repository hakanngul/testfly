package io.testfly.unit.assertion;

import io.testfly.assertion.LocatorAssert;
import io.testfly.assertion.SeleniumAssert;
import io.testfly.assertion.SoftAssertions;
import io.testfly.config.TestFlyConfig;
import io.testfly.driver.DriverManager;
import io.testfly.internal.TestFlyContext;
import io.testfly.steps.StepLogger;
import org.mockito.MockedStatic;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Unit tests for {@link LocatorAssert} polling and assertion behaviour.
 * All WebDriver interactions are mocked — no real browser is required.
 */
@Test(singleThreaded = true)
public class LocatorAssertTest {

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
        SoftAssertions.clear();
        if (stepLoggerMock != null)
            stepLoggerMock.close();
        if (contextMock != null)
            contextMock.close();
        if (driverManagerMock != null)
            driverManagerMock.close();
    }

    // ---------------------------------------------------------------
    // Helper
    // ---------------------------------------------------------------

    private WebElement mockElement(boolean displayed, String text) {
        WebElement el = mock(WebElement.class);
        lenient().when(el.isDisplayed()).thenReturn(displayed);
        lenient().when(el.getText()).thenReturn(text);
        return el;
    }

    // ---------------------------------------------------------------
    // isVisible
    // ---------------------------------------------------------------

    @Test
    public void isVisible_elementVisible_passes() {
        By locator = By.id("status");
        WebElement el = mockElement(true, "");
        when(mockDriver.findElement(locator)).thenReturn(el);

        LocatorAssert result = SeleniumAssert.assertThat(locator).isVisible();
        assertNotNull(result, "isVisible should return this for chaining");
    }

    @Test(expectedExceptions = AssertionError.class)
    public void isVisible_elementHidden_fails() {
        By locator = By.id("hidden-el");
        WebElement el = mockElement(false, "");
        when(mockDriver.findElement(locator)).thenReturn(el);

        SeleniumAssert.assertThat(locator).isVisible();
    }

    @Test(expectedExceptions = AssertionError.class)
    public void isVisible_elementNotFound_fails() {
        By locator = By.id("missing");
        when(mockDriver.findElement(locator))
                .thenThrow(new org.openqa.selenium.NoSuchElementException("not found"));

        SeleniumAssert.assertThat(locator).isVisible();
    }

    // ---------------------------------------------------------------
    // isHidden
    // ---------------------------------------------------------------

    @Test
    public void isHidden_elementHidden_passes() {
        By locator = By.id("hidden-el");
        WebElement el = mockElement(false, "");
        when(mockDriver.findElement(locator)).thenReturn(el);

        LocatorAssert result = SeleniumAssert.assertThat(locator).isHidden();
        assertNotNull(result);
    }

    @Test
    public void isHidden_elementAbsent_passes() {
        By locator = By.id("gone");
        when(mockDriver.findElement(locator))
                .thenThrow(new org.openqa.selenium.NoSuchElementException("not found"));

        SeleniumAssert.assertThat(locator).isHidden();
    }

    @Test(expectedExceptions = AssertionError.class)
    public void isHidden_elementVisible_fails() {
        By locator = By.id("visible-el");
        WebElement el = mockElement(true, "");
        when(mockDriver.findElement(locator)).thenReturn(el);

        SeleniumAssert.assertThat(locator).isHidden();
    }

    // ---------------------------------------------------------------
    // hasText
    // ---------------------------------------------------------------

    @Test
    public void hasText_exactMatch_passes() {
        By locator = By.id("title");
        WebElement el = mockElement(true, "Hello World");
        when(mockDriver.findElement(locator)).thenReturn(el);

        SeleniumAssert.assertThat(locator).hasText("Hello World");
    }

    @Test(expectedExceptions = AssertionError.class)
    public void hasText_wrongText_fails() {
        By locator = By.id("title");
        WebElement el = mockElement(true, "Goodbye");
        when(mockDriver.findElement(locator)).thenReturn(el);

        SeleniumAssert.assertThat(locator).hasText("Hello");
    }

    // ---------------------------------------------------------------
    // containsText
    // ---------------------------------------------------------------

    @Test
    public void containsText_partialMatch_passes() {
        By locator = By.id("msg");
        WebElement el = mockElement(true, "Welcome to TestFly");
        when(mockDriver.findElement(locator)).thenReturn(el);

        SeleniumAssert.assertThat(locator).containsText("TestFly");
    }

    @Test(expectedExceptions = AssertionError.class)
    public void containsText_noMatch_fails() {
        By locator = By.id("msg");
        WebElement el = mockElement(true, "Welcome to TestFly");
        when(mockDriver.findElement(locator)).thenReturn(el);

        SeleniumAssert.assertThat(locator).containsText("Goodbye");
    }

    // ---------------------------------------------------------------
    // hasAttribute
    // ---------------------------------------------------------------

    @Test
    public void hasAttribute_correctValue_passes() {
        By locator = By.id("link");
        WebElement el = mock(WebElement.class);
        when(el.getAttribute("href")).thenReturn("/dashboard");
        when(mockDriver.findElement(locator)).thenReturn(el);

        SeleniumAssert.assertThat(locator).hasAttribute("href", "/dashboard");
    }

    @Test(expectedExceptions = AssertionError.class)
    public void hasAttribute_wrongValue_fails() {
        By locator = By.id("link");
        WebElement el = mock(WebElement.class);
        when(el.getAttribute("href")).thenReturn("/home");
        when(mockDriver.findElement(locator)).thenReturn(el);

        SeleniumAssert.assertThat(locator).hasAttribute("href", "/dashboard");
    }

    @Test(expectedExceptions = AssertionError.class)
    public void hasAttribute_missingAttribute_fails() {
        By locator = By.id("link");
        WebElement el = mock(WebElement.class);
        when(el.getAttribute("data-custom")).thenReturn(null);
        when(mockDriver.findElement(locator)).thenReturn(el);

        SeleniumAssert.assertThat(locator).hasAttribute("data-custom", "value");
    }

    // ---------------------------------------------------------------
    // count
    // ---------------------------------------------------------------

    @Test
    public void count_correctCount_passes() {
        By locator = By.cssSelector(".item");
        WebElement el1 = mock(WebElement.class);
        WebElement el2 = mock(WebElement.class);
        WebElement el3 = mock(WebElement.class);
        when(mockDriver.findElements(locator)).thenReturn(Arrays.asList(el1, el2, el3));

        SeleniumAssert.assertThat(locator).count(3);
    }

    @Test(expectedExceptions = AssertionError.class)
    public void count_wrongCount_fails() {
        By locator = By.cssSelector(".item");
        WebElement el1 = mock(WebElement.class);
        WebElement el2 = mock(WebElement.class);
        when(mockDriver.findElements(locator)).thenReturn(Arrays.asList(el1, el2));

        SeleniumAssert.assertThat(locator).count(5);
    }

    @Test
    public void count_zero_emptyList_passes() {
        By locator = By.cssSelector(".nothing");
        when(mockDriver.findElements(locator)).thenReturn(Collections.emptyList());

        SeleniumAssert.assertThat(locator).count(0);
    }

    // ---------------------------------------------------------------
    // Chaining
    // ---------------------------------------------------------------

    @Test
    public void chaining_multipleAssertions_allPass() {
        By locator = By.id("el");
        WebElement el = mockElement(true, "Hello");
        when(mockDriver.findElement(locator)).thenReturn(el);
        when(mockDriver.findElements(locator))
                .thenReturn(Arrays.asList(mock(WebElement.class), mock(WebElement.class)));

        LocatorAssert result = SeleniumAssert.assertThat(locator)
                .isVisible()
                .containsText("Hell")
                .count(2);

        assertNotNull(result, "Chaining should return the same LocatorAssert instance");
    }

    @Test
    public void chaining_returnsThis() {
        By locator = By.id("el");
        WebElement el = mockElement(true, "");
        when(mockDriver.findElement(locator)).thenReturn(el);

        LocatorAssert la = SeleniumAssert.assertThat(locator);
        LocatorAssert returned = la.isVisible();
        assertSame(returned, la, "Each assertion method should return 'this' for chaining");
    }

    // ---------------------------------------------------------------
    // Failure message
    // ---------------------------------------------------------------

    @Test
    public void failureMessage_containsDescription() {
        By locator = By.id("username");
        when(mockDriver.findElement(locator))
                .thenThrow(new org.openqa.selenium.NoSuchElementException("not found"));

        try {
            SeleniumAssert.assertThat(locator).isVisible();
            fail("Expected AssertionError");
        } catch (AssertionError e) {
            String msg = e.getMessage();
            assertTrue(msg.contains("Expected element to be visible"),
                    "Failure message should contain assertion description, was: " + msg);
            assertTrue(msg.contains("timeout"),
                    "Failure message should mention timeout, was: " + msg);
        }
    }

    // ---------------------------------------------------------------
    // Custom as() and within()
    // ---------------------------------------------------------------

    @Test
    public void as_customMessage_prependsMessageToFailure() {
        By locator = By.id("missing-btn");
        when(mockDriver.findElement(locator))
                .thenThrow(new org.openqa.selenium.NoSuchElementException("not found"));

        try {
            SeleniumAssert.assertThat(locator).as("Login button missing").isVisible();
            fail("Expected AssertionError");
        } catch (AssertionError e) {
            assertTrue(e.getMessage().contains("[Login button missing]"),
                    "Failure should contain custom message prefix, was: " + e.getMessage());
        }
    }

    @Test
    public void within_customTimeout_appliesTimeout() {
        By locator = By.id("quick-toast");
        WebElement el = mockElement(true, "Saved");
        when(mockDriver.findElement(locator)).thenReturn(el);

        LocatorAssert result = SeleniumAssert.assertThat(locator)
                .within(Duration.ofMillis(500))
                .isVisible();
        assertNotNull(result);

        LocatorAssert resultSec = SeleniumAssert.assertThat(locator)
                .within(2)
                .hasText("Saved");
        assertNotNull(resultSec);
    }

    // ---------------------------------------------------------------
    // hasCssValue
    // ---------------------------------------------------------------

    @Test
    public void hasCssValue_matches_passes() {
        By locator = By.id("alert-box");
        WebElement el = mockElement(true, "");
        when(el.getCssValue("color")).thenReturn("rgb(255, 0, 0)");
        when(mockDriver.findElements(locator)).thenReturn(Collections.singletonList(el));

        LocatorAssert la = SeleniumAssert.assertThat(locator).hasCssValue("color", "rgb(255, 0, 0)");
        assertNotNull(la);
    }

    @Test(expectedExceptions = AssertionError.class)
    public void hasCssValue_mismatch_fails() {
        By locator = By.id("alert-box");
        WebElement el = mockElement(true, "");
        when(el.getCssValue("color")).thenReturn("rgb(0, 0, 0)");
        when(mockDriver.findElements(locator)).thenReturn(Collections.singletonList(el));

        SeleniumAssert.assertThat(locator).hasCssValue("color", "rgb(255, 0, 0)");
    }

    // ---------------------------------------------------------------
    // hasAttribute (presence check)
    // ---------------------------------------------------------------

    @Test
    public void hasAttribute_present_passes() {
        By locator = By.id("input-field");
        WebElement el = mockElement(true, "");
        when(el.getAttribute("disabled")).thenReturn("true");
        when(mockDriver.findElements(locator)).thenReturn(Collections.singletonList(el));

        LocatorAssert la = SeleniumAssert.assertThat(locator).hasAttribute("disabled");
        assertNotNull(la);
    }

    @Test(expectedExceptions = AssertionError.class)
    public void hasAttribute_missing_fails() {
        By locator = By.id("input-field");
        WebElement el = mockElement(true, "");
        when(el.getAttribute("disabled")).thenReturn(null);
        when(mockDriver.findElements(locator)).thenReturn(Collections.singletonList(el));

        SeleniumAssert.assertThat(locator).hasAttribute("disabled");
    }

    // ---------------------------------------------------------------
    // isFocused
    // ---------------------------------------------------------------

    @Test
    public void isFocused_activeElementMatches_passes() {
        By locator = By.id("active-input");
        WebElement el = mockElement(true, "");
        WebDriver.TargetLocator targetLocator = mock(WebDriver.TargetLocator.class);
        when(mockDriver.switchTo()).thenReturn(targetLocator);
        when(targetLocator.activeElement()).thenReturn(el);
        when(mockDriver.findElements(locator)).thenReturn(Collections.singletonList(el));

        LocatorAssert la = SeleniumAssert.assertThat(locator).isFocused();
        assertNotNull(la);
    }

    @Test(expectedExceptions = AssertionError.class)
    public void isFocused_activeElementDoesNotMatch_fails() {
        By locator = By.id("inactive-input");
        WebElement el = mockElement(true, "");
        WebElement otherEl = mockElement(true, "");
        WebDriver.TargetLocator targetLocator = mock(WebDriver.TargetLocator.class);
        when(mockDriver.switchTo()).thenReturn(targetLocator);
        when(targetLocator.activeElement()).thenReturn(otherEl);
        when(mockDriver.findElements(locator)).thenReturn(Collections.singletonList(el));

        SeleniumAssert.assertThat(locator).isFocused();
    }

    // ---------------------------------------------------------------
    // Soft Assertions
    // ---------------------------------------------------------------

    @Test
    public void softly_failureRecordedWithoutThrowing() {
        By locator = By.id("absent");
        when(mockDriver.findElement(locator))
                .thenThrow(new org.openqa.selenium.NoSuchElementException("not found"));

        // Does NOT throw AssertionError
        SeleniumAssert.assertThat(locator).softly().isVisible();

        assertTrue(SoftAssertions.get().hasFailed(), "Failure should be recorded in SoftAssertions");
        assertTrue(SoftAssertions.get().getFailures().get(0).contains("Expected element to be visible"));
    }

    @Test
    public void softAssert_byLocator_recordsFailureWithoutThrowing() {
        By locator = By.id("absent");
        when(mockDriver.findElement(locator))
                .thenThrow(new org.openqa.selenium.NoSuchElementException("not found"));

        SeleniumAssert.softAssert(locator).as("Custom soft check").isVisible();

        assertTrue(SoftAssertions.get().hasFailed());
        assertTrue(SoftAssertions.get().getFailures().get(0).contains("[Custom soft check]"));
    }

    @Test
    public void softAssertCollector_assertThat_recordsFailureWithoutThrowing() {
        By locator = By.id("absent");
        when(mockDriver.findElement(locator))
                .thenThrow(new org.openqa.selenium.NoSuchElementException("not found"));

        SoftAssertions.get().assertThat(locator).isVisible();

        assertTrue(SoftAssertions.get().hasFailed());
    }

    // ---------------------------------------------------------------
    // Failure diagnostics: "actual" values
    // ---------------------------------------------------------------

    private String failureOf(Runnable r) {
        try {
            r.run();
        } catch (AssertionError e) {
            return e.getMessage();
        }
        fail("Expected AssertionError");
        return null;
    }

    @Test
    public void isVisible_notDisplayed_reportsActual() {
        By locator = By.id("a");
        WebElement stubEl = mockElement(false, "");
        when(mockDriver.findElement(locator)).thenReturn(stubEl);
        String msg = failureOf(() -> SeleniumAssert.assertThat(locator).isVisible());
        assertTrue(msg.contains("actual: [not displayed]"), msg);
    }

    @Test
    public void isVisible_notFound_reportsActual() {
        By locator = By.id("a");
        when(mockDriver.findElement(locator)).thenThrow(new org.openqa.selenium.NoSuchElementException("x"));
        String msg = failureOf(() -> SeleniumAssert.assertThat(locator).isVisible());
        assertTrue(msg.contains("actual: [not found]"), msg);
    }

    @Test
    public void isHidden_displayed_reportsActual() {
        By locator = By.id("a");
        WebElement stubEl = mockElement(true, "");
        when(mockDriver.findElement(locator)).thenReturn(stubEl);
        String msg = failureOf(() -> SeleniumAssert.assertThat(locator).isHidden());
        assertTrue(msg.contains("actual: [displayed]"), msg);
    }

    @Test
    public void isEnabled_disabled_reportsActual() {
        By locator = By.id("a");
        WebElement el = mockElement(true, "");
        lenient().when(el.isEnabled()).thenReturn(false);
        when(mockDriver.findElements(locator)).thenReturn(Collections.singletonList(el));
        String msg = failureOf(() -> SeleniumAssert.assertThat(locator).isEnabled());
        assertTrue(msg.contains("actual: [disabled]"), msg);
    }

    @Test
    public void isDisabled_enabled_reportsActual() {
        By locator = By.id("a");
        WebElement el = mockElement(true, "");
        lenient().when(el.isEnabled()).thenReturn(true);
        when(mockDriver.findElements(locator)).thenReturn(Collections.singletonList(el));
        String msg = failureOf(() -> SeleniumAssert.assertThat(locator).isDisabled());
        assertTrue(msg.contains("actual: [enabled]"), msg);
    }

    @Test
    public void isChecked_unchecked_reportsActual() {
        By locator = By.id("a");
        WebElement el = mock(WebElement.class);
        when(el.isSelected()).thenReturn(false);
        when(mockDriver.findElements(locator)).thenReturn(Collections.singletonList(el));
        String msg = failureOf(() -> SeleniumAssert.assertThat(locator).isChecked());
        assertTrue(msg.contains("actual: [not selected]"), msg);
    }

    @Test
    public void hasText_mismatch_reportsActualText() {
        By locator = By.id("t");
        WebElement stubEl = mockElement(true, "Goodbye");
        when(mockDriver.findElement(locator)).thenReturn(stubEl);
        String msg = failureOf(() -> SeleniumAssert.assertThat(locator).hasText("Hello"));
        assertTrue(msg.contains("actual: [\"Goodbye\"]"), msg);
        assertTrue(msg.contains("Expected text [Hello]"), msg);
    }

    @Test
    public void containsText_mismatch_reportsActualText() {
        By locator = By.id("t");
        WebElement stubEl = mockElement(true, "Welcome");
        when(mockDriver.findElement(locator)).thenReturn(stubEl);
        String msg = failureOf(() -> SeleniumAssert.assertThat(locator).containsText("Bye"));
        assertTrue(msg.contains("actual: [\"Welcome\"]"), msg);
    }

    @Test
    public void hasText_longMultilineText_isSingleLineAndTruncated() {
        By locator = By.id("t");
        String text = "line1\nline2\n" + "x".repeat(300);
        WebElement stubEl = mockElement(true, text);
        when(mockDriver.findElement(locator)).thenReturn(stubEl);
        String msg = failureOf(() -> SeleniumAssert.assertThat(locator).hasText("nope"));
        assertTrue(!msg.contains("\n"), "message must be single line: " + msg);
        assertTrue(msg.contains("line1\\nline2"), msg);
        assertTrue(msg.contains("…"), msg);
    }

    @Test
    public void hasValue_mismatch_reportsActualValue() {
        By locator = By.id("v");
        WebElement el = mock(WebElement.class);
        when(el.getAttribute("value")).thenReturn("abc");
        when(mockDriver.findElement(locator)).thenReturn(el);
        String msg = failureOf(() -> SeleniumAssert.assertThat(locator).hasValue("xyz"));
        assertTrue(msg.contains("actual: [\"abc\"]"), msg);
    }

    @Test
    public void hasAttribute_absent_reportsAbsent() {
        By locator = By.id("v");
        WebElement el = mock(WebElement.class);
        when(el.getAttribute("href")).thenReturn(null);
        when(mockDriver.findElement(locator)).thenReturn(el);
        String msg = failureOf(() -> SeleniumAssert.assertThat(locator).hasAttribute("href", "/x"));
        assertTrue(msg.contains("actual: [attribute 'href' absent]"), msg);
    }

    @Test
    public void hasAttribute_mismatch_reportsActualValue() {
        By locator = By.id("v");
        WebElement el = mock(WebElement.class);
        when(el.getAttribute("href")).thenReturn("/home");
        when(mockDriver.findElement(locator)).thenReturn(el);
        String msg = failureOf(() -> SeleniumAssert.assertThat(locator).hasAttribute("href", "/x"));
        assertTrue(msg.contains("actual: [href=\"/home\"]"), msg);
    }

    @Test
    public void hasCssValue_mismatch_reportsActual() {
        By locator = By.id("c");
        WebElement el = mockElement(true, "");
        when(el.getCssValue("color")).thenReturn("rgb(0, 0, 0)");
        when(mockDriver.findElements(locator)).thenReturn(Collections.singletonList(el));
        String msg = failureOf(() -> SeleniumAssert.assertThat(locator).hasCssValue("color", "red"));
        assertTrue(msg.contains("actual: [color=\"rgb(0, 0, 0)\"]"), msg);
    }

    @Test
    public void hasClass_missing_reportsClassAttribute() {
        By locator = By.id("c");
        WebElement el = mock(WebElement.class);
        when(el.getAttribute("class")).thenReturn("btn primary");
        when(mockDriver.findElements(locator)).thenReturn(Collections.singletonList(el));
        String msg = failureOf(() -> SeleniumAssert.assertThat(locator).hasClass("danger"));
        assertTrue(msg.contains("actual: [class=\"btn primary\"]"), msg);
    }

    @Test
    public void isFocused_otherElementFocused_reportsFocusedElement() {
        By locator = By.id("f");
        WebElement el = mockElement(true, "");
        WebElement other = mockElement(true, "");
        when(other.getTagName()).thenReturn("input");
        when(other.getAttribute("id")).thenReturn("other");
        WebDriver.TargetLocator tl = mock(WebDriver.TargetLocator.class);
        when(mockDriver.switchTo()).thenReturn(tl);
        when(tl.activeElement()).thenReturn(other);
        when(mockDriver.findElements(locator)).thenReturn(Collections.singletonList(el));
        String msg = failureOf(() -> SeleniumAssert.assertThat(locator).isFocused());
        assertTrue(msg.contains("actual: [focused element: <input#other>]"), msg);
    }

    @Test
    public void count_mismatch_reportsActualCount() {
        By locator = By.cssSelector(".item");
        when(mockDriver.findElements(locator))
                .thenReturn(Arrays.asList(mock(WebElement.class), mock(WebElement.class)));
        String msg = failureOf(() -> SeleniumAssert.assertThat(locator).count(5));
        assertTrue(msg.contains("actual: [2 element(s)]"), msg);
    }

    @Test
    public void locatorException_becomesAssertionError() {
        By locator = By.id("inside-missing-container");
        when(mockDriver.findElements(locator))
                .thenThrow(new io.testfly.locator.LocatorException("Container not found: By.id: missing"));
        String msg = failureOf(() -> SeleniumAssert.assertThat(locator).within(Duration.ofMillis(600)).count(1));
        assertTrue(msg.contains("locator failed: Container not found"), msg);
    }

    @Test
    public void softFailure_includesActual() {
        By locator = By.id("t");
        WebElement stubEl = mockElement(true, "Goodbye");
        when(mockDriver.findElement(locator)).thenReturn(stubEl);
        SeleniumAssert.assertThat(locator).softly().hasText("Hello");
        assertTrue(SoftAssertions.get().getFailures().get(0).contains("actual: [\"Goodbye\"]"));
    }

    @Test
    public void poll_nullConfig_fallsBackToDefaultTimeout() {
        contextMock.when(TestFlyContext::getConfig).thenReturn(null);
        By locator = By.id("ok");
        WebElement stubEl = mockElement(true, "");
        when(mockDriver.findElement(locator)).thenReturn(stubEl);
        assertNotNull(SeleniumAssert.assertThat(locator).isVisible());
    }
}
