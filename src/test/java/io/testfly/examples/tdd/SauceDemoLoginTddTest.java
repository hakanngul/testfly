package io.testfly.examples.tdd;

import io.testfly.examples.pages.LoginPage;
import io.testfly.examples.pages.ProductsPage;
import io.testfly.locator.Role;
import io.testfly.test.BaseTest;
import org.openqa.selenium.By;
import org.testng.annotations.Test;

/**
 * TDD-style UI example for https://www.saucedemo.com.
 *
 * <p>Each test verifies exactly one behaviour with a clear arrange-act-assert structure.
 * Run explicitly with:
 * <pre>mvn test -Dtest=io.testfly.examples.tdd.SauceDemoLoginTddTest</pre>
 */
public class SauceDemoLoginTddTest extends BaseTest {

    @Test
    public void loginPageShouldLoad() {
        // Act
        open();

        // Assert — semantic locator
        assertThat(getByRole(Role.BUTTON, "Login")).isVisible();
    }

    @Test
    public void validUserShouldLandOnProductsPage() {
        // Arrange
        open();
        LoginPage loginPage = new LoginPage(getDriver());

        // Act
        loginPage.login("standard_user", "secret_sauce");

        // Assert
        assertThat(By.className("title")).hasText("Products");
    }

    @Test
    public void lockedOutUserShouldSeeLockedOutError() {
        // Arrange
        open();
        LoginPage loginPage = new LoginPage(getDriver());

        // Act
        loginPage.login("locked_out_user", "secret_sauce");

        // Assert
        assertThat(By.cssSelector("[data-test='error']"))
                .isVisible()
                .containsText("locked out");
    }

    @Test
    public void addingProductShouldUpdateCartBadge() {
        // Arrange
        open();
        new LoginPage(getDriver()).login("standard_user", "secret_sauce");
        ProductsPage productsPage = new ProductsPage(getDriver());

        // Act
        productsPage.addFirstProductToCart();

        // Assert
        assertThat(By.className("shopping_cart_badge")).hasText("1");
    }
}
