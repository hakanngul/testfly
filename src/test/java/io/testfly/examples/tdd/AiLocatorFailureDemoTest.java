package io.testfly.examples.tdd;

import io.testfly.examples.pages.LoginPage;
import io.testfly.locator.Role;
import io.testfly.test.BaseTest;
import org.openqa.selenium.By;
import org.testng.annotations.Test;

/**
 * Intentionally failing tests that trigger AI Failure Analysis on locator-related issues.
 *
 * <p>Each test demonstrates a different category of locator failure so the AI analyser
 * can suggest corrections — wrong element IDs, stale selectors, and missing elements.
 *
 * <p>Run with:
 * <pre>mvn test -Pexamples -Dtest=io.testfly.examples.tdd.AiLocatorFailureDemoTest</pre>
 */
public class AiLocatorFailureDemoTest extends BaseTest {

    /**
     * Wrong element ID — the submit button is {@code login-button}, not {@code submit-btn}.
     * AI should suggest the correct ID based on the page source.
     */
    @Test
    public void loginButtonShouldBeClickable() {
        open();
        assertThat(By.id("submit-btn"))
                .as("Login button should be visible on the page")
                .isVisible();
    }

    /**
     * Stale CSS selector — the error container uses {@code [data-test='error']},
     * not {@code .error-message}. AI should detect the mismatch.
     */
    @Test
    public void lockedOutUserShouldShowErrorBanner() {
        open();
        new LoginPage(getDriver()).login("locked_out_user", "secret_sauce");

        assertThat(By.cssSelector(".error-message"))
                .as("Error should mention the locked-out user")
                .containsText("locked out");
    }

    /**
     * Non-existent element — there is no "Remember me" checkbox on this page.
     * AI should report that the element does not exist and suggest a locator audit.
     */
    @Test
    public void rememberMeCheckboxShouldBePresent() {
        open();
        assertThat(By.id("remember-me")).isVisible();
    }

    /**
     * Wrong accessibility role — the login button is a {@code <input type="submit">},
     * not a {@code <button>}. AI should suggest inspecting the ARIA tree.
     */
    @Test
    public void loginButtonShouldBeFoundByRole() {
        open();
        assertThat(getByRole(Role.LINK, "Login")).isVisible();
    }
}
