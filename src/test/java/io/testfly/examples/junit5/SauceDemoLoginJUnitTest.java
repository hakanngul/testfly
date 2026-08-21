package io.testfly.examples.junit5;

import io.testfly.examples.pages.LoginPage;
import io.testfly.examples.pages.ProductsPage;
import io.testfly.junit5.BaseJUnit5Test;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 UI example for https://www.saucedemo.com.
 *
 * <p>Run explicitly with:
 * <pre>mvn test -Dtest=io.testfly.examples.junit5.SauceDemoLoginJUnitTest</pre>
 */
public class SauceDemoLoginJUnitTest extends BaseJUnit5Test {

    @Test
    void standardUserCanLogin() {
        open();

        new LoginPage(getDriver()).login("standard_user", "secret_sauce");

        ProductsPage productsPage = new ProductsPage(getDriver());
        assertEquals("Products", productsPage.getTitle());
    }

    @Test
    void lockedOutUserSeesError() {
        open();

        LoginPage loginPage = new LoginPage(getDriver());
        loginPage.login("locked_out_user", "secret_sauce");

        assertTrue(loginPage.isErrorDisplayed(), "Error message should be shown");
        assertTrue(loginPage.getErrorText().contains("locked out"),
                "Error text should mention locked out");
    }

    @Test
    void userCanAddProductToCart() {
        open();

        new LoginPage(getDriver()).login("standard_user", "secret_sauce");

        ProductsPage productsPage = new ProductsPage(getDriver());
        productsPage.addFirstProductToCart();

        assertEquals("1", productsPage.getCartCount());
    }
}
