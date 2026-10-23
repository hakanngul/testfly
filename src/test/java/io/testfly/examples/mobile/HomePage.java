package io.testfly.examples.mobile;

import io.testfly.mobile.MobileFind;
import io.testfly.mobile.MobileLocator;
import io.testfly.mobile.MobilePage;
import io.testfly.mobile.WaitFor;

/** Home screen of the login example app. {@code new HomePage()} waits for it after the splash screen. */
public class HomePage extends MobilePage {

    // shortcut: Android ids of this screen are not known yet; add android = "<package>:id/…" when they are.
    @MobileFind(ios = "HomeScreen.loginButton")
    @WaitFor
    private MobileLocator loginButton;

    @MobileFind(ios = "cookieBannerAcceptAllButton")
    private MobileLocator cookieAcceptButton;

    @Override
    protected void onLoad() {
        tapIfShown(cookieAcceptButton); // the cookie banner may cover the page
    }

    public LoginPage openLogin() {
        loginButton.tap();
        return new LoginPage();
    }
}
