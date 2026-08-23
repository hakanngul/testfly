package io.testfly.precondition;

import io.testfly.api.TestFlyApi;
import io.testfly.test.support.NavigationSupport;
import io.testfly.wait.WaitEngine;
import org.openqa.selenium.By;

/**
 * Base class for condition provider classes.
 *
 * <p>Extend this class and annotate methods with {@link ConditionProvider} to define
 * named pre-conditions. Register the class via Java SPI or programmatically:
 *
 * <pre>
 * // SPI — create META-INF/services/io.testfly.precondition.BaseConditions
 * //       containing the fully-qualified class name of your subclass
 *
 * // Programmatic
 * PreConditionRegistry.register(new AppConditions());
 * </pre>
 *
 * <p>Example:
 * <pre>
 * public class AppConditions extends BaseConditions {
 *
 *     {@literal @}ConditionProvider("login")
 *     public void login() {
 *         open("/login");
 *         type(By.id("username"), "admin");
 *         type(By.id("password"), "secret");
 *         click(By.id("submit"));
 *         WaitEngine.waitForUrlContains("/dashboard");
 *     }
 * }
 * </pre>
 *
 * @see ConditionProvider
 * @see PreCondition
 * @since 0.8.0
 */
@TestFlyApi(since = "0.8.0")
public abstract class BaseConditions implements NavigationSupport {

    // open(), open(String), getDriver(), getWait() — via NavigationSupport
    // (single source for baseUrl resolution, StepLogger, ConsoleErrorCollector shim)

    /** Waits for the element to be clickable and clicks it. */
    protected void click(By locator) {
        WaitEngine.waitForClickable(locator).click();
    }

    /** Waits for the element to be visible, clears it, then types text. */
    protected void type(By locator, String text) {
        var el = WaitEngine.waitForVisible(locator);
        el.clear();
        el.sendKeys(text);
    }
}
