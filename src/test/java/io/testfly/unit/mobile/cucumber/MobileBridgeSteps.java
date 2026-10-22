package io.testfly.unit.mobile.cucumber;

import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.testfly.driver.DriverManager;
import io.testfly.mobile.MobileRole;
import io.testfly.mobile.MobileSupport;

import java.time.Duration;

import static org.testng.Assert.assertFalse;

/** Steps for {@code mobile/features/mobile_login.feature}; the device comes from {@code @mobile}. */
public class MobileBridgeSteps implements MobileSupport {

    @When("I sign in on the device as {string}")
    public void signIn(String user) {
        assertFalse(DriverManager.hasDriver(), "@mobile scenarios start no browser");
        screen().getByTestId("username").fill(user);
        screen().getByRole(MobileRole.BUTTON, "Sign in").tap();
    }

    @Then("the device shows {string}")
    public void shows(String text) {
        expect(screen().getByText(text, false).withTimeout(Duration.ofMillis(800))).toBeVisible();
    }
}
