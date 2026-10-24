package io.testfly.unit.mobile;

import io.testfly.config.ConfigurationLoader;
import io.testfly.config.TestFlyConfig;
import org.testng.annotations.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.*;

/** YAML binding and defaults of the {@code mobile:} block. */
public class MobileConfigYamlTest {

    private TestFlyConfig.Mobile parse(String yaml) {
        String full = "browser:\n  name: chrome\nexecution:\n  mode: local\n  baseUrl: http://localhost\n" + yaml;
        Yaml y = new Yaml(new Constructor(TestFlyConfig.class, new LoaderOptions()));
        TestFlyConfig config = y.load(new StringReader(full));
        return config.getMobile();
    }

    /** Through ConfigurationLoader, so its validation runs; testfly.config is global, hence the lock. */
    private static TestFlyConfig load(String yaml) throws Exception {
        Path file = Files.createTempFile("testfly-mobile-", ".yml");
        Files.writeString(file, yaml);
        synchronized (ConfigurationLoader.class) {
            System.setProperty("testfly.config", file.toString());
            try {
                return ConfigurationLoader.load();
            } finally {
                System.clearProperty("testfly.config");
                Files.deleteIfExists(file);
            }
        }
    }

    @Test
    public void mobileOnlyConfigNeedsNoWebBlocks() throws Exception {
        TestFlyConfig config = load("mobile:\n  platform: ios\n");
        assertEquals(config.getMobile().getPlatform(), "ios");
        assertEquals(config.getBrowser().getName(), "chrome");
        assertTrue(config.getBrowser().isHeadless());
        assertEquals(config.getExecution().getMode(), "local");
        assertEquals(config.getExecution().getBaseUrl(), "http://localhost");
        assertEquals(config.getTimeouts().getExplicit(), 10);
        assertEquals(config.getTimeouts().getPageLoad(), 30);
    }

    @Test
    public void mobileConfigKeepsTheWebValuesItSets() throws Exception {
        TestFlyConfig config = load("browser:\n  name: firefox\nexecution:\n  mode: local\n  baseUrl: https://app.example\n"
                + "timeouts:\n  explicit: 5\nmobile:\n  platform: android\n");
        assertEquals(config.getBrowser().getName(), "firefox");
        assertEquals(config.getExecution().getBaseUrl(), "https://app.example");
        assertEquals(config.getTimeouts().getExplicit(), 5);
        assertEquals(config.getTimeouts().getPageLoad(), 30);
    }

    @Test
    public void webOnlyConfigStillNeedsItsBlocks() throws Exception {
        IllegalStateException e = expectThrows(IllegalStateException.class, () -> load("retry:\n  enabled: true\n"));
        assertTrue(e.getMessage().contains("Browser configuration must be specified"), e.getMessage());
    }

    @Test
    public void absentSectionLoadsNothing() {
        assertNull(parse(""));
    }

    @Test
    public void defaultsLiveInTheSection() {
        TestFlyConfig.Mobile m = parse("mobile:\n  platform: android\n");
        assertEquals(m.getServerUrl(), "http://localhost:12000");
        assertEquals(m.getPlatform(), "android");
        assertEquals(m.getDeviceId(), "");
        assertEquals(m.getDeviceName(), "");
        assertEquals(m.getApp().getBundleId(), "");
        assertEquals(m.getApp().getReset(), "relaunch");
        assertTrue(m.getDevices().isEmpty());
        assertFalse(m.isPool());
        assertEquals(m.getTimeouts().getActionMs(), 5000);
        assertEquals(m.getTimeouts().getPollMs(), 100);
        assertEquals(m.getTimeouts().getRpcMs(), 30000);
        assertEquals(m.getTimeouts().getReserveMs(), 300000);
        assertFalse(m.isAllowRemote());
        assertTrue(m.getHeaders().isEmpty());
        assertTrue(m.isScreenshotOnFailure());
    }

