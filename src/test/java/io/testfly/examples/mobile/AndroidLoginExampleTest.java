package io.testfly.examples.mobile;

import io.testfly.mobile.BaseMobileTest;
import io.testfly.mobile.MobilePlatform;
import io.testfly.mobile.MobileTest;

import java.util.stream.Stream;

import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Native Android login with a member id on an app that is already installed on the emulator.
 * Locators are Android resource ids; {@code getByTestId("btnLogin")} also matches
 * {@code <package>:id/btnLogin}.
 *
 * <pre>
 * emulator -avd &lt;name&gt;
 * mobilecli server start                      # keep it running
 * MOBILE_LOGIN_USER=&lt;member id&gt; MOBILE_LOGIN_PASSWORD=&lt;password&gt; \
 *   mvn test -Dtestfly.profile=mobile-android-login -Dtest=AndroidLoginExampleTest
 * </pre>
 *
 * The app comes from {@code MOBILE_APP_BUNDLE_ID} (default in testfly-mobile-android-login.yml).
 * Texts assume an English app.
 */
public class AndroidLoginExampleTest extends BaseMobileTest {

    private static final String COOKIE_ACCEPT_BUTTON = "btnAcceptAll";
    private static final String HOME_LOGIN_BUTTON = "btnLogin";
    private static final String USERNAME_FIELD = "etUserName";
    private static final String PASSWORD_FIELD = "etPassword";
    private static final String DAY_30_ACCOUNT_SUMMARY= "tvTitle";
    private static final String POP_UP_CONFIRM_BTN = "btnConfirm";

    @Test
    @MobileTest (platform = MobilePlatform.ANDROID)
    public void loginWithMemberId() {
        String memberId = MobileEnv.required("MOBILE_LOGIN_USER");
        String password = MobileEnv.required("MOBILE_LOGIN_PASSWORD");

        // A cookie consent sheet covers the home screen on a fresh start.
        screen().getByTestId(HOME_LOGIN_BUTTON).or(screen().getByTestId(COOKIE_ACCEPT_BUTTON)).first()
                .waitForVisible();
        if (screen().getByTestId(COOKIE_ACCEPT_BUTTON).count() > 0) {
            screen().getByTestId(COOKIE_ACCEPT_BUTTON).tap();
            screen().getByTestId(COOKIE_ACCEPT_BUTTON).waitForHidden();
        }
        screen().getByTestId(HOME_LOGIN_BUTTON).tap();

        expect(screen().getByTestId(USERNAME_FIELD)).toBeVisible();
        screen().getByTestId(USERNAME_FIELD).fill(memberId);
        screen().getByTestId(PASSWORD_FIELD).fill(password);
        // The form's submit button has the same id as the home screen's "LOG IN" button.
        screen().getByTestId(HOME_LOGIN_BUTTON).tap();

        // The login form is gone once the session is open.
        expect(screen().getByTestId(USERNAME_FIELD)).toBeHidden();

        expect(screen().getByTestId(DAY_30_ACCOUNT_SUMMARY)).toBeVisible();

        screen().getByTestId(POP_UP_CONFIRM_BTN).tap();

        expect(screen().getByTestId(DAY_30_ACCOUNT_SUMMARY)).toBeHidden();

    }
}
