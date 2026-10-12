package io.testfly.unit;

import io.testfly.locator.Locator;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.HasCapabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.WrapsDriver;
import org.testng.annotations.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * Unit tests for {@link Locator#robustClear(WebElement)}: Safari must not
 * receive modifier chords, which safaridriver leaves pressed.
 */
public class LocatorRobustClearTest {

    private static WebElement elementOf(String browserName) {
        WebDriver driver = mock(WebDriver.class, withSettings().extraInterfaces(HasCapabilities.class));
        Capabilities caps = mock(Capabilities.class);
        when(caps.getBrowserName()).thenReturn(browserName);
        when(((HasCapabilities) driver).getCapabilities()).thenReturn(caps);

        WebElement el = mock(WebElement.class, withSettings().extraInterfaces(WrapsDriver.class));
        when(((WrapsDriver) el).getWrappedDriver()).thenReturn(driver);
        return el;
    }

    @Test
    public void robustClear_safari_usesNativeClearOnly() {
        WebElement el = elementOf("Safari");
        Locator.robustClear(el);
        verify(el, never()).sendKeys(any(CharSequence[].class));
        verify(el).clear();
    }

    @Test
    public void robustClear_chrome_sendsSelectAllChordsThenClears() {
        WebElement el = elementOf("chrome");
        Locator.robustClear(el);
        verify(el, times(2)).sendKeys(any(CharSequence[].class));
        verify(el).clear();
    }

    @Test
    public void robustClear_unwrappedElement_keepsChords() {
        WebElement el = mock(WebElement.class);
        Locator.robustClear(el);
        verify(el, times(2)).sendKeys(any(CharSequence[].class));
        verify(el).clear();
    }
}
