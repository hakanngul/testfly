package io.testfly.examples.junit5;

import io.testfly.examples.pages.TheInternetDropdownPage;
import io.testfly.junit5.BaseJUnit5Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Example JUnit 5 suite showing that the SELECT and NAVIGATE agent primitives behave identically
 * to their TestNG counterparts.
 *
 * <p>Run explicitly with:
 * <pre>
 * export AI_API_KEY="your-api-key"
 * mvn test -Pexamples -Dtest=io.testfly.examples.junit5.SelectAndNavigateAgenticJUnit5Test
 * </pre>
 */
@DisplayName("SELECT and NAVIGATE Agentic Primitives (JUnit 5)")
class SelectAndNavigateAgenticJUnit5Test extends BaseJUnit5Test {

    @Test
    @DisplayName("NAVIGATE to an absolute URL and SELECT a dropdown option by visible text")
    void absoluteNavigationAndDropdownSelection() {
        open();

        act("Navigate to https://the-internet.herokuapp.com/dropdown");
        assertWithAi("A page headed 'Dropdown List' with a select box is displayed");

        act("Select 'Option 2' from the dropdown");
        assertThatPage().satisfiesAi("The dropdown shows Option 2 as its selected value");
    }

    @Test
    @DisplayName("Page Object driving NAVIGATE and SELECT through the agent")
    void dropdownPageObjectFlow() {
        open();

        new TheInternetDropdownPage(getDriver())
                .goToWithAgent()
                .selectOptionWithAgent("Option 1")
                .verifySelectedOption("Option 1");
    }

    @Test
    @DisplayName("NAVIGATE resolves a relative path against execution.baseUrl")
    void relativePathNavigation() {
        open();

        act("Log in with username 'standard_user' and password 'secret_sauce'");
        act("Navigate to /cart.html");
        assertThatPage().satisfiesAi("The Your Cart page is displayed with a Checkout button");
    }
}
