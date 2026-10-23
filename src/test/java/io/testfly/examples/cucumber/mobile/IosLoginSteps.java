package io.testfly.examples.cucumber.mobile;

import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.testfly.examples.mobile.HomePage;
import io.testfly.examples.mobile.LoginPage;
import io.testfly.mobile.MobileSupport;
import org.testng.Assert;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Steps for {@code features-mobile/ios_login.feature}, on the {@link HomePage} and {@link LoginPage} page objects.
 * Credentials come from {@code MOBILE_LOGIN_USER} and {@code MOBILE_LOGIN_PASSWORD}; they are never
 * written in the feature file.
 */
public class IosLoginSteps implements MobileSupport {

    private static final String ENV_FILE = ".vscode/mobile.env";

    @Given("the app is on the home screen")
    public void appIsOnHomeScreen() {
        page(HomePage.class).ready();
    }

    @When("I open the login form")
    public void openLoginForm() {
        page(HomePage.class).loginButton.tap();
        expect(page(LoginPage.class).username).toBeVisible();
    }

    @And("I sign in with the member id and password from the environment")
    public void signIn() {
        page(LoginPage.class).signIn(requiredEnv("MOBILE_LOGIN_USER"), requiredEnv("MOBILE_LOGIN_PASSWORD"));
    }

    @Then("the login form is closed")
    public void loginFormIsClosed() {
        expect(page(LoginPage.class).username).toBeHidden();
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
