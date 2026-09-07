package io.testfly.examples.pages;

import io.testfly.test.BasePage;
import org.openqa.selenium.WebDriver;

/**
 * Page Object for {@code https://the-internet.herokuapp.com/dropdown} demonstrating the
 * {@code NAVIGATE} and {@code SELECT} agent primitives inside the Page Object pattern.
 *
 * <p>Sauce Demo has no {@code <select>} element, so this page doubles as the canonical
 * {@code SELECT} example: {@code #dropdown} is a real select box with Option 1 and Option 2.
 */
public class TheInternetDropdownPage extends BasePage {

    /** Absolute URL, used to show NAVIGATE passing a full URL through unchanged. */
    public static final String URL = "https://the-internet.herokuapp.com/dropdown";

    public TheInternetDropdownPage(WebDriver driver) {
        super(driver);
    }

    /**
     * Navigates through the agent instead of {@code open(URL)}, exercising the NAVIGATE primitive.
     * Both calls freeze into the action cache the same way.
     */
    public TheInternetDropdownPage goToWithAgent() {
        act("Navigate to " + URL);
        return this;
    }

    /**
     * Selects a dropdown option by its visible text through the SELECT primitive.
     */
    public TheInternetDropdownPage selectOptionWithAgent(String visibleOptionText) {
        act("Select '" + visibleOptionText + "' from the dropdown");
        return this;
    }

    /**
     * Semantically verifies which option the dropdown currently shows.
     */
    public TheInternetDropdownPage verifySelectedOption(String expectedOptionText) {
        assertThatPage().satisfiesAi("The dropdown shows '" + expectedOptionText + "' as the selected value");
        return this;
    }
}
