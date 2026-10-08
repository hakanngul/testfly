package io.testfly.unit;

import io.testfly.config.TestFlyConfig;
import io.testfly.driver.DriverManager;
import io.testfly.internal.TestFlyContext;
import io.testfly.wait.WaitEngine;
import org.mockito.MockedStatic;
import org.openqa.selenium.By;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TimeoutException;
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
 * Unit tests for the newer {@link WaitEngine} conditions.
 * Thread-safe for parallel=methods via singleThreaded.
 */
@Test(singleThreaded = true)
public class WaitEngineTest {

    private WebDriver mockDriver;
    private MockedStatic<DriverManager> driverManagerMock;
    private MockedStatic<TestFlyContext> contextMock;
    private TestFlyConfig.Timeouts timeouts;

    @BeforeMethod
    public void setup() {
        mockDriver = mock(WebDriver.class);
        driverManagerMock = mockStatic(DriverManager.class);
        driverManagerMock.when(DriverManager::getDriver).thenReturn(mockDriver);
        timeouts = new TestFlyConfig.Timeouts();
        timeouts.setExplicit(2);
        TestFlyConfig config = new TestFlyConfig();
        config.setTimeouts(timeouts);
        contextMock = mockStatic(TestFlyContext.class);
        contextMock.when(TestFlyContext::getConfig).thenReturn(config);
    }

    @AfterMethod
    public void teardown() {
        if (driverManagerMock != null) driverManagerMock.close();
        if (contextMock != null) contextMock.close();
    }

    @Test
    public void waitForAttribute_returnsElement_whenAttributeMatchesExactly() {
        By locator = By.id("status");
        WebElement element = mock(WebElement.class);
        when(mockDriver.findElement(locator)).thenReturn(element);
        when(element.getAttribute("aria-expanded")).thenReturn("true");
        WebElement result = WaitEngine.waitForAttribute(locator, "aria-expanded", "true");
        assertSame(result, element);
    }

    @Test
    public void waitForUrlMatches_returnsTrue_whenUrlMatchesRegex() {
        when(mockDriver.getCurrentUrl()).thenReturn("https://shop.test/orders/42");
        assertTrue(WaitEngine.waitForUrlMatches(".*/orders/\\d+"));
    }

    @Test
    public void waitForTextMatches_returnsElement_whenTextMatchesRegex() {
        By locator = By.cssSelector(".total");
        WebElement element = mock(WebElement.class);
        when(mockDriver.findElement(locator)).thenReturn(element);
        when(element.getText()).thenReturn("$19.99");
        WebElement result = WaitEngine.waitForTextMatches(locator, "\\$\\d+\\.\\d{2}");
        assertSame(result, element);
    }

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

    @Test
    public void waitForSelected_returnsTrue_whenElementIsSelected() {
        By locator = By.id("terms");
        WebElement element = mock(WebElement.class);
        when(mockDriver.findElement(locator)).thenReturn(element);
        when(element.isSelected()).thenReturn(true);
        assertTrue(WaitEngine.waitForSelected(locator));
    }

    @Test
    public void waitForNumberOfWindowsToBe_returnsTrue_whenCountMatches() {
        when(mockDriver.getWindowHandles()).thenReturn(java.util.Set.of("win-1", "win-2"));
        assertTrue(WaitEngine.waitForNumberOfWindowsToBe(2));
    }

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

    @Test
    public void waitForMinimumElementCount_returnsElements_whenEnoughPresent() {
        By locator = By.cssSelector(".product-card");
        WebElement card1 = mock(WebElement.class);
        WebElement card2 = mock(WebElement.class);
        when(mockDriver.findElements(locator)).thenReturn(List.of(card1, card2));
        List<WebElement> result = WaitEngine.waitForMinimumElementCount(locator, 2);
        assertEquals(result.size(), 2);
    }

    @Test
    public void waitForPageLoad_withDriver_completesWhenReadyStateComplete() {
        org.openqa.selenium.JavascriptExecutor jsDriver = mock(org.openqa.selenium.JavascriptExecutor.class,
                org.mockito.Mockito.withSettings().extraInterfaces(WebDriver.class));
        when(jsDriver.executeScript("return document.readyState")).thenReturn("complete");
        WaitEngine.waitForPageLoad((WebDriver) jsDriver);
    }

    @Test
    public void waitForPageLoad_withNullDriver_doesNotThrow() {
        WaitEngine.waitForPageLoad(null);
    }

    // ----------------------------------------------------------
    // Timeout, heal and interruption semantics (TST-005 core)
    // ----------------------------------------------------------

    @Test
    public void waitForVisible_throwsTimeoutException_whenElementNeverAppears() {
        timeouts.setExplicit(1);
        By locator = By.id("never");
        when(mockDriver.findElement(locator)).thenThrow(new org.openqa.selenium.NoSuchElementException("gone"));
        assertThrows(TimeoutException.class, () -> WaitEngine.waitForVisible(locator));
    }

