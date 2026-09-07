package io.testfly.examples.pages;

import io.testfly.test.BasePage;
import org.openqa.selenium.WebDriver;

/**
 * Example Page Object demonstrating how to leverage TestFly Agentic Testing capabilities
 * alongside standard Page Object patterns.
 *
 * <p>Demonstrates:
 * <ul>
 *   <li>Goal-oriented actions: {@code act("...")} with automatic "Compile &amp; Freeze" caching</li>
 *   <li>Semantic element resolution: {@code byIntent("...")}</li>
 *   <li>Semantic assertions: {@code satisfiesAi("...")} and {@code violatesAi("...")}</li>
 * </ul>
 */
public class SauceDemoAgenticPage extends BasePage {

    public SauceDemoAgenticPage(WebDriver driver) {
        super(driver);
    }

    /**
     * Executes login autonomously by natural language goal.
     */
    public SauceDemoAgenticPage loginWithAgent(String username, String password) {
        act("Enter username '" + username + "' and password '" + password + "', then click Login");
        return this;
    }

    /**
     * Adds an item to the shopping cart autonomously.
     */
    public SauceDemoAgenticPage addItemToCartWithAgent(String itemName) {
        act("Click the Add to Cart button for item '" + itemName + "'");
        return this;
    }

    /**
     * Navigates to the shopping cart using semantic intent.
     */
    public SauceDemoAgenticPage openShoppingCart() {
        byIntent("shopping cart link or button").click();
        return this;
    }

    /**
     * Semantically verifies that the catalog displays expected inventory state.
     */
    public SauceDemoAgenticPage verifyInventoryDisplayed() {
        assertWithAi("The page displays an inventory grid with items, descriptions, prices, and Add to cart buttons");
        assertThatPage().violatesAi("Shows error message or authentication failure banner");
        return this;
    }

    /**
     * Semantically verifies that the cart page contains the expected item.
     */
    public SauceDemoAgenticPage verifyCartContains(String itemName) {
        assertThatPage().satisfiesAi("The cart contains item: " + itemName);
        return this;
    }
}
