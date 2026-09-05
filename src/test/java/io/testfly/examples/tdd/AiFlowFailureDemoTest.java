package io.testfly.examples.tdd;

import io.testfly.examples.pages.LoginPage;
import io.testfly.test.BaseTest;
import org.openqa.selenium.By;
import org.testng.annotations.Test;

/**
 * Intentionally failing tests that trigger AI Failure Analysis on flow and state issues.
 *
 * <p>Each test demonstrates a different category of flow/state failure — wrong page state,
 * incorrect navigation assumptions, and interaction-order bugs.
 *
 * <p>Run with:
 * <pre>mvn test -Pexamples -Dtest=io.testfly.examples.tdd.AiFlowFailureDemoTest</pre>
 */
public class AiFlowFailureDemoTest extends BaseTest {

    /**
     * Wrong page assumption — tries to read the products title without logging in first.
     * AI should detect that the test skipped the authentication step.
     */
    @Test
    public void productsPageTitleShouldBeVisible() {
        open();
        // Bug: forgot to login — still on the login page
        assertThat(By.className("title"))
                .as("Should be on the products page")
                .hasText("Products");
    }

    /**
     * Cart should be empty before login — but the test asserts it already has items.
     * AI should suggest that the cart state depends on a prior add-to-cart action.
     */
    @Test
    public void cartShouldHaveItemsBeforeAddingAnything() {
        open();
        new LoginPage(getDriver()).login("standard_user", "secret_sauce");

        // Bug: no product was added, cart badge doesn't even exist yet
        assertThat(By.className("shopping_cart_badge"))
                .as("Cart should have 3 items")
                .hasText("3");
    }

    /**
     * Wrong credentials should still navigate — test assumes login always succeeds.
     * AI should detect that the wrong password keeps the user on the login page.
     */
    @Test
    public void wrongPasswordShouldNavigateToProducts() {
        open();
        new LoginPage(getDriver()).login("standard_user", "wrong_password");

        // Bug: login failed — the URL is still /index.html (login page)
        assertThat(getDriver())
                .as("Should be on the inventory page after login")
                .urlContains("inventory");
    }

    /**
     * Logout flow — test tries to click a non-existent "Logout" button directly.
     * AI should suggest opening the hamburger menu first.
     */
    @Test
    public void logoutShouldReturnToLoginPage() {
        open();
        new LoginPage(getDriver()).login("standard_user", "secret_sauce");

        // Bug: logout is inside the sidebar menu, not a direct button
        find(By.id("react-burger-menu-btn")).click();
        find(By.id("logout_sidebar_link")).click();

        assertThat(getDriver())
                .as("Should be back on the login page after logout")
                .urlContains("index");
    }
}
