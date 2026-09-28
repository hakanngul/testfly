package io.testfly.unit;

import io.testfly.config.TestFlyConfig;
import io.testfly.driver.DriverManager;
import io.testfly.internal.TestFlyContext;
import io.testfly.test.BasePage;
import org.mockito.MockedStatic;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

/**
 * Unit tests for {@link BasePage} and its inherited navigation and window
 * support.
 */
@Test(singleThreaded = true)
public class BasePageTest {

    private interface MockDriverWithJs extends WebDriver, JavascriptExecutor {
    }

    private MockDriverWithJs mockDriver;
    private MockedStatic<DriverManager> driverManagerMock;
    private MockedStatic<TestFlyContext> contextMock;

    // Concrete test page for unit testing
    private static class DummyPage extends BasePage {
        public DummyPage() {
            super();
        }

        public DummyPage(WebDriver driver) {
            super(driver);
        }

        public void doClear(org.openqa.selenium.By locator) {
            clear(locator);
        }

        public void doClearElement(org.openqa.selenium.WebElement element) {
            find(element).clear();
        }
    }

    @BeforeMethod
    public void setup() {
        mockDriver = mock(MockDriverWithJs.class);
        driverManagerMock = mockStatic(DriverManager.class);
        driverManagerMock.when(DriverManager::getDriver).thenReturn(mockDriver);

        TestFlyConfig config = new TestFlyConfig();
        TestFlyConfig.Execution execution = new TestFlyConfig.Execution();
        execution.setBaseUrl("https://example.com");
        config.setExecution(execution);

        TestFlyConfig.Timeouts timeouts = new TestFlyConfig.Timeouts();
        timeouts.setExplicit(2);
        config.setTimeouts(timeouts);

        contextMock = mockStatic(TestFlyContext.class);
        contextMock.when(TestFlyContext::getConfig).thenReturn(config);
    }

    @AfterMethod
    public void teardown() {
        if (driverManagerMock != null)
            driverManagerMock.close();
        if (contextMock != null)
            contextMock.close();
    }

    @Test
    public void constructor_noArg_usesDriverManagerLazily() {
        DummyPage page = new DummyPage();
        assertNotNull(page.getDriver());
        assertSame(page.getDriver(), mockDriver);
    }

    @Test
    public void constructor_withDriver_usesProvidedDriver() {
        WebDriver customDriver = mock(WebDriver.class);
        DummyPage page = new DummyPage(customDriver);
        assertSame(page.getDriver(), customDriver);
    }

    @Test
    public void open_navigatesToBaseUrl() {
        DummyPage page = new DummyPage();
        page.open();
        verify(mockDriver).get("https://example.com");
    }

    @Test
    public void open_withRelativePath_concatenatesUrl() {
        DummyPage page = new DummyPage();
        page.open("/login");
        verify(mockDriver).get("https://example.com/login");
    }

    @Test
    public void navigateTo_callsOpen() {
        DummyPage page = new DummyPage();
        page.navigateTo("https://other.com/path");
        verify(mockDriver).get("https://other.com/path");
    }

    @Test
    public void getCurrentUrl_and_getTitle() {
        when(mockDriver.getCurrentUrl()).thenReturn("https://example.com/dashboard");
        when(mockDriver.getTitle()).thenReturn("Dashboard Page");

        DummyPage page = new DummyPage();
        assertEquals(page.getCurrentUrl(), "https://example.com/dashboard");
        assertEquals(page.getTitle(), "Dashboard Page");
    }

    @Test
    public void navigation_back_forward_refresh() {
        WebDriver.Navigation mockNav = mock(WebDriver.Navigation.class);
        when(mockDriver.navigate()).thenReturn(mockNav);
        when(mockDriver.executeScript("return document.readyState")).thenReturn("complete");

        DummyPage page = new DummyPage();
        page.back();
        verify(mockNav).back();

        page.forward();
        verify(mockNav).forward();

        page.refresh();
        verify(mockNav).refresh();
    }

    @Test
    public void window_switchToNewTab_and_switchToMainTab() {
        WebDriver.TargetLocator targetLocator = mock(WebDriver.TargetLocator.class);
        when(mockDriver.switchTo()).thenReturn(targetLocator);
        when(mockDriver.getWindowHandle()).thenReturn("win-1");

        Set<String> handles = new LinkedHashSet<>();
        handles.add("win-1");
        handles.add("win-2");
        when(mockDriver.getWindowHandles()).thenReturn(handles);

        DummyPage page = new DummyPage();
        page.switchToNewTab();
        verify(targetLocator).window("win-2");

        page.switchToMainTab();
        verify(targetLocator).window("win-1");
    }

    @Test
    public void window_zoom_and_scrollBy() {
        DummyPage page = new DummyPage();
        page.zoom(80);
        verify(mockDriver).executeScript("document.body.style.zoom = '80%';");

        page.scrollBy(100, 200);
        verify(mockDriver).executeScript("window.scrollBy(arguments[0], arguments[1]);", 100, 200);
    }

    @Test
    public void contextSupport_ctx_and_suiteCtx_accessible() {
        DummyPage page = new DummyPage();
        page.ctx().set("orderId", "12345");
        assertEquals(page.ctx().get("orderId"), "12345");

        page.suiteCtx().set("token", "abc");
        assertEquals(page.suiteCtx().get("token"), "abc");
    }

    @Test
    public void clear_by_and_webElement_executesRobustClear() {
        org.openqa.selenium.By inputLocator = org.openqa.selenium.By.id("username");
        org.openqa.selenium.WebElement mockElement = mock(org.openqa.selenium.WebElement.class);
        when(mockDriver.findElement(inputLocator)).thenReturn(mockElement);
        when(mockDriver.findElements(inputLocator)).thenReturn(java.util.List.of(mockElement));
        when(mockElement.isDisplayed()).thenReturn(true);

        DummyPage page = new DummyPage();
        page.doClear(inputLocator);

        // Verify robust clear keys and clear() were called
        verify(mockElement, atLeastOnce()).clear();
        verify(mockElement, atLeastOnce()).sendKeys(
                org.openqa.selenium.Keys.chord(org.openqa.selenium.Keys.COMMAND, "a"),
                org.openqa.selenium.Keys.BACK_SPACE);
        verify(mockElement, atLeastOnce()).sendKeys(
                org.openqa.selenium.Keys.chord(org.openqa.selenium.Keys.CONTROL, "a"),
                org.openqa.selenium.Keys.BACK_SPACE);

        // Also test direct WebElement clear via find(element).clear()
        org.openqa.selenium.WebElement anotherElement = mock(org.openqa.selenium.WebElement.class);
        when(anotherElement.isDisplayed()).thenReturn(true);
        page.doClearElement(anotherElement);
        verify(anotherElement, atLeastOnce()).clear();
    }
}
