package io.testfly.examples.testng;

import io.testfly.examples.pages.TheInternetDropdownPage;
import io.testfly.test.BaseTest;
import org.testng.annotations.Test;

/**
 * Example TestNG suite for the {@code SELECT} and {@code NAVIGATE} agent action primitives.
 *
 * <p>{@code NAVIGATE} accepts either an absolute URL, which {@code ActionExecutor.resolveTargetUrl}
 * passes straight through, or a path that it resolves against {@code execution.baseUrl}.
 * {@code SELECT} picks a {@code <select>} option by its visible text.
 *
 * <p>Run explicitly with:
 * <pre>
 * export AI_API_KEY="your-api-key"
 * mvn test -Pexamples -Dtest=io.testfly.examples.testng.SelectAndNavigateAgenticTest
 * </pre>
 */
public class SelectAndNavigateAgenticTest extends BaseTest {

    @Test(description = "NAVIGATE to an absolute URL, then SELECT a dropdown option by visible text")
    public void absoluteNavigationAndDropdownSelection() {
        open();

        // NAVIGATE with an absolute URL. resolveTargetUrl passes it through untouched even though
        // execution.baseUrl points at Sauce Demo, so one plan can cross domains.
        act("Navigate to https://the-internet.herokuapp.com/dropdown");
        assertWithAi("A page headed 'Dropdown List' with a select box is displayed");

        // SELECT. The agent compiles this into
        // {"action":"SELECT","locator":"#dropdown","value":"Option 2"} and ActionExecutor runs
        // new Select(el).selectByVisibleText("Option 2").
        act("Select 'Option 2' from the dropdown");
        assertThatPage().satisfiesAi("The dropdown currently shows Option 2 as its selected value");
        assertThatPage().violatesAi("The dropdown still shows the 'Please select an option' placeholder");
    }

    @Test(description = "NAVIGATE with a relative path resolved against execution.baseUrl")
    public void relativePathNavigationUsesBaseUrl() {
        open();

        act("Enter username 'standard_user' and password 'secret_sauce', then click the login button");
        assertWithAi("The products catalog is displayed after a successful login");

        // NAVIGATE with a path. '/cart.html' becomes https://www.saucedemo.com/cart.html because
        // execution.baseUrl is https://www.saucedemo.com/. Without a configured baseUrl a relative
        // path fails loudly instead of navigating somewhere unintended.
        act("Navigate to /cart.html");
        assertThatPage().satisfiesAi("The Your Cart page is displayed with a Checkout button");
    }

    @Test(description = "Page Object that drives NAVIGATE and SELECT through the agent")
    public void dropdownPageObjectIntegration() {
        open();

        new TheInternetDropdownPage(getDriver())
                .goToWithAgent()
                .selectOptionWithAgent("Option 1")
                .verifySelectedOption("Option 1");
    }
}
