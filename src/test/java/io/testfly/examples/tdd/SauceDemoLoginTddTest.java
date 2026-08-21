package io.testfly.examples.tdd;

import io.testfly.examples.pages.LoginPage;
import io.testfly.examples.pages.ProductsPage;
import io.testfly.locator.Role;
import io.testfly.test.BaseTest;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

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
        assertEquals(new ProductsPage(getDriver()).getTitle(), "Products");
    }

    @Test
    public void lockedOutUserShouldSeeLockedOutError() {
        // Arrange
        open();
        LoginPage loginPage = new LoginPage(getDriver());

        // Act
        loginPage.login("locked_out_user", "secret_sauce");

        // Assert
        assertTrue(loginPage.isErrorDisplayed(), "Error message should be displayed");
        assertTrue(loginPage.getErrorText().contains("locked out"),
                "Error text should explain that the user is locked out");
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
        assertEquals(productsPage.getCartCount(), "1");
    }
}