    @Test
    public void waitForVisible_throwsTimeoutException_whenElementStaysHidden() {
        timeouts.setExplicit(1);
        By locator = By.id("hidden");
        WebElement element = mock(WebElement.class);
        when(element.isDisplayed()).thenReturn(false);
        when(mockDriver.findElement(locator)).thenReturn(element);
        assertThrows(TimeoutException.class, () -> WaitEngine.waitForVisible(locator));
    }

    @Test
    public void waitForClickable_throwsTimeoutException_whenElementStaysDisabled() {
        timeouts.setExplicit(1);
        By locator = By.id("disabled");
        WebElement element = mock(WebElement.class);
        when(element.isDisplayed()).thenReturn(true);
        when(element.isEnabled()).thenReturn(false);
        when(mockDriver.findElement(locator)).thenReturn(element);
        assertThrows(TimeoutException.class, () -> WaitEngine.waitForClickable(locator));
    }

    @Test
    public void waitForClickable_returnsElement_onceItBecomesEnabled() {
        By locator = By.id("later");
        WebElement element = mock(WebElement.class);
        when(element.isDisplayed()).thenReturn(true);
        when(element.isEnabled()).thenReturn(false, true);
        when(mockDriver.findElement(locator)).thenReturn(element);
        assertSame(WaitEngine.waitForClickable(locator), element);
    }

    @Test
    public void waitForVisible_returnsHealedElement_whenTimeoutAndHealingEnabled() {
        timeouts.setExplicit(1);
        TestFlyConfig.Locators locators = new TestFlyConfig.Locators();
        locators.setSelfHealing(true);
        contextMock.when(TestFlyContext::getConfig).thenReturn(configWith(locators));
        contextMock.when(TestFlyContext::getCurrentTestId).thenReturn("heal-1");

        WebDriver jsDriver = mock(WebDriver.class,
                org.mockito.Mockito.withSettings().extraInterfaces(org.openqa.selenium.JavascriptExecutor.class));
        driverManagerMock.when(DriverManager::getDriver).thenReturn(jsDriver);

        By primary = By.cssSelector("#login-btn");
        when(jsDriver.findElement(primary)).thenThrow(new org.openqa.selenium.NoSuchElementException("gone"));
        WebElement healed = mock(WebElement.class);
        when(healed.isDisplayed()).thenReturn(true);
        when(jsDriver.findElements(By.id("login-btn"))).thenReturn(List.of(healed));

        assertSame(WaitEngine.waitForVisible(primary), healed);
    }

    @Test
    public void waitForInvisible_returnsTrue_whenElementDisappears() {
        By locator = By.id("spinner");
        WebElement element = mock(WebElement.class);
        when(element.isDisplayed()).thenReturn(true, false);
        when(mockDriver.findElement(locator)).thenReturn(element);
        assertTrue(WaitEngine.waitForInvisible(locator));
    }

    @Test
    public void waitForInvisible_throwsTimeoutException_whenElementStaysVisible() {
        timeouts.setExplicit(1);
        By locator = By.id("spinner");
        WebElement element = mock(WebElement.class);
        when(element.isDisplayed()).thenReturn(true);
        when(mockDriver.findElement(locator)).thenReturn(element);
        assertThrows(TimeoutException.class, () -> WaitEngine.waitForInvisible(locator));
    }

    @Test
    public void waitForStaleness_returnsTrue_whenElementIsDetached() {
        WebElement element = mock(WebElement.class);
        when(element.isEnabled()).thenThrow(new StaleElementReferenceException("detached"));
        assertTrue(WaitEngine.waitForStaleness(element));
    }

    @Test
    public void waitForStaleness_throwsTimeoutException_whenElementStaysAttached() {
        timeouts.setExplicit(1);
        WebElement element = mock(WebElement.class);
        when(element.isEnabled()).thenReturn(true);
        assertThrows(TimeoutException.class, () -> WaitEngine.waitForStaleness(element));
    }

    @Test
    public void waitMillis_sleepsForRequestedDuration() {
        long start = System.nanoTime();
        WaitEngine.waitMillis(150);
        assertTrue((System.nanoTime() - start) / 1_000_000 >= 140);
    }

    @Test
    public void waitMillis_restoresInterruptFlag_andThrows_whenInterrupted() {
        Thread.currentThread().interrupt();
        try {
            RuntimeException ex = expectThrows(RuntimeException.class, () -> WaitEngine.waitMillis(5_000));
            assertTrue(ex.getCause() instanceof InterruptedException);
            assertTrue(Thread.currentThread().isInterrupted(), "interrupt flag must be restored");
        } finally {
            Thread.interrupted(); // clear the flag so later tests are unaffected
        }
    }

    private TestFlyConfig configWith(TestFlyConfig.Locators locators) {
        TestFlyConfig config = new TestFlyConfig();
        config.setTimeouts(timeouts);
        config.setLocators(locators);
        return config;
    }
}
