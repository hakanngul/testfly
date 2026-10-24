package io.testfly.unit.mobile;

import io.testfly.config.TestFlyConfig;
import io.testfly.exceptions.ConfigurationException;
import io.testfly.mobile.Mobile;
import io.testfly.mobile.MobileCrashReport;
import io.testfly.mobile.MobileDevice;
import io.testfly.mobile.MobileException;
import io.testfly.mobile.MobileAssertionError;
import io.testfly.mobile.MobileBounds;
import io.testfly.mobile.MobileLocator;
import io.testfly.mobile.MobileOrientation;
import io.testfly.mobile.MobileTimeoutException;
import io.testfly.mobile.MobileWebView;
import io.testfly.mobile.internal.MobileSessions;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static io.testfly.mobile.MobileAssertions.expect;
import static io.testfly.mobile.MobileAssertions.expectPoll;
import static org.testng.Assert.*;

/** Appium-parity extras on the fake app: gestures, clipboard, files, crash reports, webviews, settings, apps, keyboard, permission dialogs. */
public class MobileExtrasTest {

    private static TestFlyConfig.Mobile config(FakeMobilecliServer server, FakeApp app, String id) {
        app.install(server);
        TestFlyConfig.Mobile m = new TestFlyConfig.Mobile();
        m.setServerUrl(server.url());
        m.setPlatform(app.platform);
        m.setDeviceId(id);
        m.getApp().setBundleId(FakeApp.BUNDLE);
        m.getTimeouts().setActionMs(1500);
        m.getTimeouts().setPollMs(20);
        m.getTimeouts().setRpcMs(2000);
        m.getTimeouts().setReserveMs(2000);
        return m;
    }

    private static String uniqueId() {
        return "extras-" + Long.toHexString(System.nanoTime());
    }

    @Test
    public void gesturesClipboardFilesAndApps() throws Exception {
        String id = uniqueId();
        try (FakeMobilecliServer server = new FakeMobilecliServer()) {
            FakeApp app = new FakeApp(id);
            try (MobileDevice device = Mobile.connect(config(server, app, id))) {
                device.screen().getByText("Welcome back").pinchOut();
                assertEquals(app.lastPinch, "out@540,160");
                device.screen().getByText("Welcome back").pinchIn();
                assertTrue(app.lastPinch.startsWith("in@"));

                device.screen().getByPlaceholder("Username").dragTo(device.screen().getByText("Sign in"));
                assertEquals(app.lastGesture.size(), 5);
                assertEquals(app.lastGesture.get(0).path("y").asInt(), 320, "starts on the source centre");
                assertEquals(app.lastGesture.get(3).path("y").asInt(), 640, "ends on the target centre");

                device.setClipboard("hello");
                assertEquals(device.clipboard(), "hello");
                device.clearClipboard();
                assertEquals(device.clipboard(), "");

                Path local = Files.createTempFile("tf-push", ".txt");
                Files.writeString(local, "photo-bytes");
                device.pushFile(local, "/sdcard/Download/photo.jpg");
                Path pulled = device.pullFile("/sdcard/Download/photo.jpg", local.resolveSibling("tf-pulled-" + System.nanoTime() + ".txt"));
                assertEquals(Files.readString(pulled), "photo-bytes");
                expectThrows(MobileException.class, () -> device.pullFile("/missing", local));

                assertTrue(device.isAppInstalled(FakeApp.BUNDLE));
                assertFalse(device.isAppInstalled("com.other"));
                assertEquals(device.foregroundApp(), FakeApp.BUNDLE);
                device.backgroundApp(Duration.ofMillis(10));
                assertEquals(app.foreground, FakeApp.BUNDLE, "brought back after HOME");
                device.uninstallApp("com.other");
                assertEquals(app.uninstalled, "com.other");

                device.setAnimations(false);
                assertEquals(app.settings, "off/-");
                device.setAppearance("Dark");
                assertEquals(app.settings, "-/dark");
                expectThrows(IllegalArgumentException.class, () -> device.setAppearance("blue"));
            }
        }
    }

