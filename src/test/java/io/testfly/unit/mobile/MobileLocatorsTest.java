package io.testfly.unit.mobile;

import io.testfly.config.TestFlyConfig;
import io.testfly.mobile.Mobile;
import io.testfly.mobile.MobileDevice;
import io.testfly.mobile.MobileLocatorException;
import io.testfly.mobile.MobileLocators;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static io.testfly.mobile.MobileAssertions.expect;
import static org.testng.Assert.*;

/** {@link MobileLocators}: YAML locator files with ios/android/mobile blocks on the fake app. */
public class MobileLocatorsTest {

    private static final String YAML = String.join("\n",
            "login:",
            "  field:                         # different element per platform",
            "    ios: label=Password",
            "    android: dev.testfly.demo:id/username",
            "  signIn: role=button;name=Sign in",
            "  welcome:",
            "    mobile: text=Welcome back",
            "  iosOnly:",
            "    ios: text=Welcome back",
            "");

    private static MobileLocators locators(String yaml) {
        return MobileLocators.load(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)), "test.yml");
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
        return Mobile.connect(m);
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
    public void resolvesTheSelectorOfTheRunningPlatform() {
        MobileLocators locators = locators(YAML);
        assertEquals(locators.keys(), Set.of("login.field", "login.signIn", "login.welcome", "login.iosOnly"));
        for (String platform : new String[] {"ios", "android"}) {
            try (FakeMobilecliServer server = new FakeMobilecliServer(); MobileDevice device = connect(server, platform)) {
                locators.get("login.field", device.screen()).fill("ada");
                if (platform.equals("ios")) {
                    expect(device.screen().getByLabel("Password")).toHaveText("ada");
                    expect(locators.get("login.iosOnly", device.screen())).toBeVisible();
                } else {
                    expect(device.screen().getByPlaceholder("Username")).toHaveText("ada");
                    MobileLocatorException e = expectThrows(MobileLocatorException.class,
                            () -> locators.get("login.iosOnly", device.screen()).count());
                    assertTrue(e.getMessage().contains("login.iosOnly: no android selector in test.yml"), e.getMessage());
                }
                expect(locators.get("login.welcome", device.screen())).toBeVisible();
                expect(locators.get("login.signIn", device.screen())).toHaveCount(1);
            }
        }
    }

    @Test
    public void reportsBadFilesAndUnknownKeys() throws Exception {
        assertTrue(expectThrows(MobileLocatorException.class, () -> locators("a:\n  ios: colour=red\n"))
                .getMessage().contains("'a' in 'test.yml': Invalid mobile selector"));
        assertTrue(expectThrows(MobileLocatorException.class, () -> locators("a: 5\n"))
                .getMessage().contains("must be a selector string or a map"));
        assertTrue(expectThrows(MobileLocatorException.class, () -> locators("a: [x]\n"))
                .getMessage().contains("must be a selector string or a map"));
        assertTrue(expectThrows(MobileLocatorException.class, () -> locators("a: x\na: y\n"))
                .getMessage().contains("Invalid YAML"));
        assertTrue(expectThrows(MobileLocatorException.class, () -> MobileLocators.load("no/such/file.yml"))
                .getMessage().contains("not found"));
        try (FakeMobilecliServer server = new FakeMobilecliServer(); MobileDevice device = connect(server, "android")) {
            assertTrue(expectThrows(MobileLocatorException.class, () -> locators(YAML).get("login.nope", device.screen()))
                    .getMessage().contains("No mobile locator 'login.nope'"));
        }

        Path file = Files.createTempFile("mobile-locators-", ".yml");
        Files.writeString(file, YAML);
        assertTrue(MobileLocators.load(file.toString()).keys().contains("login.signIn"), "loads from the file system");
        Files.delete(file);
    }
}
