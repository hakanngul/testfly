package io.testfly.examples.mobile;

import io.testfly.mobile.MobileFind;
import io.testfly.mobile.MobileLocator;

/** Login form of the example app, one field per element for iOS and Android. */
public class LoginPage {

    // shortcut: only btnLogin's Android id is known; add the others as android = "<package>:id/…".
    @MobileFind(ios = "LoginScreen.nameTextField")
    public MobileLocator username;

    @MobileFind(ios = "LoginScreen.passwordTextField")
    public MobileLocator password;

    @MobileFind(ios = "LoginScreen.loginButton", android = "com.starlettech.letsbet.stage:id/btnLogin")
    public MobileLocator loginButton;

    public void signIn(String memberId, String secret) {
        username.fill(memberId);
        password.fill(secret);
        loginButton.tap();
    }
}
