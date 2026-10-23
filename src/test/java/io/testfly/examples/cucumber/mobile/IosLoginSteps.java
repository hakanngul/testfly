package io.testfly.examples.cucumber.mobile;

import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.testfly.mobile.MobileSupport;
import org.testng.Assert;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Steps for {@code features-mobile/ios_login.feature}. Locators are iOS accessibility identifiers.
 * Credentials come from {@code MOBILE_LOGIN_USER} and {@code MOBILE_LOGIN_PASSWORD}; they are never
 * written in the feature file.
 */
public class IosLoginSteps implements MobileSupport {

    private static final String ENV_FILE = ".vscode/mobile.env";
    private static final String HOME_LOGIN_BUTTON = "HomeScreen.loginButton";
    private static final String COOKIE_ACCEPT_BUTTON = "cookieBannerAcceptAllButton";
    private static final String USERNAME_FIELD = "LoginScreen.nameTextField";
    private static final String PASSWORD_FIELD = "LoginScreen.passwordTextField";
    private static final String LOGIN_BUTTON = "LoginScreen.loginButton";

    @Given("the app is on the home screen")
    public void appIsOnHomeScreen() {
        // The app opens on a splash screen, then the home screen; a cookie banner may cover it.
        screen().getByTestId(HOME_LOGIN_BUTTON).waitForVisible();
        if (screen().getByTestId(COOKIE_ACCEPT_BUTTON).count() > 0) {
            screen().getByTestId(COOKIE_ACCEPT_BUTTON).tap();
            screen().getByTestId(COOKIE_ACCEPT_BUTTON).waitForHidden();
        }
    }

    @When("I open the login form")
    public void openLoginForm() {
        screen().getByTestId(HOME_LOGIN_BUTTON).tap();
        expect(screen().getByTestId(USERNAME_FIELD)).toBeVisible();
    }

    @And("I sign in with the member id and password from the environment")
    public void signIn() {
        screen().getByTestId(USERNAME_FIELD).fill(requiredEnv("MOBILE_LOGIN_USER"));
        screen().getByTestId(PASSWORD_FIELD).fill(requiredEnv("MOBILE_LOGIN_PASSWORD"));
        screen().getByTestId(LOGIN_BUTTON).tap();
    }

    @Then("the login form is closed")
    public void loginFormIsClosed() {
        expect(screen().getByTestId(USERNAME_FIELD)).toBeHidden();
    }

    /**
     * The environment variable, else the same key from the git-ignored {@code .vscode/mobile.env}
     * (tools like the Cucumber Java Runner extension do not load that file themselves).
     */
    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            value = fromEnvFile(name);
        }
        Assert.assertTrue(value != null && !value.isBlank(),
                "Environment variable " + name + " (or " + ENV_FILE + ") is required");
        return value;
    }

    private static String fromEnvFile(String name) {
        Path file = Path.of(ENV_FILE);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            for (String line : Files.readAllLines(file)) {
                String trimmed = line.trim();
                if (trimmed.startsWith(name + "=")) {
                    return trimmed.substring(name.length() + 1);
                }
            }
        } catch (IOException e) {
            return null;
        }
        return null;
    }
}
