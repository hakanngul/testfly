package io.testfly.examples.testng;

import io.testfly.examples.pages.LoginPage;
import io.testfly.test.BaseTest;
import org.openqa.selenium.By;
import org.testng.annotations.Test;

/**
 * Failing Web UI test to demonstrate Playwright-style video recording on test failure.
 *
 * <p>When executed with {@code recording.mode: retain-on-failure} in {@code testfly.yml}:
 * <ul>
 *   <li>The browser session is recorded frame-by-frame.</li>
 *   <li>Upon failure, the recording is saved to {@code target/recordings/}.</li>
 *   <li>The animated video is embedded directly in the TestFly HTML report and Allure.</li>
 * </ul>
 *
 * <p>Run explicitly with:
 * <pre>mvn test -Dtest=io.testfly.examples.testng.FailingWebUiRecordingTest</pre>
 */
public class FailingWebUiRecordingTest extends BaseTest {

    @Test(priority = 1)
    public void successfulLoginTest() {
        // Step 1: Open the website
        open();

        // Step 2: Login successfully
        LoginPage loginPage = new LoginPage(getDriver());
        loginPage.login("standard_user", "secret_sauce");

        // Step 3: Assertion succeeds — recording will be discarded (no video saved)
        assertThat(By.className("title")).hasText("Products");
        assertThat(By.className("inventory_item")).isVisible();
    }

    @Test(priority = 2)
    public void successfulSauceDemoTitleTest() {
        // Step 1: Open the website
        open();

        // Step 2: Assert header title succeeds — recording will be discarded (no video saved)
        assertThat(By.className("login_logo")).hasText("Swag Labs");
    }

    @Test(priority = 3)
    public void failingWebUiTestWithVideoRecording() {
        // Step 1: Open the website
        open();

        // Step 2: Perform UI interactions
        LoginPage loginPage = new LoginPage(getDriver());
        loginPage.login("standard_user", "secret_sauce");

        // Step 3: Intentional assertion failure to trigger retain-on-failure video recording
        assertThat(By.className("title"))
                .hasText("Non-Existent Page Title That Fails On Purpose");
    }
}
