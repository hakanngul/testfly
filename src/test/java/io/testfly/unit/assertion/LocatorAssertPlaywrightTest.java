package io.testfly.unit.assertion;

import io.testfly.assertion.LocatorAssert;
import io.testfly.assertion.SeleniumAssert;
import io.testfly.assertion.SoftAssertions;
import io.testfly.config.TestFlyConfig;
import io.testfly.driver.DriverManager;
import io.testfly.internal.TestFlyContext;
import io.testfly.locator.Role;
import io.testfly.steps.StepLogger;
import org.mockito.MockedStatic;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;
import static org.testng.Assert.expectThrows;

/**
 * Unit tests for the assertions added for parity with Playwright for Java's
 * {@code LocatorAssertions}. All WebDriver interactions are mocked.
 */
@Test(singleThreaded = true)
public class LocatorAssertPlaywrightTest {

    private static final By EL = By.id("el");

    private WebDriver driver;
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

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private static LocatorAssert expect() {
        return SeleniumAssert.assertThat(EL).within(Duration.ofMillis(300));
    }

    private WebElement found(WebElement... els) {
        when(driver.findElements(EL)).thenReturn(Arrays.asList(els));
        return els.length > 0 ? els[0] : null;
    }

    private static WebElement element(String tag) {
        WebElement el = mock(WebElement.class);
        lenient().when(el.getTagName()).thenReturn(tag);
        lenient().when(el.isDisplayed()).thenReturn(true);
        lenient().when(el.isEnabled()).thenReturn(true);
        return el;
    }

    private static WebElement withText(String text) {
        WebElement el = element("li");
        lenient().when(el.getText()).thenReturn(text);
        return el;
    }

    private void jsReturns(Object result) {
        when(((JavascriptExecutor) driver).executeScript(anyString(), any(Object[].class))).thenReturn(result);
    }

    /** JSON the accessibility probe returns for one element. */
    private static String a11yNode(String role, String name, String description) {
        return "{\"role\":\"" + role + "\",\"name\":\"" + name + "\",\"description\":\"" + description
                + "\",\"attrs\":{}}";
    }

    // ---------------------------------------------------------------
    // State
    // ---------------------------------------------------------------

    @Test
    public void isAttached_presentPasses_absentFails() {
        found(element("div"));
        expect().isAttached();

        when(driver.findElements(EL)).thenReturn(Collections.emptyList());
        expectThrows(AssertionError.class, () -> expect().isAttached());
    }

    @Test
    public void isEmpty_inputWithEmptyValue_passes() {
        WebElement input = found(element("input"));
        when(input.getDomProperty("value")).thenReturn("");
        expect().isEmpty();
    }

    @Test(expectedExceptions = AssertionError.class)
    public void isEmpty_inputWithSpaces_fails() {
        WebElement input = found(element("INPUT"));
        when(input.getDomProperty("value")).thenReturn("   ");
        expect().isEmpty();
    }

    @Test
    public void isEmpty_divWithWhitespaceOnly_passes() {
        WebElement div = found(element("div"));
        when(div.getDomProperty("textContent")).thenReturn("  \n ");
        expect().isEmpty();
    }

    @Test(expectedExceptions = AssertionError.class)
    public void isEmpty_divWithText_fails() {
        WebElement div = found(element("div"));
        when(div.getDomProperty("textContent")).thenReturn("3 items");
        expect().isEmpty();
    }

    @Test
    public void isIndeterminate_mixedCheckbox_passes() {
        WebElement box = found(element("input"));
        when(box.getDomProperty("indeterminate")).thenReturn("true");
        expect().isIndeterminate();
    }

    @Test
    public void isInViewport_ratio() {
        found(element("div"));
        jsReturns(0.6);
        expect().isInViewport(0.5);
        expectThrows(AssertionError.class, () -> expect().isInViewport(0.75));

        jsReturns(0.01);
        expect().isInViewport(0);
        expectThrows(IllegalArgumentException.class, () -> expect().isInViewport(1.5));
    }

    @Test
    public void hasCount_matchesCount() {
        found(withText("a"), withText("b"));
        expect().hasCount(2);
    }

    // ---------------------------------------------------------------
    // Text
    // ---------------------------------------------------------------

    @Test
    public void hasText_pattern_findsPartialMatch() {
        found(withText("Order #123 placed"));
        expect().hasText(Pattern.compile("#\\d+")).containsText(Pattern.compile("placed$"));
        expectThrows(AssertionError.class, () -> expect().hasText(Pattern.compile("^#\\d+$")));
    }

    @Test
    public void hasText_arrays_matchAllElementsInOrder() {
        found(withText("Home"), withText(" Shoes "));
        expect().hasText(new String[] { "Home", "Shoes" })
                .hasText(new Pattern[] { Pattern.compile("^Ho"), Pattern.compile("oes") });
        expectThrows(AssertionError.class, () -> expect().hasText(new String[] { "Shoes", "Home" }));
    }

