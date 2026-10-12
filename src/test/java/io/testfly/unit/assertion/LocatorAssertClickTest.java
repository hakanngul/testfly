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
import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Interactive;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Unit tests for the verified {@link LocatorAssert#click()}. All WebDriver
 * interactions are mocked.
 */
@Test(singleThreaded = true)
public class LocatorAssertClickTest {

    private static final By LOGIN = By.id("login");

    private WebDriver driver;
    private WebElement button;
    private MockedStatic<DriverManager> driverManagerMock;
    private MockedStatic<TestFlyContext> contextMock;
    private MockedStatic<StepLogger> stepLoggerMock;

    @BeforeMethod
    public void setUp() {
        driver = mock(WebDriver.class, withSettings().extraInterfaces(JavascriptExecutor.class, Interactive.class));
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

        // Default: a visible, enabled, uncovered button that receives the click.
        button = mock(WebElement.class);
        lenient().when(button.isDisplayed()).thenReturn(true);
        lenient().when(button.isEnabled()).thenReturn(true);
        lenient().when(driver.findElements(LOGIN)).thenReturn(List.of(button));
        script("elementFromPoint", null);
        script("__testflyClicked === true", true);
    }

    @AfterMethod
    public void tearDown() {
        SoftAssertions.clear();
        stepLoggerMock.close();
        contextMock.close();
        driverManagerMock.close();
    }

    /** Stubs the result of any script containing {@code fragment}. */
    private void script(String fragment, Object result) {
        lenient().when(((JavascriptExecutor) driver).executeScript(
                argThat((String s) -> s != null && s.contains(fragment)), any()))
                .thenReturn(result);
    }

    private static LocatorAssert expect(By by) {
        return SeleniumAssert.assertThat(by).within(Duration.ofMillis(300));
    }

    @Test
    public void click_success_scrollsHoversClicksAndLogsPass() {
        expect(LOGIN).click();

        verify((JavascriptExecutor) driver).executeScript(
                argThat((String s) -> s.contains("scrollIntoView")), any());
        verify((Interactive) driver).perform(anyCollection());
        verify(button).click();
        stepLoggerMock.verify(() -> StepLogger.step("Clicked: " + LOGIN, StepStatus.PASS));
    }

    @Test
    public void click_elementAbsent_failsWithoutClicking() {
        when(driver.findElements(LOGIN)).thenReturn(Collections.emptyList());

        AssertionError e = expectThrows(AssertionError.class, () -> expect(LOGIN).click());
        assertTrue(e.getMessage().contains("Click not performed: element not visible and enabled"));
    }

    @Test
    public void click_elementDisabled_failsWithoutClicking() {
        when(button.isEnabled()).thenReturn(false);

        expectThrows(AssertionError.class, () -> expect(LOGIN).click());
        verify(button, never()).click();
    }

    @Test
    public void click_elementCovered_failsNamingTheCoveringElement() {
        script("elementFromPoint", "<div class='cookie-banner'>");

        AssertionError e = expectThrows(AssertionError.class, () -> expect(LOGIN).click());
        assertTrue(e.getMessage().contains("is covered by <div class='cookie-banner'>"), e.getMessage());
        verify(button, never()).click();
    }

    @Test
    public void click_intercepted_fails() {
        doThrow(new ElementClickInterceptedException("element click intercepted")).when(button).click();

        AssertionError e = expectThrows(AssertionError.class, () -> expect(LOGIN).click());
        assertTrue(e.getMessage().contains("Click not performed on"));
    }

    @Test
    public void click_eventNotReceived_fails() {
        script("__testflyClicked === true", false);

        AssertionError e = expectThrows(AssertionError.class, () -> expect(LOGIN).click());
        assertTrue(e.getMessage().contains("Click did not reach"));
        stepLoggerMock.verify(() -> StepLogger.step("Clicked: " + LOGIN, StepStatus.PASS), never());
    }

    @Test
    public void click_pageChangedBeforeReadBack_countsAsDelivered() {
        lenient().when(((JavascriptExecutor) driver).executeScript(
                argThat((String s) -> s != null && s.contains("__testflyClicked === true")), any()))
                .thenThrow(new StaleElementReferenceException("navigated"));

        expect(LOGIN).click();
        stepLoggerMock.verify(() -> StepLogger.step("Clicked: " + LOGIN, StepStatus.PASS));
    }

    @Test
    public void click_openedAlert_countsAsDeliveredWithoutRunningScript() {
        WebDriver.TargetLocator target = mock(WebDriver.TargetLocator.class);
        when(driver.switchTo()).thenReturn(target);
        when(target.alert()).thenReturn(mock(org.openqa.selenium.Alert.class));

        expect(LOGIN).click();

        // Reading the probe would make the browser dismiss the alert.
        verify((JavascriptExecutor) driver, never()).executeScript(
                argThat((String s) -> s != null && s.contains("__testflyClicked === true")), any());
        stepLoggerMock.verify(() -> StepLogger.step("Clicked: " + LOGIN, StepStatus.PASS));
    }

    @Test
    public void click_softMode_recordsFailureAndContinues() {
        script("elementFromPoint", "<div class='modal'>");

        expect(LOGIN).softly().as("Header login").click();

        verify(button, never()).click();
        assertTrue(SoftAssertions.get().hasFailed());
        String failure = SoftAssertions.get().getFailures().get(0);
        assertTrue(failure.startsWith("[Header login] Click not performed"), failure);
    }
}
