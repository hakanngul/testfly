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
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Unit tests for {@link LocatorAssert#not()}: Playwright-style negation of the
 * next assertion. All WebDriver interactions are mocked.
 */
@Test(singleThreaded = true)
public class LocatorAssertNotTest {

    private static final By STATUS = By.id("status");

    private WebDriver driver;
    private WebElement element;
    private MockedStatic<DriverManager> driverManagerMock;
    private MockedStatic<TestFlyContext> contextMock;
    private MockedStatic<StepLogger> stepLoggerMock;

    @BeforeMethod
    public void setUp() {
        driver = mock(WebDriver.class, withSettings().extraInterfaces(JavascriptExecutor.class));
        driverManagerMock = mockStatic(DriverManager.class);
        driverManagerMock.when(DriverManager::getDriver).thenReturn(driver);

        TestFlyConfig.Timeouts timeouts = new TestFlyConfig.Timeouts();
        timeouts.setExplicit(1);
        TestFlyConfig config = new TestFlyConfig();
        config.setTimeouts(timeouts);
        contextMock = mockStatic(TestFlyContext.class);
        contextMock.when(TestFlyContext::getConfig).thenReturn(config);

        stepLoggerMock = mockStatic(StepLogger.class);
        SoftAssertions.clear();

        element = mock(WebElement.class);
        lenient().when(element.isDisplayed()).thenReturn(true);
        lenient().when(element.isEnabled()).thenReturn(true);
        lenient().when(element.getText()).thenReturn("Welcome");
        present(element);
    }

    @AfterMethod
    public void tearDown() {
        SoftAssertions.clear();
        stepLoggerMock.close();
        contextMock.close();
        driverManagerMock.close();
    }

    private void present(WebElement el) {
        lenient().when(driver.findElement(STATUS)).thenReturn(el);
        lenient().when(driver.findElements(STATUS)).thenReturn(List.of(el));
    }

    private void absent() {
        when(driver.findElement(STATUS)).thenThrow(new NoSuchElementException("absent"));
        when(driver.findElements(STATUS)).thenReturn(Collections.emptyList());
    }

    private static LocatorAssert expect() {
        return SeleniumAssert.assertThat(STATUS).within(Duration.ofMillis(300));
    }

    // ---------------------------------------------------------------
    // Visibility & presence
    // ---------------------------------------------------------------

    @Test
    public void notVisible_hiddenOrAbsent_passes() {
        when(element.isDisplayed()).thenReturn(false);
        expect().not().isVisible();

        absent();
        expect().not().isVisible();
    }

    @Test
    public void notVisible_visible_failsWithNegatedMessage() {
        AssertionError e = expectThrows(AssertionError.class, () -> expect().not().isVisible());
        assertTrue(e.getMessage().startsWith("Expected NOT element to be visible"), e.getMessage());
    }

    @Test
    public void notAttached_absent_passes() {
        absent();
        expect().not().isAttached();
    }

    @Test(expectedExceptions = AssertionError.class)
    public void notAttached_present_fails() {
        expect().not().isAttached();
    }

    @Test
    public void notCount_differentCount_passes() {
        expect().not().hasCount(3);
    }

    // ---------------------------------------------------------------
    // Text, value, attributes, classes
    // ---------------------------------------------------------------

    @Test
    public void notHasText_differentTextOrAbsent_passes() {
        expect().not().hasText("Error").not().containsText("fail");

        absent();
        expect().not().hasText("Error");
    }

    @Test
    public void notHasText_sameText_fails() {
        AssertionError e = expectThrows(AssertionError.class, () -> expect().not().hasText("Welcome"));
        assertTrue(e.getMessage().startsWith("Expected NOT text [Welcome]"), e.getMessage());
    }

    @Test
    public void notHasValue_differentValue_passes() {
        when(element.getAttribute("value")).thenReturn("admin");
        expect().not().hasValue("guest");
    }

    @Test(expectedExceptions = AssertionError.class)
    public void notHasValue_sameValue_fails() {
        when(element.getAttribute("value")).thenReturn("admin");
        when(element.getDomAttribute("value")).thenReturn("admin");
        when(element.getDomProperty("value")).thenReturn("admin");
        expect().not().hasValue("admin");
    }

    @Test
    public void notContainsClass_missingClass_passes() {
        when(element.getDomAttribute("class")).thenReturn("btn btn-primary");
        expect().not().containsClass("loading");
    }

    @Test(expectedExceptions = AssertionError.class)
    public void notContainsClass_presentClass_fails() {
        when(element.getDomAttribute("class")).thenReturn("btn loading");
        expect().not().containsClass("loading");
    }

    @Test
    public void notHasAttribute_missingAttribute_passes() {
        expect().not().hasAttribute("disabled");
    }

    @Test
    public void notChecked_unchecked_passes() {
        expect().not().isChecked();
    }

    @Test(expectedExceptions = AssertionError.class)
    public void notChecked_checked_fails() {
        when(element.isSelected()).thenReturn(true);
        expect().not().isChecked();
    }

    @Test
    public void notFocused_otherActiveElement_passes() {
        WebDriver.TargetLocator target = mock(WebDriver.TargetLocator.class);
        when(driver.switchTo()).thenReturn(target);
        when(target.activeElement()).thenReturn(mock(WebElement.class));
        expect().not().isFocused();
    }

    // ---------------------------------------------------------------
    // Scope, actions, soft mode, logging
    // ---------------------------------------------------------------

    @Test
    public void not_negatesOnlyTheNextAssertion() {
        expect().not().hasText(Pattern.compile("Error")).hasText(Pattern.compile("Welcome"));

        // If not() leaked into the second assertion, this would pass.
        expectThrows(AssertionError.class,
                () -> expect().not().hasText(Pattern.compile("Error")).hasText(Pattern.compile("Error")));
    }

    @Test
    public void not_withVerifiedActions_throwsIllegalState() {
        IllegalStateException click = expectThrows(IllegalStateException.class, () -> expect().not().click());
        assertTrue(click.getMessage().contains("not() cannot be combined with click()"));
        expectThrows(IllegalStateException.class, () -> expect().not().fill("x"));
        expectThrows(IllegalStateException.class, () -> expect().not().clear());
        verify(element, never()).click();
    }

    @Test
    public void not_softMode_recordsNegatedFailure() {
        expect().softly().not().hasText("Welcome");

        assertTrue(SoftAssertions.get().hasFailed());
        assertTrue(SoftAssertions.get().getFailures().get(0).startsWith("Expected NOT text [Welcome]"));
    }

    @Test
    public void not_marksTheLoggedStep() {
        absent();
        expect().not().isAttached();

        stepLoggerMock.verify(() -> StepLogger.step(argThat((String m) -> m.startsWith("[not] Assert attached"))));
    }
}
