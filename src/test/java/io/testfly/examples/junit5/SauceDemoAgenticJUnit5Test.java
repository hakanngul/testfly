package io.testfly.examples.junit5;

import io.testfly.junit5.BaseJUnit5Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Example JUnit 5 test suite demonstrating TestFly Agentic Testing parity in JUnit 5.
 *
 * <p>Run explicitly with:
 * <pre>
 * export AI_API_KEY="your-api-key"
 * mvn test -Dtest=io.testfly.examples.junit5.SauceDemoAgenticJUnit5Test
 * </pre>
 */
@DisplayName("SauceDemo Agentic Testing (JUnit 5)")
class SauceDemoAgenticJUnit5Test extends BaseJUnit5Test {

    @Test
    @DisplayName("Autonomous login and checkout flow with Compile & Freeze Action Caching")
    void autonomousLoginAndCartFlow() {
        open();

        // 1. Goal-oriented action
        act("Log in with username 'standard_user' and password 'secret_sauce'");

        // 2. Semantic assertions
        assertWithAi("The products catalog is visible with inventory items");
        assertThatPage().violatesAi("Error banner or invalid credentials message");

        // 3. Goal action
        act("Add the first product to cart and open the shopping cart");

        // 4. Verification
        assertThatPage().satisfiesAi("Shopping cart contains one item");
    }
}
