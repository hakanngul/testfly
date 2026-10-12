package io.testfly.unit.assertion;

import io.testfly.assertion.LocatorAssert;
import io.testfly.assertion.SeleniumAssert;
import io.testfly.assertion.SoftAssertions;
import io.testfly.config.TestFlyConfig;
import io.testfly.driver.DriverManager;
import io.testfly.internal.TestFlyContext;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests for the form, text, selection, accessibility and negation
 * assertions of {@link LocatorAssert}. All WebDriver interactions are mocked.
 */
@Test(singleThreaded = true)
public class LocatorAssertExtendedTest {

    private static final By FIELD = By.id("field");

    private WebDriver mockDriver;
    private MockedStatic<DriverManager> driverManagerMock;
    private MockedStatic<TestFlyContext> contextMock;
    private MockedStatic<StepLogger> stepLoggerMock;

    @BeforeMethod
    public void setUp() {
        mockDriver = mock(WebDriver.class, withSettings().extraInterfaces(JavascriptExecutor.class));

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
        if (stepLoggerMock != null)
            stepLoggerMock.close();
        if (contextMock != null)
            contextMock.close();
        if (driverManagerMock != null)
            driverManagerMock.close();
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    /** Short timeout so failing assertions finish quickly. */
    private static LocatorAssert expect(By by) {
        return SeleniumAssert.assertThat(by).within(Duration.ofMillis(300));
    }

    private void found(By by, WebElement... els) {
        when(mockDriver.findElements(by)).thenReturn(Arrays.asList(els));
    }

    private void absent(By by) {
        when(mockDriver.findElements(by)).thenReturn(Collections.emptyList());
    }

    private static WebElement input(String value) {
        WebElement el = mock(WebElement.class);
        lenient().when(el.getDomProperty("value")).thenReturn(value);
        lenient().when(el.isDisplayed()).thenReturn(true);
        lenient().when(el.isEnabled()).thenReturn(true);
        lenient().when(el.getText()).thenReturn("");
        return el;
    }

    private static WebElement withText(String text) {
        WebElement el = mock(WebElement.class);
        lenient().when(el.getText()).thenReturn(text);
        return el;
    }

    private void jsReturns(Object result) {
        when(((JavascriptExecutor) mockDriver).executeScript(anyString(), any(Object[].class))).thenReturn(result);
    }

    // ---------------------------------------------------------------
    // Value
    // ---------------------------------------------------------------

    @Test
    public void hasValue_nonEmpty_passes() {
        found(FIELD, input("secret"));
        expect(FIELD).hasValue();
    }

    @Test(expectedExceptions = AssertionError.class)
    public void hasValue_empty_fails() {
        found(FIELD, input(""));
        expect(FIELD).hasValue();
    }

    @Test(expectedExceptions = AssertionError.class)
    public void hasValue_elementAbsent_fails() {
        absent(FIELD);
        expect(FIELD).hasValue();
    }

    @Test
    public void hasEmptyValue_emptyOrNull_passes() {
        found(FIELD, input(""));
        expect(FIELD).hasEmptyValue();
        found(FIELD, input(null));
        expect(FIELD).hasEmptyValue();
    }

    @Test(expectedExceptions = AssertionError.class)
    public void hasEmptyValue_filled_fails() {
        found(FIELD, input("admin"));
        expect(FIELD).hasEmptyValue();
    }

    @Test
    public void containsValue_and_valueMatches_pass() {
        found(FIELD, input("admin@example.com"));
        expect(FIELD).containsValue("@example").valueMatches("[a-z]+@example\\.com");
    }

    @Test(expectedExceptions = AssertionError.class)
    public void valueMatches_partialMatch_fails() {
        found(FIELD, input("admin@example.com"));
        expect(FIELD).valueMatches("admin");
    }

    // ---------------------------------------------------------------
    // Password & input attributes
    // ---------------------------------------------------------------

    @Test
    public void isPasswordHidden_typePassword_passes() {
        WebElement el = input("secret");
        when(el.getDomProperty("type")).thenReturn("password");
        found(FIELD, el);
        expect(FIELD).isPasswordHidden().hasValue().hasType("PASSWORD");
    }

    @Test(expectedExceptions = AssertionError.class)
    public void isPasswordHidden_typeText_fails() {
        WebElement el = input("secret");
        when(el.getDomProperty("type")).thenReturn("text");
        found(FIELD, el);
        expect(FIELD).isPasswordHidden();
    }

    @Test
    public void isPasswordVisible_typeText_passes() {
        WebElement el = input("secret");
        when(el.getDomProperty("type")).thenReturn("text");
        found(FIELD, el);
        expect(FIELD).isPasswordVisible();
    }

    @Test(expectedExceptions = AssertionError.class)
    public void isPasswordVisible_typePassword_fails() {
        WebElement el = input("secret");
        when(el.getDomProperty("type")).thenReturn("password");
        found(FIELD, el);
        expect(FIELD).isPasswordVisible();
    }

    @Test
    public void placeholder_maxLength_required_pass() {
        WebElement el = input("");
        when(el.getDomAttribute("placeholder")).thenReturn("Username");
        when(el.getDomAttribute("maxlength")).thenReturn("20");
        when(el.getDomAttribute("aria-required")).thenReturn("true");
        found(FIELD, el);
        expect(FIELD).hasPlaceholder("Username").hasMaxLength(20).isRequired();
    }

    @Test(expectedExceptions = AssertionError.class)
    public void isRequired_notRequired_fails() {
        found(FIELD, input(""));
        expect(FIELD).isRequired();
    }

    @Test
    public void isEditable_enabledWithoutReadonly_passes() {
        found(FIELD, input(""));
        expect(FIELD).isEditable();
    }

    @Test(expectedExceptions = AssertionError.class)
    public void isEditable_readonly_fails() {
        WebElement el = input("");
        when(el.getDomAttribute("readonly")).thenReturn("true");
        found(FIELD, el);
        expect(FIELD).isEditable();
    }

    @Test
    public void isReadOnly_readonly_passes() {
        WebElement el = input("");
        when(el.getDomAttribute("readonly")).thenReturn("true");
        found(FIELD, el);
        expect(FIELD).isReadOnly();
    }

    // ---------------------------------------------------------------
    // HTML5 validation
    // ---------------------------------------------------------------

    @Test
    public void isValid_checkValidityTrue_passes() {
        found(FIELD, input("ok"));
        jsReturns(true);
        expect(FIELD).isValid();
    }

    @Test(expectedExceptions = AssertionError.class)
    public void isValid_checkValidityFalse_fails() {
        found(FIELD, input(""));
        jsReturns(false);
        expect(FIELD).isValid();
    }

    @Test
    public void isInvalid_checkValidityFalse_passes() {
        found(FIELD, input(""));
        jsReturns(false);
        expect(FIELD).isInvalid();
    }

    @Test
    public void hasValidationMessage_contains_passes() {
        found(FIELD, input(""));
        jsReturns("Please fill out this field.");
        expect(FIELD).hasValidationMessage("fill out");
    }

    // ---------------------------------------------------------------
    // Text
    // ---------------------------------------------------------------

    @Test
    public void hasTextMatching_and_ignoringCase_pass() {
        found(FIELD, withText("  Order #123 placed "));
        expect(FIELD).hasTextMatching("Order #\\d+ placed").hasTextIgnoringCase("order #123 PLACED");
    }

    @Test
    public void hasTexts_sameOrder_passes() {
        found(FIELD, withText("A"), withText(" B "), withText("C"));
        expect(FIELD).hasTexts("A", "B", "C").containsTexts("C", "A");
    }

    @Test(expectedExceptions = AssertionError.class)
    public void hasTexts_wrongOrder_fails() {
        found(FIELD, withText("A"), withText("B"));
        expect(FIELD).hasTexts("B", "A");
    }

    @Test(expectedExceptions = AssertionError.class)
    public void containsTexts_missing_fails() {
        found(FIELD, withText("A"));
        expect(FIELD).containsTexts("A", "Z");
    }

    // ---------------------------------------------------------------
    // Selection & count
    // ---------------------------------------------------------------

    @Test
    public void isNotChecked_unchecked_passes() {
        found(FIELD, input(""));
        expect(FIELD).isNotChecked();
    }

    @Test(expectedExceptions = AssertionError.class)
    public void isNotChecked_checked_fails() {
        WebElement el = input("");
        when(el.isSelected()).thenReturn(true);
        found(FIELD, el);
        expect(FIELD).isNotChecked();
    }

    @Test(expectedExceptions = AssertionError.class)
    public void isNotChecked_absent_fails() {
        absent(FIELD);
        expect(FIELD).isNotChecked();
    }

    @Test
    public void isSelected_selected_passes() {
        WebElement el = input("");
        when(el.isSelected()).thenReturn(true);
        found(FIELD, el);
        expect(FIELD).isSelected();
    }

    @Test
    public void select_selectedOptionAndOptions_pass() {
        WebElement first = withText("Turkey");
        WebElement second = withText("Germany");
        when(second.isSelected()).thenReturn(true);
        WebElement select = mock(WebElement.class);
        when(select.getTagName()).thenReturn("select");
        when(select.findElements(By.tagName("option"))).thenReturn(Arrays.asList(first, second));
        found(FIELD, select);

        expect(FIELD).hasSelectedOption("Germany").hasOptions("Turkey", "Germany");
    }

    @Test
    public void countAtLeast_atMost_exists_pass() {
        found(FIELD, withText("a"), withText("b"));
        expect(FIELD).countAtLeast(2).countAtMost(2).exists();
    }

    @Test(expectedExceptions = AssertionError.class)
    public void countAtLeast_tooFew_fails() {
        found(FIELD, withText("a"));
        expect(FIELD).countAtLeast(2);
    }

    @Test
    public void doesNotExist_absent_passes() {
        absent(FIELD);
        expect(FIELD).doesNotExist();
    }

    @Test(expectedExceptions = AssertionError.class)
    public void doesNotExist_presentButHidden_fails() {
        WebElement el = input("");
        when(el.isDisplayed()).thenReturn(false);
        found(FIELD, el);
        expect(FIELD).doesNotExist();
    }

    @Test
    public void isInViewport_scriptTrue_passes() {
        found(FIELD, input(""));
        jsReturns(true);
        expect(FIELD).isInViewport();
    }

    // ---------------------------------------------------------------
    // Accessibility & identity
    // ---------------------------------------------------------------

    @Test
    public void role_accessibleName_id_pass() {
        WebElement el = input("");
        when(el.getAriaRole()).thenReturn("textbox");
        when(el.getAccessibleName()).thenReturn(" Username ");
        when(el.getDomAttribute("id")).thenReturn("user");
        found(FIELD, el);
        expect(FIELD).hasRole("textbox").hasAccessibleName("Username").hasId("user");
    }

    @Test(expectedExceptions = AssertionError.class)
    public void hasRole_mismatch_fails() {
        WebElement el = input("");
        when(el.getAriaRole()).thenReturn("button");
        found(FIELD, el);
        expect(FIELD).hasRole("textbox");
    }

    // ---------------------------------------------------------------
    // Negations
    // ---------------------------------------------------------------

    @Test
    public void textNegations_pass() {
        found(FIELD, withText("Welcome"));
        expect(FIELD).doesNotHaveText("Error").doesNotContainText("fail");
    }

    @Test(expectedExceptions = AssertionError.class)
    public void doesNotHaveText_matching_fails() {
        found(FIELD, withText(" Error "));
        expect(FIELD).doesNotHaveText("Error");
    }

    @Test(expectedExceptions = AssertionError.class)
    public void doesNotHaveText_absent_fails() {
        absent(FIELD);
        expect(FIELD).doesNotHaveText("Error");
    }

    @Test
    public void doesNotHaveValue_different_passes() {
        found(FIELD, input("admin"));
        expect(FIELD).doesNotHaveValue("root");
    }

    @Test(expectedExceptions = AssertionError.class)
    public void doesNotHaveValue_same_fails() {
        found(FIELD, input("admin"));
        expect(FIELD).doesNotHaveValue("admin");
    }

    @Test
    public void doesNotHaveClass_otherClasses_passes() {
        WebElement el = input("");
        when(el.getDomAttribute("class")).thenReturn("btn btn-primary");
        found(FIELD, el);
        expect(FIELD).doesNotHaveClass("disabled");
    }

    @Test(expectedExceptions = AssertionError.class)
    public void doesNotHaveClass_present_fails() {
        WebElement el = input("");
        when(el.getDomAttribute("class")).thenReturn("btn disabled");
        found(FIELD, el);
        expect(FIELD).doesNotHaveClass("disabled");
    }

    @Test
    public void doesNotHaveAttribute_pass() {
        WebElement el = input("");
        when(el.getDomAttribute("aria-invalid")).thenReturn("false");
        found(FIELD, el);
        expect(FIELD).doesNotHaveAttribute("disabled").doesNotHaveAttribute("aria-invalid", "true");
    }

    @Test(expectedExceptions = AssertionError.class)
    public void doesNotHaveAttribute_present_fails() {
        WebElement el = input("");
        when(el.getDomAttribute("disabled")).thenReturn("true");
        found(FIELD, el);
        expect(FIELD).doesNotHaveAttribute("disabled");
    }

    @Test
    public void isNotFocused_otherActive_passes() {
        WebElement el = input("");
        found(FIELD, el);
        WebDriver.TargetLocator target = mock(WebDriver.TargetLocator.class);
        when(mockDriver.switchTo()).thenReturn(target);
        when(target.activeElement()).thenReturn(mock(WebElement.class));
        expect(FIELD).isNotFocused();
    }

    @Test
    public void softly_negationFailureRecordedWithoutThrowing() {
        found(FIELD, withText("Error"));
        expect(FIELD).softly().doesNotHaveText("Error");

        assertTrue(SoftAssertions.get().hasFailed());
        assertTrue(SoftAssertions.get().getFailures().get(0).contains("Expected text not to be [Error]"));
    }
}
