package io.testfly.examples.testng;

import io.testfly.examples.pages.LoginPage;
import io.testfly.test.BaseTest;
import org.openqa.selenium.By;
import org.testng.annotations.Test;

import java.time.Duration;

/**
 * Example demonstrating modern TestFly assertions against
 * https://www.saucedemo.com.
 *
 * <p>
 * Showcases:
 * <ul>
 * <li>Web-first assertions with {@code assertThat(By / Locator)}</li>
 * <li>Custom per-assertion timeouts with {@code within(Duration)}</li>
 * <li>Descriptive failure context with {@code as(String)}</li>
 * <li>New matchers: {@code hasAttribute}, {@code hasCssValue},
 * {@code isFocused}</li>
 * <li>Fluent soft assertions with {@code softAssert(locator)} and
 * {@code .softly()}</li>
 * </ul>
 *
 * <p>
 * Run with:
 * 
 * <pre>
 * mvn test -Dtest=io.testfly.examples.testng.SauceDemoAssertionsExampleTest
 * </pre>
 */
public class SauceDemoAssertionsExampleTest extends BaseTest {

        private static final String SAUCE_DEMO_URL = "https://www.saucedemo.com/";

        @Test
        public void testLoginPageElementsWithCustomModifiersAndMatchers() {
                open(SAUCE_DEMO_URL);

                // 1. Descriptive message + custom timeout
                assertThat(By.id("login-button"))
                                .as("Login button should be visible on initial load")
                                .within(Duration.ofSeconds(5))
                                .isVisible();

                // 2. State & interactability matchers
                assertThat(By.id("login-button"))
                                .as("Login button should be clickable")
                                .isEnabled()
                                .hasValue("Login");

                // 3. New matcher: Attribute presence check (regardless of attribute value)
                assertThat(By.id("user-name"))
                                .as("Username input should define a placeholder attribute")
                                .hasAttribute("placeholder");

                // 4. New matcher: CSS property validation
                assertThat(By.id("login-button"))
                                .as("Login button cursor style")
                                .hasCssValue("cursor", "pointer");
        }

        @Test
        public void testProductsCatalogWithCountAndText() {
                open(SAUCE_DEMO_URL);

                LoginPage loginPage = new LoginPage(getDriver());
                loginPage.login("standard_user", "secret_sauce");

                // Web-first auto-retrying wait for header title
                assertThat(By.className("title"))
                                .as("Products catalog title after login")
                                .within(Duration.ofSeconds(10))
                                .hasText("Products");

                // Count matcher: Verify 6 items are loaded in the inventory grid
                assertThat(By.className("inventory_item"))
                                .as("Default inventory item count")
                                .count(6);

                // First item text verification
                assertThat(By.cssSelector(".inventory_item_name"))
                                .containsText("Sauce Labs Backpack");
        }

        @Test
        public void testInventoryDashboardWithFluentSoftAssertions() {
                open(SAUCE_DEMO_URL);

                LoginPage loginPage = new LoginPage(getDriver());
                loginPage.login("standard_user", "secret_sauce");

                // Fluent soft assertions: all checks run even if one fails.
                // The framework collects failures and reports all at the end of the test.
                softAssert(By.className("title"))
                                .as("Page header verification")
                                .hasText("Products");

                softAssert(By.id("shopping_cart_container"))
                                .as("Shopping cart badge visibility")
                                .isVisible();

                softAssert(By.id("react-burger-menu-btn"))
                                .as("Side navigation menu button")
                                .isEnabled();

                // Using .softly() chain modifier directly on assertThat
                assertThat(By.cssSelector(".footer_copy"))
                                .as("Footer copyright text")
                                .softly()
                                .containsText("Sauce Labs");
        }

        @Test
        public void testLockedOutUserErrorBannerAssertion() {
                open(SAUCE_DEMO_URL);

                LoginPage loginPage = new LoginPage(getDriver());
                loginPage.login("locked_out_user", "secret_sauce");

                // Verify error banner appearance and exact error content
                assertThat(By.cssSelector("[data-test='error']"))
                                .as("Locked out user notification banner")
                                .within(Duration.ofSeconds(3))
                                .isVisible()
                                .containsText("Sorry, this user has been locked out.");
        }
}
