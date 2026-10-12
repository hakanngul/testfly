package io.testfly.unit.assertion;

import io.testfly.assertion.SeleniumAssert;
import io.testfly.assertion.SoftAssertions;
import io.testfly.config.TestFlyConfig;
import io.testfly.driver.DriverManager;
import io.testfly.internal.TestFlyContext;
import io.testfly.locator.Locator;
import io.testfly.steps.StepLogger;
import org.mockito.MockedStatic;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * {@code assertThat(Locator)} resolves the same element set as the {@link Locator} itself:
 * {@code count()} sees every match, single-element matchers prefer the first visible match.
 */
@Test(singleThreaded = true)
public class LocatorChainAssertTest {

    private static final By CARDS = By.cssSelector(".card");

    private WebDriver mockDriver;
    private MockedStatic<DriverManager> driverManagerMock;
    private MockedStatic<TestFlyContext> contextMock;
    private MockedStatic<StepLogger> stepLoggerMock;

    @BeforeMethod
    public void setUp() {
        mockDriver = mock(WebDriver.class);
        // Real drivers hand a non-remotable By (the Locator bridge) back to the By itself.
        lenient().when(mockDriver.findElement(argThat(LocatorChainAssertTest::isLocatorBridge)))
                .thenAnswer(inv -> inv.<By>getArgument(0).findElement(mockDriver));
        lenient().when(mockDriver.findElements(argThat(LocatorChainAssertTest::isLocatorBridge)))
                .thenAnswer(inv -> inv.<By>getArgument(0).findElements(mockDriver));

        driverManagerMock = mockStatic(DriverManager.class);
        driverManagerMock.when(DriverManager::getDriver).thenReturn(mockDriver);

        TestFlyConfig.Timeouts timeouts = new TestFlyConfig.Timeouts();
        timeouts.setExplicit(1);
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
        stepLoggerMock.close();
        contextMock.close();
        driverManagerMock.close();
    }

    private static boolean isLocatorBridge(By by) {
        return by != null && by.toString().startsWith("Locator[");
    }

    private static WebElement element(boolean displayed, String text) {
        WebElement el = mock(WebElement.class);
        lenient().when(el.isDisplayed()).thenReturn(displayed);
        lenient().when(el.getText()).thenReturn(text);
        return el;
    }

    @Test
    public void count_includesHiddenMatches() {
        List<WebElement> cards = List.of(element(true, "a"), element(false, "b"));
        when(mockDriver.findElements(CARDS)).thenReturn(cards);

        SeleniumAssert.assertThat(Locator.of(CARDS)).count(2);
    }

    @Test
    public void hasText_prefersFirstVisibleMatch() {
        List<WebElement> cards = List.of(element(false, "Mobile"), element(true, "Desktop"));
        when(mockDriver.findElements(CARDS)).thenReturn(cards);

        SeleniumAssert.assertThat(Locator.of(CARDS)).hasText("Desktop");
    }

    @Test
    public void hasAttribute_resolvesHiddenElement() {
        WebElement hiddenInput = element(false, "");
        when(hiddenInput.getAttribute("value")).thenReturn("token-1");
        when(mockDriver.findElements(CARDS)).thenReturn(List.of(hiddenInput));

        SeleniumAssert.assertThat(Locator.of(CARDS)).hasAttribute("value", "token-1");
    }

    @Test
    public void isHidden_passesForHiddenAndMissingMatches() {
        List<WebElement> hidden = List.of(element(false, ""));
        when(mockDriver.findElements(CARDS)).thenReturn(hidden);
        SeleniumAssert.assertThat(Locator.of(CARDS)).isHidden();

        By missing = By.id("missing");
        when(mockDriver.findElements(missing)).thenReturn(List.of());
        SeleniumAssert.assertThat(Locator.of(missing)).isHidden();
    }

    @Test
    public void isVisible_reportsNotDisplayedWhenOnlyHiddenMatches() {
        List<WebElement> hidden = List.of(element(false, ""));
        when(mockDriver.findElements(CARDS)).thenReturn(hidden);

        AssertionError error = expectThrows(AssertionError.class,
                () -> SeleniumAssert.assertThat(Locator.of(CARDS)).isVisible());
        assertTrue(error.getMessage().contains("actual: [not displayed]"), error.getMessage());
    }

    @Test
    public void hasText_trimsElementText() {
        By title = By.id("title");
        WebElement heading = element(true, "  Dashboard \n");
        when(mockDriver.findElement(title)).thenReturn(heading);

        SeleniumAssert.assertThat(title).hasText("Dashboard");
    }
}
