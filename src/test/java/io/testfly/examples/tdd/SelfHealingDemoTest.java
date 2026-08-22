package io.testfly.examples.tdd;

import io.testfly.examples.pages.LoginPage;
import io.testfly.examples.pages.ProductsPage;
import io.testfly.healing.HealLog;
import io.testfly.test.BaseTest;
import org.openqa.selenium.By;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * Self-healing locator demo — tests that would normally fail due to broken selectors
 * but pass because the framework finds alternative strategies automatically.
 *
 * <p>Each test intentionally uses a broken CSS selector. The self-healing engine
 * extracts usable fragments (id, class) from the broken selector and retries.
 *
 * <p>Requires {@code locators.selfHealing: true} in testfly.yml.
 *
 * <p>After the suite, check {@code target/healed-locators.json} for a full report
 * of which locators were healed and which strategy succeeded.
 *
 * <p>Run with:
 * <pre>mvn test -Pexamples -Dtest=io.testfly.examples.tdd.SelfHealingDemoTest</pre>
 */
public class SelfHealingDemoTest extends BaseTest {

    /**
     * Wrong tag name — {@code span#user-name} doesn't match anything because
     * the username field is an {@code <input>}, not a {@code <span>}.
     *
     * <p>Self-healing extracts {@code #user-name} → retries {@code By.id("user-name")} → success.
     * Strategy: {@code id-from-css}
     */
    @Test
    public void typeUsernameWithBrokenTagSelector() {
        open();

        // Broken: span#user-name (element is actually <input id="user-name">)
        // Self-healing extracts #user-name → By.id("user-name")
        find(By.cssSelector("span#user-name")).type("standard_user");
        find(By.cssSelector("div#password")).type("secret_sauce");

        // Also broken: button#login-button (element is <input type="submit" id="login-button">)
        find(By.cssSelector("button#login-button")).click();

        assertEquals(new ProductsPage(getDriver()).getTitle(), "Products",
                "Should land on products page after login");
    }

    /**
     * Non-existent compound selector — {@code div.form-group .btn_primary} doesn't match
     * because there's no wrapping {@code .form-group} element on saucedemo.
     *
     * <p>Self-healing extracts the last class segment {@code .btn_primary}
     * → retries {@code By.className("btn_primary")} → success.
     * Strategy: {@code class-from-css}
     */
    @Test
    public void clickLoginButtonWithBrokenCompoundSelector() {
        open();
        new LoginPage(getDriver()).login("standard_user", "secret_sauce");

        // On the products page, try a broken selector for the cart icon
        // Broken: nav.topbar > div.header-container .shopping_cart_link
        // Self-healing extracts .shopping_cart_link → By.className("shopping_cart_link")
        find(By.cssSelector("nav.topbar > div.header-container .shopping_cart_link")).click();

        assertTrue(getDriver().getCurrentUrl().contains("cart"),
                "Should navigate to the cart page");
    }

    /**
     * Verifies that the heal events were actually recorded during this test suite.
     * This is not a healing test itself — it's an assertion that healing happened above.
     */
    @Test(dependsOnMethods = {"typeUsernameWithBrokenTagSelector", "clickLoginButtonWithBrokenCompoundSelector"})
    public void verifyHealEventsWereRecorded() {
        assertFalse(HealLog.getAll().isEmpty(),
                "At least one locator should have been healed during this suite. "
                        + "Make sure locators.selfHealing: true is set in testfly.yml");

        System.out.println("[Demo] Total healed locators: " + HealLog.getAll().size());
        HealLog.getAll().forEach(e ->
                System.out.println("[Demo]   " + e.getOriginalLocator()
                        + " → " + e.getHealedLocator()
                        + " (" + e.getStrategy() + ")"));
    }
}
