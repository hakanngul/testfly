package io.testfly.examples.mobile;

import io.testfly.mobile.MobileFind;
import io.testfly.mobile.MobileLocator;
import io.testfly.mobile.MobilePage;
import io.testfly.mobile.WaitFor;

/** Login form of the example app, one field per element for iOS and Android. */
public class LoginPage extends MobilePage {

    // shortcut: only the login button's Android id is known; add the others as android = "<package>:id/…".
    @MobileFind(ios = "LoginScreen.nameTextField")
    @WaitFor
    private MobileLocator username;

    @MobileFind(ios = "LoginScreen.passwordTextField")
    private MobileLocator password;

    @MobileFind(ios = "LoginScreen.loginButton", android = "com.starlettech.letsbet.stage:id/btnLogin")
    private MobileLocator loginButton;

    public LoginPage signIn(String memberId, String secret) {
        username.fill(memberId);
        password.fill(secret);
        loginButton.tap();
        return this;
    }

    /** The form is gone once the session is open. */
    public void shouldBeClosed() {
        expect(username).toBeHidden();
    }
}
