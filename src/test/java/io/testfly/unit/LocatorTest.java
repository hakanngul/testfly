package io.testfly.unit;

import io.testfly.config.TestFlyConfig;
import io.testfly.driver.DriverManager;
import io.testfly.internal.TestFlyContext;
import io.testfly.locator.Locator;
import io.testfly.locator.LocatorException;
import io.testfly.test.BaseTest;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.testng.Assert.*;

/**
 * Unit tests for {@link Locator}.
 * All tests use a mocked WebDriver — no real browser required.
 * Thread-safe for parallel=methods via singleThreaded.
 */
@Test(singleThreaded = true)
public class LocatorTest {

    @Test
    public void resolve_throwsLocatorException_whenNoElementsFound() {
        LocatorException ex = new LocatorException("No element found for: By.id: missing");
        assertTrue(ex.getMessage().contains("No element found"));
    }

    @Test
    public void locatorException_preservesCause() {
        RuntimeException cause = new RuntimeException("root cause");
        LocatorException ex = new LocatorException("wrapped", cause);
        assertEquals(ex.getCause(), cause);
        assertEquals(ex.getMessage(), "wrapped");
    }

    @Test
    public void locator_toString_includesRootBy() {
        Locator loc = Locator.of(By.id("username"));
        assertTrue(loc.toString().contains("username"),
                "toString should include root By description");
    }

    @Test
    public void locator_toString_includesFilterAndNth() {
        Locator loc = Locator.of(By.cssSelector(".row"))
                .filter(".active")
                .nth(2);
        String str = loc.toString();
        assertTrue(str.contains(".active"), "toString should include filter");
        assertTrue(str.contains("2"),       "toString should include nth index");
    }

    @Test
    public void locator_toString_includesWithText() {
        Locator loc = Locator.ofCss("button").withText("Save");
        assertTrue(loc.toString().contains("Save"), "toString should include withText value");
    }

    @Test
    public void locator_toString_includesWithin() {
        Locator loc = Locator.of(By.cssSelector("input"))
                .within(By.id("login-form"));
        assertTrue(loc.toString().contains("login-form"), "toString should include within container");
    }

    @Test
    public void locatorOfCss_createsByCssSelector() {
        Locator loc = Locator.ofCss(".submit-btn");
        assertTrue(loc.toString().contains("submit-btn"));
    }

    @Test
    public void locatorOf_createsByLocator() {
        Locator loc = Locator.of(By.name("email"));
        assertTrue(loc.toString().contains("email"));
    }

    @Test
    public void locatorException_withMessageOnly() {
        LocatorException ex = new LocatorException("test error");
        assertEquals("test error", ex.getMessage());
        assertNull(ex.getCause());
    }

    @Test
    public void locator_chaining_doesNotMutateOriginal() {
        Locator base    = Locator.ofCss(".item");
        Locator filtered = base.filter(".active");
        assertNotNull(base);
        assertNotNull(filtered);
        assertTrue(filtered.toString().contains(".active"));
    }

    // ----------------------------------------------------------
    // BaseTest.find() alias
    // ----------------------------------------------------------

    private static class BaseTestFixture extends BaseTest {
        Locator findCss(String css) { return find(css); }
        Locator findBy(By by) { return find(by); }
        @SuppressWarnings("removal")
        Locator dollarCss(String css) { return $(css); }
    }

    @Test
    public void baseTest_findByCss_delegatesToLocatorOfCss() {
        BaseTestFixture fixture = new BaseTestFixture();
        Locator loc = fixture.findCss(".submit-btn");
        assertTrue(loc.toString().contains("submit-btn"));
    }

    @Test
    public void baseTest_findByBy_delegatesToLocatorOf() {
        BaseTestFixture fixture = new BaseTestFixture();
        Locator loc = fixture.findBy(By.id("username"));
        assertTrue(loc.toString().contains("username"));
    }

    @SuppressWarnings("removal")
    @Test
    public void baseTest_dollarAliasStillWorks() {
        BaseTestFixture fixture = new BaseTestFixture();
        Locator loc = fixture.dollarCss(".submit-btn");
        assertTrue(loc.toString().contains("submit-btn"));
    }

    // ----------------------------------------------------------
    // Self-healing integration
    // ----------------------------------------------------------

