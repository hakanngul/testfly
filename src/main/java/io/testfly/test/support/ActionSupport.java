package io.testfly.test.support;

import io.testfly.agent.ActionCompiler;
import io.testfly.api.TestFlyApi;
import io.testfly.driver.DriverManager;
import org.openqa.selenium.WebDriver;

/**
 * Goal-oriented agentic testing actions.
 *
 * <p>Enables tests and page objects to execute natural language goals
 * via the {@link #act(String)} method. Goals are compiled to deterministic
 * Selenium actions and cached to {@code .testfly/action-cache.json} for instant replay.
 */
@TestFlyApi(since = "1.9.0")
public interface ActionSupport {

    /**
     * Executes a goal-oriented action sequence on the active thread's WebDriver.
     *
     * <pre>
     * act("Delete the first item in the shopping cart");
     * act("Type 'laptop' into the search input and click Search");
     * </pre>
     *
     * @param goal natural language goal
     */
    default void act(String goal) {
        ActionCompiler.execute(DriverManager.getDriver(), goal);
    }

    /**
     * Executes a goal-oriented action sequence on the provided WebDriver instance.
     *
     * @param driver custom WebDriver instance
     * @param goal   natural language goal
     */
    default void act(WebDriver driver, String goal) {
        ActionCompiler.execute(driver, goal);
    }
}
