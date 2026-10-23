package io.testfly.mobile;

import io.testfly.config.ConfigurationLoader;
import io.testfly.config.TestFlyConfig;
import io.testfly.exceptions.ConfigurationException;
import io.testfly.internal.TestFlyContext;
import io.testfly.mobile.internal.DeviceReservations;
import io.testfly.mobile.internal.DeviceReservations.Reservation;
import io.testfly.mobile.internal.DeviceSelector;
import io.testfly.mobile.internal.JsonRpcClient;
import io.testfly.mobile.internal.MobileCliDriver;
import io.testfly.mobile.internal.MobileServerLauncher;
import io.testfly.mobile.internal.MobileServerUrl;
import io.testfly.mobile.internal.MobileSession;
import io.testfly.mobile.internal.ServerVersion;
import io.testfly.mobile.spi.MobileDriver;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Opens native mobile sessions (incubating, ADR 0002). TestFly talks to a user-installed, unmodified
 * mobilecli server ({@code mobilecli server start}); it does not ship mobilecli.
 *
 * <pre>{@code
 * try (MobileDevice device = Mobile.connect()) {
 *     MobileScreen screen = device.screen();
 *     screen.getByTestId("username").fill("ada");
 *     screen.getByRole(MobileRole.BUTTON, "Sign in").tap();
 *     MobileAssertions.expect(screen.getByText("Welcome, ada")).toBeVisible();
 * }
 * }</pre>
 */
public final class Mobile {

    private static final Set<String> RESETS = Set.of("none", "relaunch", "cleardata");

    private Mobile() {
    }

    /**
     * Opens the device described by the {@code mobile:} section of {@code testfly.yml}: the
     * framework's configuration when TestFly is running, else {@code testfly.yml} loaded directly.
     */
    public static MobileDevice connect() {
        TestFlyConfig config = TestFlyContext.isInitialized() ? TestFlyContext.getConfig() : ConfigurationLoader.load();
        TestFlyConfig.Mobile mobile = config != null ? config.getMobile() : null;
        if (mobile == null) {
            throw new ConfigurationException("Native mobile needs a 'mobile:' section in testfly.yml "
                    + "(at least mobile.platform: android|ios). See ADR 0002.");
        }
        return connect(mobile);
    }

    /** Opens the device described by {@code mobile}, through mobilecli at {@code mobile.serverUrl}. */
    public static MobileDevice connect(TestFlyConfig.Mobile mobile) {
        validate(mobile);
        URI endpoint = MobileServerUrl.rpcEndpoint(mobile.getServerUrl(), mobile.isAllowRemote());
        MobileCliDriver driver = new MobileCliDriver(new JsonRpcClient(endpoint, mobile.getHeaders()));
        Duration rpcTimeout = Duration.ofMillis(mobile.getTimeouts().getRpcMs());
        ServerVersion.require(MobileServerLauncher.serverVersion(mobile, endpoint, driver, rpcTimeout));
        return connect(driver, mobile);
    }

    /**
     * Opens a device through a custom {@link MobileDriver} (another automation backend, a fake). The
     * driver is closed with the device.
     */
    public static MobileDevice connect(MobileDriver driver, TestFlyConfig.Mobile mobile) {
        MobilePlatform platform = validate(mobile);
        TestFlyConfig.Mobile.Timeouts t = mobile.getTimeouts();
        Duration rpcTimeout = Duration.ofMillis(t.getRpcMs());
        Reservation reservation = null;
        try {
            List<MobileDeviceInfo> candidates = DeviceSelector.candidates(driver.devices(rpcTimeout), platform,
                    mobile.getDeviceId(), mobile.getDeviceName(), mobile.getDevices(), mobile.isPool());
            reservation = DeviceReservations.acquireAny(candidates.stream().map(MobileDeviceInfo::id).toList(),
                    Duration.ofMillis(t.getReserveMs()));
            String reservedId = reservation.deviceId();
            MobileDeviceInfo device = candidates.stream().filter(d -> d.id().equals(reservedId)).findFirst().orElseThrow();
            driver.useDevice(device);
            MobileSession session = new MobileSession(driver, device, platform, Duration.ofMillis(t.getActionMs()),
                    Duration.ofMillis(t.getPollMs()), rpcTimeout);
            String reset = mobile.getApp().getReset().toLowerCase(Locale.ROOT);
            prepareApp(session, mobile.getApp(), reset);
            return new MobileDevice(session, reservation, mobile.getApp().getBundleId(), !reset.equals("none"));
        } catch (RuntimeException e) {
            if (reservation != null) {
                reservation.close();
            }
            try {
                driver.close();
            } catch (Exception closeFailure) {
                e.addSuppressed(closeFailure);
            }
            throw e;
        }
    }

