package io.testfly.examples.mobile;

import io.testfly.mobile.BaseMobileTest;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Native iOS login with a member id on an app that is already installed on the simulator.
 * Locators are iOS accessibility identifiers (see {@code getByTestId}).
 *
 * <pre>
 * mobilecli server start                      # keep it running
 * mobilecli agent install --device &lt;udid&gt;
 * MOBILE_DEVICE_ID=&lt;udid&gt; MOBILE_LOGIN_USER=&lt;member id&gt; MOBILE_LOGIN_PASSWORD=&lt;password&gt; \
 *   mvn test -Dtestfly.profile=mobile-ios-login -Dtest=IosLoginExampleTest
 * </pre>
 *
 * The app comes from {@code MOBILE_APP_BUNDLE_ID} (default in testfly-mobile-ios-login.yml).
 */
public class IosLoginExampleTest extends BaseMobileTest {

    private static final String HOME_LOGIN_BUTTON = "HomeScreen.loginButton";
    private static final String COOKIE_ACCEPT_BUTTON = "cookieBannerAcceptAllButton";
    private static final String USERNAME_FIELD = "LoginScreen.nameTextField";
    private static final String PASSWORD_FIELD = "LoginScreen.passwordTextField";
    private static final String LOGIN_BUTTON = "LoginScreen.loginButton";

    @Test
    public void loginWithMemberId() {
        String memberId = requiredEnv("MOBILE_LOGIN_USER");
        String password = requiredEnv("MOBILE_LOGIN_PASSWORD");

        // The app opens on a splash screen, then the home screen; a cookie banner may cover it.
        screen().getByTestId(HOME_LOGIN_BUTTON).waitForVisible();
        if (screen().getByTestId(COOKIE_ACCEPT_BUTTON).count() > 0) {
            screen().getByTestId(COOKIE_ACCEPT_BUTTON).tap();
            screen().getByTestId(COOKIE_ACCEPT_BUTTON).waitForHidden();
        }
        screen().getByTestId(HOME_LOGIN_BUTTON).tap();

        expect(screen().getByTestId(USERNAME_FIELD)).toBeVisible();
        screen().getByTestId(USERNAME_FIELD).fill(memberId);
        screen().getByTestId(PASSWORD_FIELD).fill(password);
        screen().getByTestId(LOGIN_BUTTON).tap();

        // The login form is gone once the session is open.
        expect(screen().getByTestId(USERNAME_FIELD)).toBeHidden();
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        Assert.assertTrue(value != null && !value.isBlank(), "Environment variable " + name + " is required");
        return value;
    }
}
