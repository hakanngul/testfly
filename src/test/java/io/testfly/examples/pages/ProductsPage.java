package io.testfly.examples.pages;

import io.testfly.test.BasePage;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

/**
 * Page Object for the Sauce Demo products/inventory page.
 */
public class ProductsPage extends BasePage {

    private static final By TITLE = By.className("title");
    private static final By FIRST_ADD_TO_CART_BUTTON = By.cssSelector(".inventory_item:first-child .btn_inventory");
    private static final By CART_BADGE = By.className("shopping_cart_badge");

    public ProductsPage(WebDriver driver) {
        super(driver);
    }

    public String getTitle() {
        return find(TITLE).getText();
    }

    public void addFirstProductToCart() {
        find(FIRST_ADD_TO_CART_BUTTON).click();
    }

    public String getCartCount() {
        return find(CART_BADGE).getText();
    }
}