    private static void prepareApp(MobileSession session, TestFlyConfig.Mobile.App app, String reset) {
        Duration rpc = session.rpcTimeout();
        if (!app.getInstall().isBlank()) {
            session.driver().installApp(app.getInstall(), rpc);
        }
        String bundleId = app.getBundleId();
        if (bundleId.isBlank() || reset.equals("none")) {
            return;
        }
        if (reset.equals("cleardata")) {
            session.driver().clearAppData(bundleId, rpc);
        } else {
            try {
                session.driver().terminateApp(bundleId, rpc);
            } catch (MobileRpcException e) {
                // Physical iOS devices report a not-running app as an error; anything else is real.
                if (e.getMessage() == null || !e.getMessage().contains("not found")) {
                    throw e;
                }
            }
        }
        session.driver().launchApp(bundleId, rpc);
    }

    /** Fails fast on a missing platform or invalid values (ADR 0002 D5). */
    static MobilePlatform validate(TestFlyConfig.Mobile mobile) {
        if (mobile == null) {
            throw new ConfigurationException("mobile settings are missing");
        }
        MobilePlatform platform = MobilePlatform.fromWire(mobile.getPlatform());
        if (platform == null) {
            throw new ConfigurationException("mobile.platform must be 'android' or 'ios', got: "
                    + (mobile.getPlatform() == null || mobile.getPlatform().isBlank() ? "(missing)" : mobile.getPlatform()));
        }
        if (!RESETS.contains(mobile.getApp().getReset().toLowerCase(Locale.ROOT))) {
            throw new ConfigurationException("mobile.app.reset must be none, relaunch or clearData, got: " + mobile.getApp().getReset());
        }
        if (!mobile.getDevices().isEmpty()) {
            if (mobile.getDevices().stream().anyMatch(id -> id == null || id.isBlank())) {
                throw new ConfigurationException("mobile.devices must not contain blank ids: " + mobile.getDevices());
            }
            if (!mobile.getDeviceId().isBlank() || mobile.isPool()) {
                throw new ConfigurationException("mobile.devices cannot be combined with mobile.deviceId or mobile.pool; "
                        + "list every device of the pool in mobile.devices");
            }
        }
        if (mobile.isPool() && !mobile.getDeviceId().isBlank()) {
            throw new ConfigurationException("mobile.pool selects devices by mobile.deviceName; remove mobile.deviceId");
        }
        TestFlyConfig.Mobile.Timeouts t = mobile.getTimeouts();
        positive("mobile.timeouts.actionMs", t.getActionMs());
        positive("mobile.timeouts.pollMs", t.getPollMs());
        positive("mobile.timeouts.rpcMs", t.getRpcMs());
        positive("mobile.timeouts.serverStartMs", t.getServerStartMs());
        if (t.getReserveMs() < 0) {
            throw new ConfigurationException("mobile.timeouts.reserveMs must be >= 0, got: " + t.getReserveMs());
        }
        return platform;
    }

    private static void positive(String key, long value) {
        if (value <= 0) {
            throw new ConfigurationException(key + " must be > 0, got: " + value);
        }
    }
}
