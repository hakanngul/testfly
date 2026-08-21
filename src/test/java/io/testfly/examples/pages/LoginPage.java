package io.testfly.examples.pages;

import io.testfly.test.BasePage;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

/**
 * Page Object for the Sauce Demo login screen.
 */
public class LoginPage extends BasePage {

    private static final By USERNAME = By.id("user-name");
    private static final By PASSWORD = By.id("password");
    private static final By LOGIN_BUTTON = By.id("login-button");
    private static final By ERROR_MESSAGE = By.cssSelector("[data-test='error']");

    public LoginPage(WebDriver driver) {
        super(driver);
    }

    public void login(String username, String password) {
        find(USERNAME).type(username);
        find(PASSWORD).type(password);
        find(LOGIN_BUTTON).click();
    }

    public boolean isErrorDisplayed() {
        return find(ERROR_MESSAGE).isVisible();
    }

    public String getErrorText() {
        return find(ERROR_MESSAGE).getText();
    }
}