    @Test
    public void platformBlocksLayOverTheSharedBlock() {
        TestFlyConfig.Mobile m = parse("mobile:\n"
                + "  platform: ios\n"
                + "  deviceId: shared\n"
                + "  alerts: dismiss\n"
                + "  app:\n"
                + "    bundleId: com.shared\n"
                + "    reset: clearData\n"
                + "  ios:\n"
                + "    deviceId: sim-udid\n"
                + "    app:\n"
                + "      bundleId: com.example.ios\n"
                + "  android:\n"
                + "    deviceName: Pixel.*\n"
                + "    app: { bundleId: com.example.android, reset: relaunch }\n");

        TestFlyConfig.Mobile ios = m.forPlatform(null);
        assertEquals(ios.getPlatform(), "ios", "no platform asked: mobile.platform");
        assertEquals(ios.getDeviceId(), "sim-udid");
        assertEquals(ios.getApp().getBundleId(), "com.example.ios");
        assertEquals(ios.getApp().getReset(), "clearData", "left out of the ios block: shared value");
        assertEquals(ios.getAlerts(), "dismiss");

        TestFlyConfig.Mobile android = m.forPlatform("android");
        assertEquals(android.getPlatform(), "android");
        assertEquals(android.getDeviceId(), "shared");
        assertEquals(android.getDeviceName(), "Pixel.*");
        assertEquals(android.getApp().getBundleId(), "com.example.android");
        assertEquals(android.getApp().getReset(), "relaunch");

        assertEquals(m.getApp().getBundleId(), "com.shared", "forPlatform copies; the original is unchanged");
        assertEquals(parse("mobile:\n  platform: android\n").forPlatform("ios").getPlatform(), "ios", "works without blocks");
    }

    @Test
    public void mobileTestTakesOnePlatform() {
        @io.testfly.mobile.MobileTest(platform = {io.testfly.mobile.MobilePlatform.IOS, io.testfly.mobile.MobilePlatform.ANDROID})
        class Both {
        }
        assertThrows(io.testfly.mobile.MobileException.class,
                () -> io.testfly.mobile.internal.MobileTestDetector.platform(Both.class, null));
        assertNull(io.testfly.mobile.internal.MobileTestDetector.platform(Object.class, null));
    }

    @Test
    public void fullBlockBinds() {
        TestFlyConfig.Mobile m = parse("mobile:\n"
                + "  serverUrl: https://lab.example.com\n"
                + "  platform: ios\n"
                + "  deviceName: iPhone 1[56]\n"
                + "  devices: [udid-1, udid-2]\n"
                + "  pool: true\n"
                + "  app:\n"
                + "    bundleId: com.example.app\n"
                + "    install: build/App.app\n"
                + "    reset: clearData\n"
                + "  timeouts:\n"
                + "    actionMs: 8000\n"
                + "    pollMs: 250\n"
                + "    rpcMs: 20000\n"
                + "    reserveMs: 0\n"
                + "  allowRemote: true\n"
                + "  headers:\n"
                + "    Authorization: Bearer token\n"
                + "  screenshotOnFailure: false\n");
        assertEquals(m.getServerUrl(), "https://lab.example.com");
        assertEquals(m.getPlatform(), "ios");
        assertEquals(m.getDeviceName(), "iPhone 1[56]");
        assertEquals(m.getDevices(), List.of("udid-1", "udid-2"));
        assertTrue(m.isPool());
        assertEquals(m.getApp().getBundleId(), "com.example.app");
        assertEquals(m.getApp().getInstall(), "build/App.app");
        assertEquals(m.getApp().getReset(), "clearData");
        assertEquals(m.getTimeouts().getActionMs(), 8000);
        assertEquals(m.getTimeouts().getPollMs(), 250);
        assertEquals(m.getTimeouts().getRpcMs(), 20000);
        assertEquals(m.getTimeouts().getReserveMs(), 0);
        assertTrue(m.isAllowRemote());
        assertEquals(m.getHeaders(), Map.of("Authorization", "Bearer token"));
        assertFalse(m.isScreenshotOnFailure());
    }

    @Test
    public void blankValuesFallBackToDefaults() {
        TestFlyConfig.Mobile m = parse("mobile:\n  serverUrl: \"\"\n  deviceId:\n  app:\n    reset: \"\"\n");
        assertEquals(m.getServerUrl(), "http://localhost:12000");
        assertEquals(m.getDeviceId(), "");
        assertEquals(m.getApp().getReset(), "relaunch");
    }
}