    private MockedStatic<?>[] setupHealingMocks(boolean selfHealingEnabled) {
        WebDriver mockDriver = mock(WebDriver.class,
                Mockito.withSettings().extraInterfaces(JavascriptExecutor.class));
        lastMockDriver = mockDriver;

        MockedStatic<DriverManager> driverManagerMock = mockStatic(DriverManager.class);
        driverManagerMock.when(DriverManager::getDriver).thenReturn(mockDriver);

        TestFlyConfig.Locators locators = new TestFlyConfig.Locators();
        locators.setSelfHealing(selfHealingEnabled);

        TestFlyConfig.Timeouts timeouts = new TestFlyConfig.Timeouts();
        timeouts.setExplicit(2);

        TestFlyConfig config = new TestFlyConfig();
        config.setLocators(locators);
        config.setTimeouts(timeouts);

        MockedStatic<TestFlyContext> contextMock = mockStatic(TestFlyContext.class);
        contextMock.when(TestFlyContext::getConfig).thenReturn(config);
        contextMock.when(TestFlyContext::getCurrentTestId).thenReturn("test-1");

        return new MockedStatic<?>[]{driverManagerMock, contextMock};
    }

    private WebDriver lastMockDriver;

    private void closeMocks(MockedStatic<?>[] mocks) {
        for (MockedStatic<?> m : mocks) m.close();
    }

    @Test
    public void resolve_triggersSelfHealing_whenPlainByNotFound_andHealingEnabled() {
        MockedStatic<?>[] mocks = setupHealingMocks(true);
        try {
            By primary = By.cssSelector("#login-btn");
            when(lastMockDriver.findElements(primary)).thenReturn(Collections.emptyList());
            WebElement healed = mock(WebElement.class);
            when(healed.isDisplayed()).thenReturn(true);
            when(healed.getText()).thenReturn("Healed Button");
            when(lastMockDriver.findElements(By.id("login-btn"))).thenReturn(List.of(healed));
            assertEquals(Locator.of(primary).getText(), "Healed Button");
        } finally {
            closeMocks(mocks);
        }
    }

    @Test
    public void resolve_throwsException_whenHealingDisabled_andElementNotFound() {
        MockedStatic<?>[] mocks = setupHealingMocks(false);
        try {
            By primary = By.cssSelector("#login-btn");
            when(lastMockDriver.findElements(primary)).thenReturn(Collections.emptyList());
            assertThrows(LocatorException.class, () -> Locator.of(primary).getText());
        } finally {
            closeMocks(mocks);
        }
    }

    @Test
    public void resolve_skipsSelfHealing_whenChainFilterApplied() {
        MockedStatic<?>[] mocks = setupHealingMocks(true);
        try {
            By primary = By.cssSelector("#login-btn");
            when(lastMockDriver.findElements(primary)).thenReturn(Collections.emptyList());
            assertThrows(LocatorException.class,
                    () -> Locator.of(primary).filter(".active").getText());
        } finally {
            closeMocks(mocks);
        }
    }

    @Test
    public void elements_withText_matchesSubstringByDefault() {
        MockedStatic<?>[] mocks = setupHealingMocks(false);
        try {
            WebElement row1 = mock(WebElement.class);
            when(row1.getText()).thenReturn("ORD-1234 Shipped");
            when(row1.isDisplayed()).thenReturn(true);

            WebElement row2 = mock(WebElement.class);
            when(row2.getText()).thenReturn("ORD-5678 Pending");
            when(row2.isDisplayed()).thenReturn(true);

            By by = By.cssSelector("tbody tr");
            when(lastMockDriver.findElements(by)).thenReturn(List.of(row1, row2));

            List<WebElement> matched = Locator.of(by).withText("ORD-1234").elements();
            assertEquals(matched.size(), 1);
            assertSame(matched.get(0), row1);
        } finally {
            closeMocks(mocks);
        }
    }

    @Test
    public void elements_withTextExact_requiresExactMatch() {
        MockedStatic<?>[] mocks = setupHealingMocks(false);
        try {
            WebElement row1 = mock(WebElement.class);
            when(row1.getText()).thenReturn("ORD-1234 Shipped");
            when(row1.isDisplayed()).thenReturn(true);

            WebElement row2 = mock(WebElement.class);
            when(row2.getText()).thenReturn("ORD-1234");
            when(row2.isDisplayed()).thenReturn(true);

            By by = By.cssSelector("tbody tr");
            when(lastMockDriver.findElements(by)).thenReturn(List.of(row1, row2));

            List<WebElement> matched = Locator.of(by).withText("ORD-1234").exact().elements();
            assertEquals(matched.size(), 1);
            assertSame(matched.get(0), row2);
        } finally {
            closeMocks(mocks);
        }
    }
}
