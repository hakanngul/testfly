package io.testfly.unit.assertion;

import io.testfly.assertion.LocatorAssert;
import io.testfly.assertion.SeleniumAssert;
import io.testfly.assertion.SoftAssertions;
import io.testfly.config.TestFlyConfig;
import io.testfly.driver.DriverManager;
import io.testfly.internal.TestFlyContext;
import io.testfly.steps.StepLogger;
import io.testfly.steps.StepStatus;
import org.mockito.MockedStatic;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Unit tests for the verified {@link LocatorAssert#fill(String)} and
 * {@link LocatorAssert#clear()}. All WebDriver interactions are mocked; the
 * field mock keeps a simulated {@code value}.
 */
@Test(singleThreaded = true)
public class LocatorAssertFillTest {

    private static final By FIELD = By.id("field");

    private WebDriver driver;
    private WebElement field;
    private AtomicReference<String> value;
    private int maxLength;
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
        TestFlyConfig.Locators locators = new TestFlyConfig.Locators();
        locators.setSelfHealing(false);
        TestFlyConfig config = new TestFlyConfig();
        config.setTimeouts(timeouts);
        config.setLocators(locators);
        contextMock = mockStatic(TestFlyContext.class);
        contextMock.when(TestFlyContext::getConfig).thenReturn(config);

        stepLoggerMock = mockStatic(StepLogger.class);
        SoftAssertions.clear();

        value = new AtomicReference<>("");
        maxLength = Integer.MAX_VALUE;
        field = simulatedInput(value);
        lenient().when(driver.findElements(FIELD)).thenReturn(List.of(field));
    }

    @AfterMethod
    public void tearDown() {
        SoftAssertions.clear();
        stepLoggerMock.close();
        contextMock.close();
        driverManagerMock.close();
    }

    /** An input mock whose value reacts to clear() and sendKeys() like a browser field. */
    private WebElement simulatedInput(AtomicReference<String> state) {
        WebElement el = mock(WebElement.class);
        lenient().when(el.isDisplayed()).thenReturn(true);
        lenient().when(el.isEnabled()).thenReturn(true);
        lenient().when(el.getDomProperty("value")).thenAnswer(inv -> state.get());
        lenient().doAnswer(inv -> {
            state.set("");
            return null;
        }).when(el).clear();
        lenient().doAnswer(inv -> {
            StringBuilder typed = new StringBuilder();
            for (Object part : inv.getArguments()) {
                typed.append(part);
            }
            String keys = typed.toString();
            if (keys.indexOf(Keys.BACK_SPACE.charAt(0)) >= 0) {
                state.set("");
            } else {
                String next = state.get() + keys;
                state.set(next.length() > maxLength ? next.substring(0, maxLength) : next);
            }
            return null;
        }).when(el).sendKeys(any(CharSequence[].class));
        return el;
    }

    private static LocatorAssert expect(By by) {
        return SeleniumAssert.assertThat(by).within(Duration.ofMillis(300));
    }

    private void verifyPassStep(String message) {
        stepLoggerMock.verify(() -> StepLogger.step(message, StepStatus.PASS));
    }

    // ---------------------------------------------------------------
    // fill
    // ---------------------------------------------------------------

    @Test
    public void fill_replacesValueAndLogsPass() {
        value.set("old");

        expect(FIELD).fill("admin");

        verify((JavascriptExecutor) driver).executeScript(argThat((String s) -> s.contains("scrollIntoView")), any());
        verifyPassStep("Filled: " + FIELD + " with [admin]");
    }

    @Test
    public void fill_truncatedByMaxLength_failsShowingActualValue() {
        maxLength = 3;

        AssertionError e = expectThrows(AssertionError.class, () -> expect(FIELD).fill("abcdef"));
        assertTrue(e.getMessage().contains("Fill did not stick"), e.getMessage());
        assertTrue(e.getMessage().contains("expected value [abcdef] but was [abc]"), e.getMessage());
    }

    @Test
    public void fill_valueResetByAppRightAfterTyping_fails() {
        // The value reads correctly once, then the app (e.g. controlled state) wipes it.
        when(field.getDomProperty("value")).thenAnswer(inv -> {
            String current = value.get();
            if ("admin".equals(current)) {
                value.set("");
            }
            return current;
        });

        AssertionError e = expectThrows(AssertionError.class, () -> expect(FIELD).fill("admin"));
        assertTrue(e.getMessage().contains("expected value [admin] but was []"), e.getMessage());
    }

    @Test
    public void fill_password_neverLogsTheValue() {
        when(field.getDomProperty("type")).thenReturn("password");

        expect(FIELD).fill("s3cret!");

        verifyPassStep("Filled: " + FIELD + " (7 characters)");
        stepLoggerMock.verify(() -> StepLogger.step(argThat((String m) -> m.contains("s3cret!")), any(StepStatus.class)),
                never());
    }

    @Test
    public void fill_passwordMismatch_hidesValueInFailure() {
        when(field.getDomProperty("type")).thenReturn("password");
        maxLength = 3;

        AssertionError e = expectThrows(AssertionError.class, () -> expect(FIELD).fill("s3cret!"));
        assertTrue(e.getMessage().contains("expected 7 characters but got 3 (password value hidden)"), e.getMessage());
        assertFalse(e.getMessage().contains("s3c"), e.getMessage());
    }

    @Test
    public void fill_readOnly_failsWithoutTyping() {
        when(field.getDomAttribute("readonly")).thenReturn("true");

        AssertionError e = expectThrows(AssertionError.class, () -> expect(FIELD).fill("admin"));
        assertTrue(e.getMessage().contains("Fill not performed: element is read-only"), e.getMessage());
        verify(field, never()).sendKeys("admin");
    }

    @Test
    public void fill_absent_fails() {
        when(driver.findElements(FIELD)).thenReturn(Collections.emptyList());

        AssertionError e = expectThrows(AssertionError.class, () -> expect(FIELD).fill("admin"));
        assertTrue(e.getMessage().contains("Fill not performed: element not visible and enabled"), e.getMessage());
    }

    @Test
    public void fill_fieldReRendered_readsTheNewField() {
        AtomicReference<String> rerenderedValue = new AtomicReference<>("admin");
        WebElement rerendered = simulatedInput(rerenderedValue);
        when(field.getDomProperty("value")).thenThrow(new StaleElementReferenceException("re-rendered"));
        when(driver.findElements(FIELD)).thenReturn(List.of(field), List.of(rerendered));

        expect(FIELD).fill("admin");

        verifyPassStep("Filled: " + FIELD + " with [admin]");
    }

    @Test
    public void fill_softMode_recordsFailureAndContinues() {
        when(field.getDomAttribute("readonly")).thenReturn("true");

        expect(FIELD).softly().as("Username").fill("admin");

        assertTrue(SoftAssertions.get().hasFailed());
        assertTrue(SoftAssertions.get().getFailures().get(0).startsWith("[Username] Fill not performed"));
    }

    // ---------------------------------------------------------------
    // clear
    // ---------------------------------------------------------------

    @Test
    public void clear_emptiesFieldAndLogsPass() {
        value.set("abc");

        expect(FIELD).clear();

        verifyPassStep("Cleared: " + FIELD);
    }

    @Test
    public void clear_valueRestoredByApp_fails() {
        value.set("abc");
        // The app puts the value back right after every clear attempt.
        doAnswer(inv -> null).when(field).clear();
        doAnswer(inv -> null).when(field).sendKeys(any(CharSequence[].class));

        AssertionError e = expectThrows(AssertionError.class, () -> expect(FIELD).clear());
        assertTrue(e.getMessage().contains("Clear did not stick"), e.getMessage());
        assertTrue(e.getMessage().contains("expected value [] but was [abc]"), e.getMessage());
    }
}
