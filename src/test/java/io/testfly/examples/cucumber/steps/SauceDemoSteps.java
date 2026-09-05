package io.testfly.examples.cucumber.steps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.testfly.cucumber.BaseCucumberSteps;
import io.testfly.examples.pages.LoginPage;
import io.testfly.examples.pages.ProductsPage;
import org.openqa.selenium.By;

/**
 * Step definitions for {@code saucedemo.feature}.
 */
public class SauceDemoSteps extends BaseCucumberSteps {

    @Given("the user is on the Sauce Demo login page")
    public void openLoginPage() {
        open();
    }

    @Given("the user is logged in as {string} with password {string}")
    public void login(String username, String password) {
        open();
        new LoginPage(getDriver()).login(username, password);
    }

    @When("the user logs in with username {string} and password {string}")
    public void performLogin(String username, String password) {
        new LoginPage(getDriver()).login(username, password);
    }

    @When("the user adds the first product to the cart")
    public void addFirstProductToCart() {
        new ProductsPage(getDriver()).addFirstProductToCart();
    }

    @Then("the products page is displayed")
    public void verifyProductsPage() {
        assertThat(By.className("title")).hasText("Products");
    }

    @Then("an error message containing {string} is displayed")
    public void verifyErrorMessage(String expectedText) {
        assertThat(By.cssSelector("[data-test='error']"))
                .isVisible()
                .containsText(expectedText);
    }

    @Then("the cart badge shows {string}")
    public void verifyCartBadge(String expectedCount) {
        assertThat(By.className("shopping_cart_badge")).hasText(expectedCount);
    }
}
