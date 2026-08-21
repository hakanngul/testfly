package io.testfly.examples.testng;

import io.testfly.examples.pages.LoginPage;
import io.testfly.examples.pages.ProductsPage;
import io.testfly.test.BaseTest;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * TestNG UI example for https://www.saucedemo.com.
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

        ProductsPage productsPage = new ProductsPage(getDriver());
        assertEquals(productsPage.getTitle(), "Products");
    }

    @Test
    public void lockedOutUserSeesError() {
        open();

        LoginPage loginPage = new LoginPage(getDriver());
        loginPage.login("locked_out_user", "secret_sauce");

        assertTrue(loginPage.isErrorDisplayed(), "Error message should be shown");
        assertTrue(loginPage.getErrorText().contains("locked out"),
                "Error text should mention locked out");
    }

    @Test
    public void userCanAddProductToCart() {
        open();

        new LoginPage(getDriver()).login("standard_user", "secret_sauce");

        ProductsPage productsPage = new ProductsPage(getDriver());
        productsPage.addFirstProductToCart();

        assertEquals(productsPage.getCartCount(), "1");
    }
}
