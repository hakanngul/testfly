package io.testfly.unit.assertion;

import io.testfly.assertion.LocatorAssert;
import io.testfly.assertion.SeleniumAssert;
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

import java.util.Arrays;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
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
    }

    @AfterMethod
    public void tearDown() {
        if (stepLoggerMock != null) stepLoggerMock.close();
        if (contextMock != null) contextMock.close();
        if (driverManagerMock != null) driverManagerMock.close();
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
}
