package io.testfly.examples.mobile;

import io.testfly.mobile.MobileFind;
import io.testfly.mobile.MobileLocator;

/** Home screen of the login example app; created with {@code page(HomePage.class)}. */
public class HomePage {

    // shortcut: Android ids of this screen are not known yet; add android = "<package>:id/…" when they are.
    @MobileFind(ios = "HomeScreen.loginButton")
    public MobileLocator loginButton;

    @MobileFind(ios = "cookieBannerAcceptAllButton")
    public MobileLocator cookieAcceptButton;

    /** Waits for the home screen (after the splash) and closes the cookie banner if it covers it. */
    public HomePage ready() {
        loginButton.waitForVisible();
        if (cookieAcceptButton.count() > 0) {
            cookieAcceptButton.tap();
            cookieAcceptButton.waitForHidden();
        }
        return this;
    }
}