    @Test
    public void containsText_array_inOrderSubsequence() {
        found(withText("Home"), withText("Products"), withText("Running shoes"));
        expect().containsText(new String[] { "Home", "shoes" });
        expectThrows(AssertionError.class, () -> expect().containsText(new String[] { "shoes", "Home" }));
    }

    // ---------------------------------------------------------------
    // Value
    // ---------------------------------------------------------------

    @Test
    public void hasValue_pattern() {
        WebElement input = found(element("input"));
        when(input.getDomProperty("value")).thenReturn("admin@example.com");
        expect().hasValue(Pattern.compile("@example\\.com$"));
    }

    @Test
    public void hasValues_multiSelect() {
        WebElement select = found(element("select"));
        lenient().when(select.getDomAttribute("multiple")).thenReturn("true");
        WebElement red = element("option");
        WebElement green = element("option");
        WebElement blue = element("option");
        when(red.isSelected()).thenReturn(true);
        when(blue.isSelected()).thenReturn(true);
        when(red.getDomProperty("value")).thenReturn("red");
        when(blue.getDomProperty("value")).thenReturn("blue");
        when(select.findElements(By.tagName("option"))).thenReturn(List.of(red, green, blue));

        expect().hasValues("red", "blue").hasValues(Pattern.compile("^r"), Pattern.compile("^b"));
        expectThrows(AssertionError.class, () -> expect().hasValues("red"));
    }

    // ---------------------------------------------------------------
    // Attributes, classes, CSS, id, JS property, role
    // ---------------------------------------------------------------

    @Test
    public void hasAttribute_pattern_and_hasId_pattern() {
        WebElement el = found(element("a"));
        when(el.getDomAttribute("href")).thenReturn("/orders/42");
        when(el.getDomAttribute("id")).thenReturn("order-42");
        expect().hasAttribute("href", Pattern.compile("/orders/\\d+")).hasId(Pattern.compile("^order-"));
    }

    @Test
    public void hasClass_pattern_and_containsClass() {
        WebElement el = found(element("button"));
        when(el.getDomAttribute("class")).thenReturn("btn primary large");
        expect().hasClass(Pattern.compile("primary")).containsClass("large btn");
        expectThrows(AssertionError.class, () -> expect().containsClass("btn disabled"));
    }

    @Test
    public void containsClass_list_oneEntryPerElement() {
        WebElement first = element("li");
        WebElement second = element("li");
        when(first.getDomAttribute("class")).thenReturn("item active");
        when(second.getDomAttribute("class")).thenReturn("item");
        found(first, second);

        expect().containsClass(List.of("active", "item"));
        expectThrows(AssertionError.class, () -> expect().containsClass(List.of("item", "active")));
    }

    @Test
    public void hasCSS_valueAndPattern() {
        WebElement el = found(element("div"));
        when(el.getCssValue("color")).thenReturn("rgb(255, 0, 0)");
        expect().hasCSS("color", "rgb(255, 0, 0)").hasCSS("color", Pattern.compile("255, 0, 0"));
    }

    @Test
    public void hasJSProperty_comparesNumbersByValue() {
        found(element("input"));
        jsReturns(1L);
        expect().hasJSProperty("tabIndex", 1).hasJSProperty("tabIndex", 1.0);
        expectThrows(AssertionError.class, () -> expect().hasJSProperty("tabIndex", 2));

        jsReturns(true);
        expect().hasJSProperty("checked", true);
    }

    @Test
    public void hasRole_enum() {
        found(element("button"));
        jsReturns(a11yNode("button", "", ""));
        expect().hasRole(Role.BUTTON);
    }

    // ---------------------------------------------------------------
    // Accessibility
    // ---------------------------------------------------------------

    @Test
    public void hasAccessibleName_pattern() {
        found(element("button"));
        jsReturns(a11yNode("button", "Submit order", ""));
        expect().hasAccessibleName(Pattern.compile("^Submit"))
                .hasAccessibleName(Pattern.compile("(?i)submit"));
    }

    @Test
    public void hasAccessibleDescription_isWhitespaceNormalized() {
        found(element("input"));
        jsReturns(a11yNode("textbox", "", "Must be at least 8 characters"));
        expect().hasAccessibleDescription("  Must be   at least\n8 characters ")
                .hasAccessibleDescription(Pattern.compile("8 characters"));
    }

    @Test
    public void hasAccessibleErrorMessage_presentPasses_emptyFails() {
        found(element("input"));
        jsReturns("Email is invalid");
        expect().hasAccessibleErrorMessage("Email is invalid")
                .hasAccessibleErrorMessage(Pattern.compile("invalid"));

        jsReturns("");
        expectThrows(AssertionError.class, () -> expect().hasAccessibleErrorMessage("Email is invalid"));
    }
}
