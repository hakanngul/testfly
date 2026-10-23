package io.testfly.unit.mobile;

import io.testfly.config.TestFlyConfig;
import io.testfly.mobile.Mobile;
import io.testfly.mobile.MobileDevice;
import io.testfly.mobile.MobileException;
import io.testfly.mobile.MobileFind;
import io.testfly.mobile.MobileLocator;
import io.testfly.mobile.MobileLocatorException;
import io.testfly.mobile.MobilePages;
import io.testfly.mobile.internal.SelectorParser;
import org.testng.annotations.Test;

import static io.testfly.mobile.MobileAssertions.expect;
import static org.testng.Assert.*;

/** {@link MobileFind} page objects and selector strings against the fake app on both platforms. */
public class MobilePagesTest {

    static class BasePage {
        @MobileFind("role=button;name=Sign in")
        MobileLocator signIn;
    }

    static class LoginPage extends BasePage {
        // Different fields per platform, so the test sees which selector was picked.
        @MobileFind(ios = "label=Password", android = "dev.testfly.demo:id/username")
        MobileLocator field;

        @MobileFind(ios = "text=Welcome back")
        MobileLocator iosOnly;
    }

    static class BadPage {
        @MobileFind("username")
        String field;
    }

    private static MobileDevice connect(FakeMobilecliServer server, String platform) {
        String id = platform + "-" + Long.toHexString(System.nanoTime());
        FakeApp app = new FakeApp(id);
        app.platform = platform;
        app.install(server);
        TestFlyConfig.Mobile m = new TestFlyConfig.Mobile();
        m.setServerUrl(server.url());
        m.setPlatform(platform);
        m.setDeviceId(id);
        m.getApp().setBundleId(FakeApp.BUNDLE);
        m.getTimeouts().setActionMs(1500);
        m.getTimeouts().setPollMs(20);
        m.getTimeouts().setRpcMs(2000);
        m.getTimeouts().setReserveMs(2000);
        return Mobile.connect(m);
    }

    @Test
    public void picksThePlatformSelectorAndFallsBackToValue() {
        for (String platform : new String[] {"ios", "android"}) {
            try (FakeMobilecliServer server = new FakeMobilecliServer(); MobileDevice device = connect(server, platform)) {
                LoginPage page = MobilePages.create(LoginPage.class, device.screen());
                page.field.fill("ada");
                if (platform.equals("ios")) {
                    expect(device.screen().getByLabel("Password")).toHaveText("ada");
                    expect(page.signIn).toBeDisabled(); // username still empty
                    expect(page.iosOnly).toBeVisible();
                } else {
                    expect(device.screen().getByPlaceholder("Username")).toHaveText("ada");
                    page.signIn.tap(); // inherited, value() selector
                    expect(device.screen().getByText("Hello, ada")).toBeVisible();
                    MobileLocatorException e = expectThrows(MobileLocatorException.class, () -> page.iosOnly.count());
                    assertTrue(e.getMessage().contains("LoginPage.iosOnly: no android selector"), e.getMessage());
                }
            }
        }
    }

    @Test
    public void rejectsFieldsThatAreNotLocators() {
        try (FakeMobilecliServer server = new FakeMobilecliServer(); MobileDevice device = connect(server, "android")) {
            MobileException e = expectThrows(MobileException.class, () -> MobilePages.create(BadPage.class, device.screen()));
            assertTrue(e.getMessage().contains("BadPage.field must be a non-static MobileLocator"), e.getMessage());
        }
    }

    @Test
    public void parsesSelectorStrings() {
        assertEquals(SelectorParser.parse("LoginScreen.loginButton").describe(), "getByTestId(\"LoginScreen.loginButton\")");
        assertEquals(SelectorParser.parse("testid=com.x:id/btn").describe(), "getByTestId(\"com.x:id/btn\")");
        assertEquals(SelectorParser.parse("text = Sign in").describe(), "getByText(\"Sign in\")");
        assertEquals(SelectorParser.parse("label=Password").describe(), "getByLabel(\"Password\")");
        assertEquals(SelectorParser.parse("placeholder=Username").describe(), "getByPlaceholder(\"Username\")");
        assertEquals(SelectorParser.parse("type=XCUIElementTypeSwitch").describe(), "getByType(\"XCUIElementTypeSwitch\")");
        assertEquals(SelectorParser.parse("role=button").describe(), "getByRole(button)");
        assertTrue(SelectorParser.parse("role=Button;name=Sign in").describe().contains("Sign in"));

        for (String bad : new String[] {"", " ", "text=", "colour=red", "role=wizard", "role=button;label=x", "role=button;name="}) {
            MobileLocatorException e = expectThrows(MobileLocatorException.class, () -> SelectorParser.parse(bad));
            assertTrue(e.getMessage().contains("Formats:"), e.getMessage());
        }
    }

    private static <E extends Throwable> E expectThrows(Class<E> type, Runnable body) {
        try {
            body.run();
        } catch (Throwable t) {
            if (type.isInstance(t)) {
                return type.cast(t);
            }
            throw new AssertionError("expected " + type.getSimpleName() + " but got " + t, t);
        }
        throw new AssertionError("expected " + type.getSimpleName());
    }
}
