package io.testfly.unit;

import io.testfly.config.TestFlyConfig;
import io.testfly.driver.DriverManager;
import io.testfly.internal.TestFlyContext;
import io.testfly.wait.WaitEngine;
import org.mockito.MockedStatic;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.testng.Assert.*;

/**
 * Unit tests for the newer {@link WaitEngine} conditions
 * ({@code waitForAttribute}, {@code waitForUrlMatches}, {@code waitForTextMatches}).
 *
 * <p>Uses a mocked {@link WebDriver} whose state already satisfies each condition,
 * so {@code WebDriverWait} succeeds on the first poll — no real browser required.
 */
public class WaitEngineTest {

    private WebDriver mockDriver;
    private MockedStatic<DriverManager> driverManagerMock;
    private MockedStatic<TestFlyContext> contextMock;

    @BeforeMethod
    public void setup() {
        mockDriver = mock(WebDriver.class);

        driverManagerMock = mockStatic(DriverManager.class);
        driverManagerMock.when(DriverManager::getDriver).thenReturn(mockDriver);

        // Supply a config so createWait() can read timeouts.explicit
        TestFlyConfig.Timeouts timeouts = new TestFlyConfig.Timeouts();
        timeouts.setExplicit(2);
        TestFlyConfig config = new TestFlyConfig();
        config.setTimeouts(timeouts);

        contextMock = mockStatic(TestFlyContext.class);
        contextMock.when(TestFlyContext::getConfig).thenReturn(config);
    }

    @AfterMethod
    public void teardown() {
        driverManagerMock.close();
        contextMock.close();
    }

    // ── waitForAttribute (exact match) ─────────────────────────────────────────

    @Test
    public void waitForAttribute_returnsElement_whenAttributeMatchesExactly() {
        By locator = By.id("status");
        WebElement element = mock(WebElement.class);
        when(mockDriver.findElement(locator)).thenReturn(element);
        when(element.getAttribute("aria-expanded")).thenReturn("true");

        WebElement result = WaitEngine.waitForAttribute(locator, "aria-expanded", "true");

        assertSame(result, element);
    }

    // ── waitForUrlMatches (regex) ──────────────────────────────────────────────

    @Test
    public void waitForUrlMatches_returnsTrue_whenUrlMatchesRegex() {
        when(mockDriver.getCurrentUrl()).thenReturn("https://shop.test/orders/42");

        assertTrue(WaitEngine.waitForUrlMatches(".*/orders/\\d+"));
    }


    // ── waitForTextMatches (regex) ─────────────────────────────────────────────

    @Test
    public void waitForTextMatches_returnsElement_whenTextMatchesRegex() {
        By locator = By.cssSelector(".total");
        WebElement element = mock(WebElement.class);
        when(mockDriver.findElement(locator)).thenReturn(element);
        when(element.getText()).thenReturn("$19.99");

        WebElement result = WaitEngine.waitForTextMatches(locator, "\\$\\d+\\.\\d{2}");

        assertSame(result, element);
    }

    // ── waitForEnabled / waitForDisabled ───────────────────────────────────────

    @Test
    public void waitForEnabled_returnsElement_whenElementIsClickable() {
        By locator = By.id("submit");
        WebElement element = mock(WebElement.class);
        when(element.isDisplayed()).thenReturn(true);
        when(element.isEnabled()).thenReturn(true);
        when(mockDriver.findElement(locator)).thenReturn(element);

        WebElement result = WaitEngine.waitForEnabled(locator);

        assertSame(result, element);
    }

    @Test
    public void waitForDisabled_returnsTrue_whenElementIsDisabled() {
        By locator = By.id("submit");
        WebElement element = mock(WebElement.class);
        when(mockDriver.findElement(locator)).thenReturn(element);
        when(element.getAttribute("disabled")).thenReturn("true");
        when(element.getDomAttribute("disabled")).thenReturn("true");

        assertTrue(WaitEngine.waitForDisabled(locator));
    }

    // ── waitForSelected ────────────────────────────────────────────────────────

    @Test
    public void waitForSelected_returnsTrue_whenElementIsSelected() {
        By locator = By.id("terms");
        WebElement element = mock(WebElement.class);
        when(mockDriver.findElement(locator)).thenReturn(element);
        when(element.isSelected()).thenReturn(true);

        assertTrue(WaitEngine.waitForSelected(locator));
    }

    // ── waitForNumberOfWindowsToBe ─────────────────────────────────────────────

    @Test
    public void waitForNumberOfWindowsToBe_returnsTrue_whenCountMatches() {
        when(mockDriver.getWindowHandles()).thenReturn(java.util.Set.of("win-1", "win-2"));

        assertTrue(WaitEngine.waitForNumberOfWindowsToBe(2));
    }

    // ── waitForFrameAvailableAndSwitchToIt ─────────────────────────────────────

    @Test
    public void waitForFrameAvailableAndSwitchToIt_switchesDriverToFrame() {
        By locator = By.id("payment-iframe");
        WebElement frame = mock(WebElement.class);
        WebDriver.TargetLocator targetLocator = mock(WebDriver.TargetLocator.class);

        when(mockDriver.findElement(locator)).thenReturn(frame);
        when(mockDriver.switchTo()).thenReturn(targetLocator);
        when(targetLocator.frame(frame)).thenReturn(mockDriver);

        WebDriver result = WaitEngine.waitForFrameAvailableAndSwitchToIt(locator);

        assertSame(result, mockDriver);
    }

    // ── waitForMinimumElementCount ─────────────────────────────────────────────

    @Test
    public void waitForMinimumElementCount_returnsElements_whenEnoughPresent() {
        By locator = By.cssSelector(".product-card");
        WebElement card1 = mock(WebElement.class);
        WebElement card2 = mock(WebElement.class);
        when(mockDriver.findElements(locator)).thenReturn(List.of(card1, card2));

        List<WebElement> result = WaitEngine.waitForMinimumElementCount(locator, 2);

        assertEquals(result.size(), 2);
    }
}
