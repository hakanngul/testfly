package io.testfly.unit.mobile;

import io.testfly.config.TestFlyConfig;
import io.testfly.exceptions.ConfigurationException;
import io.testfly.mobile.DeviceSelectionException;
import io.testfly.mobile.Mobile;
import io.testfly.mobile.MobileAssertionError;
import io.testfly.mobile.MobileDevice;
import io.testfly.mobile.MobileLocator;
import io.testfly.mobile.MobileLocatorException;
import io.testfly.mobile.MobilePlatform;
import io.testfly.mobile.MobileRole;
import io.testfly.mobile.MobileScreen;
import io.testfly.mobile.MobileTimeoutException;
import io.testfly.mobile.MobileVersionException;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

import static io.testfly.mobile.MobileAssertions.expect;
import static io.testfly.unit.mobile.FakeMobilecliServer.MAPPER;
import static org.testng.Assert.*;

/** {@link Mobile#connect} end to end against the fake mobilecli server and a small fake app. */
public class MobileDeviceTest {

    private static TestFlyConfig.Mobile config(FakeMobilecliServer server, String deviceId) {
        TestFlyConfig.Mobile m = new TestFlyConfig.Mobile();
        m.setServerUrl(server.url());
        m.setPlatform("android");
        m.setDeviceId(deviceId);
        m.getApp().setBundleId(FakeApp.BUNDLE);
        m.getTimeouts().setActionMs(1500);
        m.getTimeouts().setPollMs(20);
        m.getTimeouts().setRpcMs(2000);
        m.getTimeouts().setReserveMs(2000);
        return m;
    }

