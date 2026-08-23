package io.testfly.examples.conditions;

import io.testfly.precondition.BaseConditions;
import io.testfly.precondition.ConditionProvider;
import io.testfly.wait.WaitEngine;
import org.openqa.selenium.By;

/**
 * PreCondition providers for SauceDemo (https://www.saucedemo.com).
 *
 * <p>Registered via SPI: {@code src/test/resources/META-INF/services/io.testfly.precondition.BaseConditions}
 * <p>Usage: {@code @PreCondition("sauce-login")} on any test method.
 */
public class SauceDemoConditions extends BaseConditions {

    private static final By USERNAME = By.id("user-name");
    private static final By PASSWORD = By.id("password");
    private static final By LOGIN_BUTTON = By.id("login-button");

    @ConditionProvider("sauce-login")
    public void sauceLogin() {
        open("/");
        type(USERNAME, "standard_user");
        type(PASSWORD, "secret_sauce");
        click(LOGIN_BUTTON);
        WaitEngine.waitForUrlContains("inventory.html");
    }
}