    @Test
    public void mobilewrightParity() {
        String id = uniqueId();
        try (FakeMobilecliServer server = new FakeMobilecliServer()) {
            FakeApp app = new FakeApp(id);
            app.showRemember = true;
            try (MobileDevice device = Mobile.connect(config(server, app, id))) {
                MobileLocator remember = device.screen().getByText("Remember me");
                remember.check();
                assertTrue(app.rememberMe);
                remember.check();
                assertTrue(app.rememberMe, "check() leaves a checked box alone");
                expect(remember).toBeChecked();
                remember.setChecked(false);
                assertFalse(app.rememberMe);
                remember.uncheck();
                assertFalse(app.rememberMe, "uncheck() leaves an unchecked box alone");

                MobileLocator welcome = device.screen().getByText("Welcome back");
                assertEquals(welcome.bounds(), new MobileBounds(40, 100, 1000, 120));
                assertEquals(welcome.screenshot().length, 4);
                assertEquals(app.lastScreenshot.path("clip").toString(), "{\"x\":40,\"y\":100,\"width\":1000,\"height\":120}");

                List<MobileLocator> fields = device.screen().getByType("android.widget.EditText").all();
                assertEquals(fields.size(), 2);
                assertEquals(fields.get(1).getAttribute("label"), "Password");

                MobileLocator signIn = device.screen().getByText("Sign in");
                signIn.waitForDisabled();
                expectThrows(MobileTimeoutException.class, () -> signIn.withTimeout(Duration.ofMillis(200)).waitForEnabled());
                expect(device.screen().getByPlaceholder("Username")).toBeEmpty();
                device.screen().getByPlaceholder("Username").fill("ada");
                signIn.waitForEnabled();
                expect(device.screen().getByPlaceholder("Username")).not().toBeEmpty();
                expect(welcome).toHaveText("  WELCOME   back ", true);
                expectThrows(MobileAssertionError.class,
                        () -> expect(welcome).withTimeout(Duration.ofMillis(100)).toHaveText("welcome", true));

                device.screen().tap(500, 640);
                assertEquals(app.screen, "home", "a raw tap on the Sign in button");

                device.setFoldState("half-open");
                assertEquals(app.fold, "half-open");
                expectThrows(IllegalArgumentException.class, () -> device.setFoldState("bent"));
                assertEquals(device.installedApps(), List.of(FakeApp.BUNDLE));
                device.launchApp(FakeApp.BUNDLE, List.of("fr-FR", "en-US"));
                assertEquals(app.lastLaunch.path("locales").toString(), "[\"fr-FR\",\"en-US\"]");

                expectPoll(device::orientation).toBe(MobileOrientation.PORTRAIT);
                MobileAssertionError e = expectThrows(MobileAssertionError.class,
                        () -> expectPoll(device::foregroundApp).withTimeout(Duration.ofMillis(300)).toBe("com.other"));
                assertTrue(e.getMessage().contains("last value: " + FakeApp.BUNDLE), e.getMessage());
            }
        }
    }

        @Test
    public void settingsFromConfigAreAppliedWhenTheDeviceOpens() {
        String id = uniqueId();
        try (FakeMobilecliServer server = new FakeMobilecliServer()) {
            FakeApp app = new FakeApp(id);
            TestFlyConfig.Mobile m = config(server, app, id);
            m.setAnimations("off");
            m.setAppearance("light");
            try (MobileDevice ignored = Mobile.connect(m)) {
                assertEquals(app.settings, "off/light");
            }
            m.setAnimations("slow");
            expectThrows(ConfigurationException.class, () -> Mobile.connect(m));
        }
    }

    @Test
    public void webViews() {
        String id = uniqueId();
        try (FakeMobilecliServer server = new FakeMobilecliServer()) {
            FakeApp app = new FakeApp(id);
            try (MobileDevice device = Mobile.connect(config(server, app, id))) {
                assertEquals(device.webViews().size(), 2);
                MobileWebView terms = device.webView();
                assertEquals(terms.id(), "wv-1");
                assertEquals(terms.title(), "Terms");
                assertTrue(terms.content().contains("<h1>Terms</h1>"));
                assertEquals(terms.query("h1").get(0).get("text"), "Terms");
                assertEquals(terms.evaluate("1+1"), 3, "the fake answers the expression length");
                terms.navigate("https://demo.testfly.dev/privacy");
                terms.waitForLoad();
                assertEquals(terms.url(), "https://demo.testfly.dev/privacy");
            }
        }
    }

    @Test
    public void keyboardOnBothPlatforms() {
        for (String platform : new String[] {"android", "ios"}) {
            String id = uniqueId();
            try (FakeMobilecliServer server = new FakeMobilecliServer()) {
                FakeApp app = new FakeApp(id);
                app.platform = platform;
                try (MobileDevice device = Mobile.connect(config(server, app, id))) {
                    assertFalse(device.isKeyboardShown());
                    device.hideKeyboard(); // nothing to hide
                    app.keyboard = true;
                    assertTrue(device.isKeyboardShown(), platform);
                    device.hideKeyboard();
                    assertFalse(app.keyboard, platform);
                }
            }
        }
    }

    @Test
    public void alertsAcceptOrDenyTheAndroidPermissionDialog() {
        for (String mode : new String[] {"accept", "dismiss"}) {
            String id = uniqueId();
            try (FakeMobilecliServer server = new FakeMobilecliServer()) {
                FakeApp app = new FakeApp(id);
                TestFlyConfig.Mobile m = config(server, app, id);
                m.setAlerts(mode);
                try (MobileDevice device = Mobile.connect(m)) {
                    app.permissionDialog = true;
                    expect(device.screen().getByText("Welcome back")).toBeVisible();
                }
                assertEquals(app.permissionAnswer, mode.equals("accept") ? "allow" : "deny");
            }
        }
    }

    @Test
    public void newCrashReportsAreSavedWhenATestFails() throws Exception {
        String id = uniqueId();
        try (FakeMobilecliServer server = new FakeMobilecliServer()) {
            FakeApp app = new FakeApp(id);
            app.crashes.add("old-crash");
            TestFlyConfig.Mobile m = config(server, app, id);
            MobileSessions.setOpener(p -> Mobile.connect(m));
            try {
                MobileDevice device = MobileSessions.open();
                assertEquals(device.crashReports(), List.of(new MobileCrashReport("old-crash", "Demo", "2026-10-24-120000")));
                app.crashes.add("new-crash");
                MobileSessions.captureFailure("extras.crashTest");
                Path saved = Path.of("target/reports/crashes/extras.crashTest_new-crash.txt");
                assertTrue(Files.readString(saved).contains("EXC_CRASH (new-crash)"));
                assertFalse(Files.exists(Path.of("target/reports/crashes/extras.crashTest_old-crash.txt")), "only crashes of this test");
            } finally {
                MobileSessions.close();
                MobileSessions.setOpener(null);
            }
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
