package io.testfly.examples.testng;

import io.testfly.examples.pages.SauceDemoAgenticPage;
import io.testfly.test.BaseTest;
import org.openqa.selenium.By;
import org.testng.annotations.Test;

/**
 * Example TestNG test suite demonstrating TestFly Agentic Testing:
 * <ul>
 *   <li><b>Goal-Oriented Autonomous Actions:</b> {@code act("...")} compiles intents to Selenium steps
 *       and freezes them to {@code .testfly/action-cache.json} for instant replay.</li>
 *   <li><b>Semantic Assertions:</b> {@code assertWithAi("...")} and {@code violatesAi("...")} evaluate
 *       complex UI states using zero-shot LLM reasoning without 500ms polling latency.</li>
 *   <li><b>Dynamic Intent Locators:</b> {@code byIntent("...")} locates elements using accessibility
 *       semantics and LLM intent matching.</li>
 *   <li><b>AI-Driven Self-Healing:</b> Selectors automatically heal when IDs or classes change.</li>
 * </ul>
 *
 * <p>Run explicitly with:
 * <pre>
 * export AI_API_KEY="your-api-key"
 * mvn test -Dtest=io.testfly.examples.testng.SauceDemoAgenticTest
 * </pre>
 */
public class SauceDemoAgenticTest extends BaseTest {

    @Test(description = "Demonstrates goal-oriented actions and semantic assertions in an e-commerce flow")
    public void autonomousECommerceFlow() {
        open();

        // 1. Autonomous Login: Compiles into (TYPE user -> TYPE pass -> CLICK login)
        // First run compiles; subsequent runs execute frozen action plan with 0 ms AI latency
        act("Enter username 'standard_user' and password 'secret_sauce', then click the login button");

        // 2. Semantic Page Assertion: Validates overall inventory state
        assertWithAi("The user is logged in and products catalog is displayed with item prices and Add to Cart buttons");
        assertThatPage().violatesAi("Error banner, access denied, or session timeout notice");

        // 3. Goal-Oriented Cart Interaction
        act("Click Add to Cart for Sauce Labs Backpack and navigate to the shopping cart");

        // 4. Semantic Cart Verification
        assertThatPage().satisfiesAi("The shopping cart page lists Sauce Labs Backpack with quantity 1");

        // 5. Dynamic Intent Action
        byIntent("Checkout button").click();

        // 6. Semantic Step Validation
        assertWithAi("The checkout information form asking for First Name, Last Name, and Postal Code is visible");
    }

    @Test(description = "Demonstrates element-level semantic assertions on specific sub-trees")
    public void elementLevelSemanticAssertions() {
        open();

        // Target the login box specifically
        assertThat(find(".login-box")).satisfiesAi("Contains input fields for Username, Password, and a Login submit button");
        assertThat(find(".login-box")).violatesAi("Contains payment information or shopping cart badges");
    }

    @Test(description = "Demonstrates Agentic Page Object integration")
    public void agenticPageObjectIntegration() {
        open();

        SauceDemoAgenticPage page = new SauceDemoAgenticPage(getDriver());
        page.loginWithAgent("standard_user", "secret_sauce")
            .verifyInventoryDisplayed()
            .addItemToCartWithAgent("Sauce Labs Bike Light")
            .openShoppingCart()
            .verifyCartContains("Sauce Labs Bike Light");
    }
}