    private static String uniqueId() {
        return "emu-" + Long.toHexString(System.nanoTime());
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

    @Test
    public void signsInWithAutoWaitingLocatorsAndAssertions() {
        String id = uniqueId();
        try (FakeMobilecliServer server = new FakeMobilecliServer()) {
            FakeApp app = new FakeApp(id).install(server);
            try (MobileDevice device = Mobile.connect(config(server, id))) {
                assertEquals(device.id(), id);
                assertEquals(device.platform(), MobilePlatform.ANDROID);
                // relaunch: terminate, then launch
                List<String> methods = server.methods();
                assertTrue(methods.indexOf("device.apps.terminate") < methods.indexOf("device.apps.launch"), methods.toString());

                MobileScreen screen = device.screen();
                MobileLocator signIn = screen.getByRole(MobileRole.BUTTON, "Sign in");
                expect(screen.getByText("Welcome back")).toBeVisible();
                expect(signIn).toBeDisabled();
                expect(screen.getByRole(MobileRole.TEXTFIELD)).toHaveCount(2);

                screen.getByTestId("username").fill("ad");
                screen.getByTestId("username").append("a");
                expect(screen.getByPlaceholder("Username")).toHaveText("ada");
                screen.getByTestId("username").type("ada");
                expect(screen.getByPlaceholder("Username")).toHaveText("ada");
                screen.getByLabel("Password").fill("s3cret");
                assertEquals(app.password, "s3cret");
                expect(signIn).toBeEnabled();

                app.buttonMoves = 3; // the button animates in: tap waits for stable bounds
                signIn.tap();

                expect(screen.getByTestId("greeting")).toHaveText(Pattern.compile("Hello, ada"));
                expect(screen.getByText("Orders").within(screen.getByType("androidx.recyclerview.widget.RecyclerView"))).toBeVisible();
                expect(screen.getByText("Sign out").within(screen.getByType("androidx.recyclerview.widget.RecyclerView"))).toHaveCount(0);
                expect(screen.getByRole(MobileRole.LISTITEM).filterHasText("sett")).toHaveText("Settings");
                expect(screen.getByText("Welcome back")).toBeHidden();
                expect(screen.getByText("Welcome back")).not().toBeVisible();
                assertEquals(screen.getByTestId("greeting").getText(), "Hello, ada");
                assertEquals(device.screenshot().length, 4);
            }
            assertEquals(app.foreground, "com.android.launcher", "close() terminates the app");
        }
    }

    @Test
    public void strictModeAndTimeoutsExplainWhatTheySaw() {
        String id = uniqueId();
        try (FakeMobilecliServer server = new FakeMobilecliServer()) {
            new FakeApp(id).install(server);
            try (MobileDevice device = Mobile.connect(config(server, id))) {
                MobileScreen screen = device.screen();
                MobileLocatorException strict = expectThrows(MobileLocatorException.class,
                        () -> screen.getByRole(MobileRole.TEXTFIELD).fill("x"));
                assertTrue(strict.getMessage().contains("matched 2 elements"), strict.getMessage());
                assertTrue(strict.getMessage().contains(":id/username"), "lists the candidates");
                screen.getByRole(MobileRole.TEXTFIELD).first().fill("first-field");

                MobileTimeoutException disabled = expectThrows(MobileTimeoutException.class,
                        () -> screen.getByText("Welcome back").and(screen.getByRole(MobileRole.BUTTON)).withTimeout(Duration.ofMillis(1000)).tap());
                assertTrue(disabled.getMessage().contains("no element matches"), disabled.getMessage());

                screen.getByTestId("username").clear();
                MobileTimeoutException notEnabled = expectThrows(MobileTimeoutException.class,
                        () -> screen.getByText("Sign in").withTimeout(Duration.ofMillis(1000)).tap());
                assertTrue(notEnabled.getMessage().contains("the element is disabled"), notEnabled.getMessage());

                MobileAssertionError failed = expectThrows(MobileAssertionError.class,
                        () -> expect(screen.getByText("Welcome back"), "login header").withTimeout(Duration.ofMillis(1000)).toHaveText("Bye"));
                assertTrue(failed.getMessage().startsWith("login header\nexpect(getByText(\"Welcome back\")).toHaveText(\"Bye\") failed after 1000 ms"),
                        failed.getMessage());
            }
        }
    }

    @Test
    public void slowViewTreeReadsDoNotOutliveTheBudget() {
        String id = uniqueId();
        try (FakeMobilecliServer server = new FakeMobilecliServer()) {
            new FakeApp(id).install(server);
            try (MobileDevice device = Mobile.connect(config(server, id))) {
                server.on("device.dump.ui", p -> FakeMobilecliServer.delay(5000, MAPPER.createObjectNode().putArray("elements")));
                long start = System.nanoTime();
                MobileTimeoutException e = expectThrows(MobileTimeoutException.class,
                        () -> device.screen().getByText("Welcome back").withTimeout(Duration.ofMillis(400)).tap());
                long ms = Duration.ofNanos(System.nanoTime() - start).toMillis();
                assertTrue(ms < 2000, "took " + ms + " ms");
                assertTrue(e.getMessage().contains("timed out"), e.getMessage());
            }
        }
    }

    @Test
    public void connectFailsFastOnBadConfigurationAndOldServers() {
        try (FakeMobilecliServer server = new FakeMobilecliServer()) {
            String id = uniqueId();
            new FakeApp(id).install(server);
            TestFlyConfig.Mobile noPlatform = config(server, id);
            noPlatform.setPlatform(null);
            assertTrue(expectThrows(ConfigurationException.class, () -> Mobile.connect(noPlatform)).getMessage().contains("mobile.platform"));
            TestFlyConfig.Mobile badReset = config(server, id);
            badReset.getApp().setReset("reinstall");
            expectThrows(ConfigurationException.class, () -> Mobile.connect(badReset));
            TestFlyConfig.Mobile badPoll = config(server, id);
            badPoll.getTimeouts().setPollMs(0);
            expectThrows(ConfigurationException.class, () -> Mobile.connect(badPoll));

            TestFlyConfig.Mobile ios = config(server, "");
            ios.setPlatform("ios");
            ios.setDeviceName("Galaxy");
            expectThrows(DeviceSelectionException.class, () -> Mobile.connect(ios));

            server.on("server.info", p -> MAPPER.createObjectNode().put("name", "mobilecli").put("version", "1.0.3"));
            expectThrows(MobileVersionException.class, () -> Mobile.connect(config(server, id)));
            assertFalse(server.methods().contains("devices.list") && server.methods().lastIndexOf("devices.list") > server.methods().lastIndexOf("server.info"),
                    "nothing else is called after a failed version check");
        }
    }

    @Test
    public void aSecondSessionWaitsForTheDevice() throws Exception {
        String id = uniqueId();
        try (FakeMobilecliServer server = new FakeMobilecliServer()) {
            new FakeApp(id).install(server);
            TestFlyConfig.Mobile cfg = config(server, id);
            cfg.getTimeouts().setReserveMs(150);
            try (MobileDevice first = Mobile.connect(cfg)) {
                assertNotNull(first);
                expectThrows(MobileTimeoutException.class, () -> Mobile.connect(cfg));
            }
            try (MobileDevice again = Mobile.connect(cfg)) {
                assertEquals(again.id(), id, "closing releases the device");
            }
        }
    }

    @Test
    public void aDevicePoolGivesParallelSessionsDifferentDevices() {
        String a = uniqueId();
        String b = uniqueId();
        try (FakeMobilecliServer server = new FakeMobilecliServer()) {
            new FakeApp(a, b).install(server);
            TestFlyConfig.Mobile cfg = config(server, "");
            cfg.setDevices(List.of(a, b));
            cfg.getTimeouts().setReserveMs(150);
            try (MobileDevice first = Mobile.connect(cfg); MobileDevice second = Mobile.connect(cfg)) {
                assertEquals(List.of(first.id(), second.id()), List.of(a, b));
                expect(second.screen().getByText("Welcome back")).toBeVisible();
                MobileTimeoutException busy = expectThrows(MobileTimeoutException.class, () -> Mobile.connect(cfg));
                assertTrue(busy.getMessage().contains("All 2 pool devices"), busy.getMessage());
            }
            TestFlyConfig.Mobile byName = config(server, "");
            byName.setPool(true);
            byName.setDeviceName("Pixel 8");
            try (MobileDevice pooled = Mobile.connect(byName)) {
                assertEquals(pooled.id(), a, "mobile.pool takes every online match, first free one first");
            }

            TestFlyConfig.Mobile both = config(server, a);
            both.setDevices(List.of(a, b));
            expectThrows(ConfigurationException.class, () -> Mobile.connect(both));
        }
    }

    @Test
    public void resetNoneLeavesTheAppAlone() {
        String id = uniqueId();
        try (FakeMobilecliServer server = new FakeMobilecliServer()) {
            FakeApp app = new FakeApp(id).install(server);
            app.foreground = FakeApp.BUNDLE;
            TestFlyConfig.Mobile cfg = config(server, id);
            cfg.getApp().setReset("none");
            try (MobileDevice device = Mobile.connect(cfg)) {
                expect(device.screen().getByText("Welcome back")).toBeVisible();
            }
            assertFalse(server.methods().contains("device.apps.launch"));
            assertFalse(server.methods().contains("device.apps.terminate"));
            assertEquals(app.foreground, FakeApp.BUNDLE);
        }
    }

    // ── device settings and recording ───────────────────────────────────

    @Test
    public void rotatesAndOverridesTheLocationAndRestoresBothOnClose() {
        String id = uniqueId();
        try (FakeMobilecliServer server = new FakeMobilecliServer()) {
            FakeApp app = new FakeApp(id).install(server);
            try (MobileDevice device = Mobile.connect(config(server, id))) {
                assertEquals(device.orientation(), io.testfly.mobile.MobileOrientation.PORTRAIT);
                device.setOrientation(io.testfly.mobile.MobileOrientation.LANDSCAPE);
                assertEquals(device.orientation(), io.testfly.mobile.MobileOrientation.LANDSCAPE);

                device.setLocation(41.0082, 28.9784);
                assertEquals(app.location, "41.0082,28.9784");
                expectThrows(IllegalArgumentException.class, () -> device.setLocation(91, 0));
            }
            assertEquals(app.orientation, "portrait", "the orientation before the first change is restored");
            assertNull(app.location, "the location override is cleared");
        }
    }

    @Test
    public void recordsOnRequestAndKeepsAutomaticRecordingsOnlyWhenAsked() throws Exception {
        String id = uniqueId();
        try (FakeMobilecliServer server = new FakeMobilecliServer()) {
            FakeApp app = new FakeApp(id).install(server);

            // Manual recording.
            java.nio.file.Path manual = java.nio.file.Files.createTempDirectory("tf-rec-").resolve("manual.mp4");
            try (MobileDevice device = Mobile.connect(config(server, id))) {
                device.startRecording(manual);
                assertEquals(app.recordingTo, manual.toAbsolutePath().toString());
                expectThrows(IllegalStateException.class, () -> device.startRecording(manual));
                assertEquals(device.stopRecording(), manual);
                assertTrue(java.nio.file.Files.exists(manual));
                assertNull(device.stopRecording(), "nothing is recording any more");
            }

            // recordVideo: failures — a passed test's recording is discarded at close.
            TestFlyConfig.Mobile failures = config(server, id);
            failures.setRecordVideo("failures");
            String discarded;
            try (MobileDevice device = Mobile.connect(failures)) {
                discarded = app.recordingTo;
                assertNotNull(discarded, "the recording starts with the device");
            }
            assertNull(app.recordingTo);
            assertFalse(java.nio.file.Files.exists(java.nio.file.Path.of(discarded)), "a passed test keeps no video");

            // recordVideo: failures — a failed test keeps it under target/reports/videos.
            try (MobileDevice device = Mobile.connect(failures)) {
                java.nio.file.Path kept = device.saveRecording("my test");
                assertNotNull(kept);
                assertTrue(kept.toString().contains("reports" + java.io.File.separator + "videos"), kept.toString());
                assertTrue(kept.getFileName().toString().startsWith("my_test_mobile_"), kept.toString());
                assertTrue(java.nio.file.Files.exists(kept));
                java.nio.file.Files.delete(kept);
            }

            // recordVideo: always — kept at close.
            TestFlyConfig.Mobile always = config(server, id);
            always.setRecordVideo("always");
            try (MobileDevice device = Mobile.connect(always)) {
                assertNotNull(app.recordingTo);
            }
            assertNull(app.recordingTo, "stopped at close");
            try (var files = java.nio.file.Files.list(java.nio.file.Path.of("target", "reports", "videos"))) {
                // No test id outside a runner: the file is named after the device.
                java.util.List<java.nio.file.Path> saved = files.filter(f -> f.getFileName().toString().startsWith(id + "_mobile_")).toList();
                assertEquals(saved.size(), 1, String.valueOf(saved));
                java.nio.file.Files.delete(saved.get(0));
            }
        }
    }

    @Test
    public void rejectsAnUnknownRecordingMode() {
        TestFlyConfig.Mobile m = new TestFlyConfig.Mobile();
        m.setPlatform("android");
        m.setRecordVideo("sometimes");
        ConfigurationException e = expectThrows(ConfigurationException.class, () -> Mobile.connect(m));
        assertTrue(e.getMessage().contains("mobile.recordVideo must be off, failures or always"), e.getMessage());
    }
}
