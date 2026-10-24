package io.testfly.examples.mobile;

import io.testfly.mobile.MobileFind;
import io.testfly.mobile.MobileLocator;
import io.testfly.mobile.MobilePage;
import io.testfly.mobile.MobileTimeoutException;

import java.time.Duration;

/**
 * Home screen of the login example app. {@code new HomePage()} waits for it
 * after the splash screen.
 */
public class HomePage extends MobilePage {

    // shortcut: iOS ids from an iPhone 17 simulator, Android ids from the Pixel_10
    // emulator (stage build).
    // Not on Android (left iOS-only): live bets button, pre-match/live tabs, cookie
    // reject/settings buttons.

    // Header
    // No @WaitFor: a cookie sheet can hide this button from the view tree, so onLoad() waits for it or the sheet.
    @MobileFind(ios = "HomeScreen.loginButton", android = "com.starlettech.letsbet.stage:id/btnLogin")
    private MobileLocator loginButton;

    @MobileFind(ios = "HomeScreen.signInButton", android = "com.starlettech.letsbet.stage:id/btnSignUp")
    private MobileLocator signUpButton;

    @MobileFind(ios = "HomeScreen.languageStackView", android = "com.starlettech.letsbet.stage:id/btnLanguage")
    private MobileLocator languageSelector;

    @MobileFind(ios = "HomeScreen.languageLabel", android = "com.starlettech.letsbet.stage:id/tvLangCode")
    private MobileLocator languageLabel;

    // Content
    @MobileFind(ios = "HomeScreen.homeTableView", android = "com.starlettech.letsbet.stage:id/rvHome")
    private MobileLocator homeTable;

    @MobileFind(ios = "searchTextField", android = "com.starlettech.letsbet.stage:id/tvSearch")
    private MobileLocator searchField;

    @MobileFind(ios = "depositMoneyButton", android = "com.starlettech.letsbet.stage:id/btnDepositMoney")
    private MobileLocator depositButton;

    @MobileFind(ios = "discoverButton", android = "com.starlettech.letsbet.stage:id/btnCampaigns")
    private MobileLocator discoverButton;

    @MobileFind(ios = "PlayableWidgetFooterView.playNowButton")
    private MobileLocator liveBetsButton;

    @MobileFind(ios = "beforeMatchLabel")
    private MobileLocator preMatchTab;

    @MobileFind(ios = "liveLabel")
    private MobileLocator liveTab;

    @MobileFind(ios = "slotsButtonView", android = "com.starlettech.letsbet.stage:id/cardViewSlots")
    private MobileLocator slotsButton;

    // Tab bar
    @MobileFind(ios = "tabItem_0", android = "com.starlettech.letsbet.stage:id/nav_graph_home")
    private MobileLocator homeTab;

    @MobileFind(ios = "tabItem_1", android = "com.starlettech.letsbet.stage:id/nav_graph_bulletin")
    private MobileLocator sportsBettingTab;

    @MobileFind(ios = "tabItem_2", android = "com.starlettech.letsbet.stage:id/nav_graph_basket")
    private MobileLocator couponTab;

    @MobileFind(ios = "tabItem_3", android = "com.starlettech.letsbet.stage:id/nav_graph_bet_buddy")
    private MobileLocator letsShareTab;

    @MobileFind(ios = "tabItem_4", android = "com.starlettech.letsbet.stage:id/nav_graph_coupons")
    private MobileLocator myCouponsTab;

    // Cookie banner
    @MobileFind(ios = "cookieSettingBannerView")
    private MobileLocator cookieBanner;

    @MobileFind(ios = "cookieBannerAcceptAllButton", android = "com.starlettech.letsbet.stage:id/btnAcceptAll")
    private MobileLocator cookieAcceptButton;

    @MobileFind(ios = "cookieBannerRejectAllButton")
    private MobileLocator cookieRejectButton;

    @MobileFind(ios = "cookieBannerSettingsButton")
    private MobileLocator cookieSettingsButton;

    // iOS system prompt; it has no id and, while it is up, the view tree holds only the alert.
    @MobileFind(ios = "role=button;name=Ask App Not to Track")
    private MobileLocator trackingDeny;

    @Override
    protected void onLoad() {
        // The page is up once the login button or the cookie banner that covers it is shown.
        loginButton.or(cookieAcceptButton).first().waitForVisible();
        tapIfShown(cookieAcceptButton);
    }

    public LoginPage openLogin() {
        //dismissTrackingPrompt();
        tapIfShown(cookieAcceptButton); // may only show up after the prompt is gone
        loginButton.tap();
        return new LoginPage();
    }

    /** The prompt appears a few seconds after launch, so wait briefly for it; it is absent on some runs. */
    private void dismissTrackingPrompt() {
        try {
            trackingDeny.withTimeout(Duration.ofSeconds(5)).waitForVisible();
        } catch (MobileTimeoutException absent) {
            return;
        }
        trackingDeny.tap();
        trackingDeny.waitForHidden();
    }
}
