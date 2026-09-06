package io.testfly.agent;

import io.testfly.api.TestFlyApi;
import io.testfly.driver.DriverManager;
import io.testfly.steps.StepLogger;
import io.testfly.wait.WaitEngine;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;

import java.time.Duration;

/**
 * Executes compiled action steps using safe WebDriver waits and StepLogger integration.
 */
@TestFlyApi(since = "1.9.0")
public final class ActionExecutor {

    private ActionExecutor() {}

    /**
     * Executes all steps of an ActionPlan sequentially with explicit timeouts.
     *
     * @param driver  active WebDriver session
     * @param plan    ActionPlan to execute
     * @param timeout wait timeout for each step
     */
    public static void execute(WebDriver driver, ActionPlan plan, Duration timeout) {
        if (driver == null) {
            throw new IllegalArgumentException("WebDriver cannot be null");
        }
        if (plan == null || plan.steps() == null || plan.steps().isEmpty()) {
            throw new IllegalArgumentException("ActionPlan has no steps to execute");
        }

        StepLogger.step("Executing agent goal: \"" + plan.goal() + "\" (" + plan.steps().size() + " step(s))");

        for (int i = 0; i < plan.steps().size(); i++) {
            ActionStep step = plan.steps().get(i);
            executeStep(driver, step, i + 1, plan.steps().size(), timeout);
        }
    }

    /**
     * Executes a single action step.
     */
    public static void executeStep(WebDriver driver, ActionStep step, int index, int total, Duration timeout) {
        String desc = (step.description() != null && !step.description().isBlank())
                ? step.description()
                : step.action() + " on " + step.locator();

        StepLogger.step("AI Step " + index + "/" + total + " [" + step.action() + "]: " + desc);

        By by = parseLocator(step.locator());

        switch (step.action()) {
            case CLICK -> {
                WebElement el = waitClickable(driver, by, timeout);
                el.click();
            }
            case TYPE -> {
                WebElement el = waitVisible(driver, by, timeout);
                el.clear();
                if (step.value() != null) {
                    el.sendKeys(step.value());
                }
            }
            case CLEAR -> {
                WebElement el = waitVisible(driver, by, timeout);
                el.clear();
            }
            case HOVER -> {
                WebElement el = waitVisible(driver, by, timeout);
                new Actions(driver).moveToElement(el).perform();
            }
            case WAIT_VISIBLE -> {
                waitVisible(driver, by, timeout);
            }
            case PRESS_ENTER -> {
                WebElement el = waitVisible(driver, by, timeout);
                el.sendKeys(Keys.ENTER);
            }
        }
    }

    private static WebElement waitClickable(WebDriver driver, By by, Duration timeout) {
        if (driver == null || driver == DriverManager.getDriver()) {
            return WaitEngine.waitForClickable(by);
        }
        return new org.openqa.selenium.support.ui.WebDriverWait(driver, timeout)
                .until(org.openqa.selenium.support.ui.ExpectedConditions.elementToBeClickable(by));
    }

    private static WebElement waitVisible(WebDriver driver, By by, Duration timeout) {
        if (driver == null || driver == DriverManager.getDriver()) {
            return WaitEngine.waitForVisible(by);
        }
        return new org.openqa.selenium.support.ui.WebDriverWait(driver, timeout)
                .until(org.openqa.selenium.support.ui.ExpectedConditions.visibilityOfElementLocated(by));
    }

    /**
     * Parses a locator string into an appropriate Selenium {@link By} (CSS, XPath, ID, or Name).
     * Handles common prefixes and formatting emitted by LLMs (e.g. {@code css=}, {@code xpath=}, {@code id=}, quotes/backticks).
     */
    public static By parseLocator(String locatorStr) {
        if (locatorStr == null || locatorStr.isBlank()) {
            throw new IllegalArgumentException("Step locator string cannot be empty");
        }
        String trimmed = locatorStr.trim();

        // Strip surrounding quotes or backticks if present
        if ((trimmed.startsWith("`") && trimmed.endsWith("`") && trimmed.length() >= 2)
                || (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2)
                || (trimmed.startsWith("'") && trimmed.endsWith("'") && trimmed.length() >= 2)) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }

        String lower = trimmed.toLowerCase();
        if (lower.startsWith("css=") || lower.startsWith("css:")) {
            String css = trimmed.substring(4).trim();
            return By.cssSelector(stripQuotes(css));
        }
        if (lower.startsWith("xpath=") || lower.startsWith("xpath:")) {
            String xpath = trimmed.substring(6).trim();
            return By.xpath(stripQuotes(xpath));
        }
        if (lower.startsWith("id=") || lower.startsWith("id:")) {
            String id = trimmed.substring(3).trim();
            return By.id(stripQuotes(id));
        }
        if (lower.startsWith("name=") || lower.startsWith("name:")) {
            String name = trimmed.substring(5).trim();
            return By.name(stripQuotes(name));
        }

        if (trimmed.startsWith("//") || trimmed.startsWith("(") || trimmed.startsWith("./")) {
            return By.xpath(trimmed);
        }
        return By.cssSelector(trimmed);
    }

    private static String stripQuotes(String s) {
        if (s == null) return "";
        String trimmed = s.trim();
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2)
                || (trimmed.startsWith("'") && trimmed.endsWith("'") && trimmed.length() >= 2)) {
            return trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }
}
