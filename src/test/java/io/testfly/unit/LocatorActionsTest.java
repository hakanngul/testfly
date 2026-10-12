package io.testfly.unit;

import io.testfly.config.TestFlyConfig;
import io.testfly.driver.DriverManager;
import io.testfly.internal.TestFlyContext;
import io.testfly.locator.Locator;
import io.testfly.steps.StepLogger;
import org.mockito.MockedStatic;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Interactive;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests for the {@link Locator} getters and actions added for form,
 * keyboard, mouse and upload interactions. All WebDriver interactions are
 * mocked.
 */
@Test(singleThreaded = true)
public class LocatorActionsTest {

    private static final By FIELD = By.id("field");

    private WebDriver driver;
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
        TestFlyConfig.Locators locators = new TestFlyConfig.Locators();
        locators.setSelfHealing(false);
        TestFlyConfig config = new TestFlyConfig();
        config.setTimeouts(timeouts);
        config.setLocators(locators);

        contextMock = mockStatic(TestFlyContext.class);
        contextMock.when(TestFlyContext::getConfig).thenReturn(config);
        contextMock.when(TestFlyContext::getCurrentTestId).thenReturn("test-1");

        stepLoggerMock = mockStatic(StepLogger.class);
    }

    @AfterMethod
    public void tearDown() {
        stepLoggerMock.close();
        contextMock.close();
        driverManagerMock.close();
    }

    private WebElement element(By by) {
        WebElement el = mock(WebElement.class);
        lenient().when(el.isDisplayed()).thenReturn(true);
        lenient().when(el.isEnabled()).thenReturn(true);
        when(driver.findElements(by)).thenReturn(List.of(el));
        return el;
    }

    // ---------------------------------------------------------------
    // Value & typing
    // ---------------------------------------------------------------

    @Test
    public void getValue_returnsValueAttribute() {
        WebElement el = element(FIELD);
        when(el.getAttribute("value")).thenReturn("admin");
        assertEquals(Locator.of(FIELD).getValue(), "admin");
    }

    @Test
    public void fill_typesText() {
        WebElement el = element(FIELD);
        Locator.of(FIELD).fill("admin");
        verify(el).sendKeys("admin");
    }

    @Test
    public void press_sendsKeyChord() {
        WebElement el = element(FIELD);
        Locator.of(FIELD).press(Keys.CONTROL, "a");
        verify(el).sendKeys(Keys.chord(Keys.CONTROL, "a"));
    }

    // ---------------------------------------------------------------
    // Checkbox
    // ---------------------------------------------------------------

    @Test
    public void check_unchecked_clicksOnce() {
        WebElement el = element(FIELD);
        when(el.isSelected()).thenReturn(false);
        Locator.of(FIELD).check();
        verify(el, times(1)).click();
    }

    @Test
    public void check_alreadyChecked_doesNotClick() {
        WebElement el = element(FIELD);
        when(el.isSelected()).thenReturn(true);
        Locator.of(FIELD).check();
        verify(el, never()).click();
    }

    @Test
    public void uncheck_checked_clicksOnce() {
        WebElement el = element(FIELD);
        when(el.isSelected()).thenReturn(true);
        Locator.of(FIELD).uncheck();
        verify(el, times(1)).click();
    }

    @Test
    public void isChecked_noElement_returnsFalseWithoutWaiting() {
        when(driver.findElements(FIELD)).thenReturn(Collections.emptyList());
        assertFalse(Locator.of(FIELD).isChecked());
        assertFalse(Locator.of(FIELD).isPresent());
    }

    // ---------------------------------------------------------------
    // Select
    // ---------------------------------------------------------------

    @Test
    public void selectByIndex_clicksOption_andGetSelectedOptionReadsIt() {
        WebElement select = element(FIELD);
        when(select.getTagName()).thenReturn("select");
        WebElement first = mock(WebElement.class);
        WebElement second = mock(WebElement.class);
        lenient().when(first.isEnabled()).thenReturn(true);
        lenient().when(second.isEnabled()).thenReturn(true);
        when(second.getAttribute("index")).thenReturn("1");
        lenient().when(first.getAttribute("index")).thenReturn("0");
        when(second.isSelected()).thenReturn(false, true);
        when(second.getText()).thenReturn(" Germany ");
        when(select.findElements(By.tagName("option"))).thenReturn(List.of(first, second));

        Locator locator = Locator.of(FIELD);
        locator.selectByIndex(1);
        verify(second).click();
        assertEquals(locator.getSelectedOption(), "Germany");
    }

    // ---------------------------------------------------------------
    // Mouse
    // ---------------------------------------------------------------

    @Test
    public void doubleClick_rightClick_dragTo_performActions() {
        element(FIELD);
        By target = By.id("target");
        element(target);

        Locator.of(FIELD).doubleClick();
        Locator.of(FIELD).rightClick();
        Locator.of(FIELD).dragTo(Locator.of(target));

        verify((Interactive) driver, times(3)).perform(anyCollection());
    }

    @Test
    public void focus_runsFocusScript() {
        WebElement el = element(FIELD);
        Locator.of(FIELD).focus();
        verify((JavascriptExecutor) driver).executeScript("arguments[0].focus();", el);
    }

    // ---------------------------------------------------------------
    // Upload
    // ---------------------------------------------------------------

    @Test
    public void upload_hiddenInput_sendsAbsolutePathsJoinedByNewline() throws Exception {
        WebElement input = element(FIELD);
        when(input.isDisplayed()).thenReturn(false);
        Path a = Files.createTempFile("testfly-upload-a", ".txt");
        Path b = Files.createTempFile("testfly-upload-b", ".txt");
        try {
            Locator.of(FIELD).upload(a.toString(), b.toString());
            verify(input).sendKeys(new File(a.toString()).getAbsolutePath() + "\n"
                    + new File(b.toString()).getAbsolutePath());
        } finally {
            Files.deleteIfExists(a);
            Files.deleteIfExists(b);
        }
    }

    // ---------------------------------------------------------------
    // Texts & waitFor
    // ---------------------------------------------------------------

    @Test
    public void allTexts_returnsTrimmedVisibleTexts() {
        WebElement a = mock(WebElement.class);
        WebElement b = mock(WebElement.class);
        when(a.isDisplayed()).thenReturn(true);
        when(b.isDisplayed()).thenReturn(true);
        when(a.getText()).thenReturn(" A ");
        when(b.getText()).thenReturn("B");
        when(driver.findElements(FIELD)).thenReturn(List.of(a, b));
        assertEquals(Locator.of(FIELD).allTexts(), List.of("A", "B"));
    }

    @Test
    public void waitFor_detached_returnsWhenNoMatch() {
        when(driver.findElements(FIELD)).thenReturn(Collections.emptyList());
        Locator.of(FIELD).waitFor(Locator.State.DETACHED);
        Locator.of(FIELD).waitFor(Locator.State.HIDDEN);
    }

    @Test
    public void waitFor_visible_timesOutWhenHidden() {
        WebElement el = element(FIELD);
        when(el.isDisplayed()).thenReturn(false);
        assertThrows(TimeoutException.class, () -> Locator.of(FIELD).waitFor(Locator.State.VISIBLE));
        assertTrue(Locator.of(FIELD).isPresent());
    }
}
