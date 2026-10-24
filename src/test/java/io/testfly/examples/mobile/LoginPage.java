package io.testfly.examples.mobile;

import io.testfly.mobile.MobileFind;
import io.testfly.mobile.MobileLocator;
import io.testfly.mobile.MobilePage;
import io.testfly.mobile.WaitFor;

/** Login form of the example app, one field per element for iOS and Android. */
public class LoginPage extends MobilePage {

    // shortcut: iOS ids from an iPhone 17 simulator, Android ids from the Pixel_10 emulator (stage build).
    // Face ID checkbox does not exist on Android (left iOS-only).
    @MobileFind(ios = "cancelButton", android = "com.starlettech.letsbet.stage:id/back")
    private MobileLocator cancelButton;

    @MobileFind(ios = "LoginScreen.nameTextField", android = "com.starlettech.letsbet.stage:id/etUserName")
    @WaitFor
    private MobileLocator username;

    @MobileFind(ios = "LoginScreen.passwordTextField", android = "com.starlettech.letsbet.stage:id/etPassword")
    private MobileLocator password;

    @MobileFind(ios = "LoginScreen.sessionButton", android = "com.starlettech.letsbet.stage:id/rbKeepLogin")
    private MobileLocator rememberMeCheckbox;

    @MobileFind(ios = "LoginScreen.userInfoButton", android = "com.starlettech.letsbet.stage:id/rbAskLogin")
    private MobileLocator askCredentialsCheckbox;

    @MobileFind(ios = "LoginScreen.faceIDButton")
    private MobileLocator faceIdCheckbox;

    @MobileFind(ios = "LoginScreen.loginButton", android = "com.starlettech.letsbet.stage:id/btnLogin")
    private MobileLocator loginButton;

    @MobileFind(ios = "LoginScreen.forgotPasswordButton", android = "com.starlettech.letsbet.stage:id/tvForgotPassword")
    private MobileLocator forgotPasswordButton;

    @MobileFind(ios = "LoginScreen.registerButton", android = "com.starlettech.letsbet.stage:id/tvSignUp")
    private MobileLocator registerButton;

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
