package io.testfly.unit.mobile;

import io.testfly.config.TestFlyConfig;
import org.testng.annotations.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.StringReader;
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
