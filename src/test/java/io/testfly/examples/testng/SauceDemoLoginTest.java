package io.testfly.examples.testng;

import io.testfly.examples.pages.LoginPage;
import io.testfly.examples.pages.ProductsPage;
import io.testfly.precondition.PreCondition;
import io.testfly.test.BaseTest;
import org.openqa.selenium.By;
import org.testng.annotations.Test;

/**
 * TestNG UI example for https://www.saucedemo.com.
 *
 * <p>Demonstrates {@link PreCondition} session caching via
 * {@code io.testfly.examples.conditions.SauceDemoConditions}:
 * the first test with {@code @PreCondition("sauce-login")} runs the provider
 * and caches cookies+localStorage; subsequent tests restore the session
 * without re-running login.
 *
 * <p>Run explicitly with:
 * <pre>mvn test -Dtest=io.testfly.examples.testng.SauceDemoLoginTest</pre>
 */
public class SauceDemoLoginTest extends BaseTest {

    @Test
    public void standardUserCanLogin() {
        open();

        LoginPage loginPage = new LoginPage(getDriver());
        loginPage.login("standard_user", "secret_sauce");

        assertThat(By.className("title")).hasText("Products");
    }

    @Test
    public void lockedOutUserSeesError() {
        open();

        LoginPage loginPage = new LoginPage(getDriver());
        loginPage.login("locked_out_user", "secret_sauce");

        assertThat(By.cssSelector("[data-test='error']"))
                .isVisible()
                .containsText("locked out");
    }

    @PreCondition("sauce-login")
    @Test
    public void userCanAddProductToCart() {
        // No login needed — PreConditionRunner restores cached session
        // (first run executes SauceDemoConditions#sauceLogin and caches it)
        ProductsPage productsPage = new ProductsPage(getDriver());
        productsPage.addFirstProductToCart();

        assertThat(By.className("shopping_cart_badge")).hasText("1");
    }

    @PreCondition("sauce-login")
    @Test
    public void userCanSeeProductsAfterCachedLogin() {
        // Second consumer of the same condition — instant restore, no login replay
        assertThat(By.className("title")).hasText("Products");
    }
}
