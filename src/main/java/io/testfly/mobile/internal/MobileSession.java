package io.testfly.mobile.internal;

import io.testfly.mobile.MobileDeviceInfo;
import io.testfly.mobile.MobileNode;
import io.testfly.mobile.MobilePlatform;
import io.testfly.mobile.spi.MobileDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * Everything one open device needs at runtime: the driver bound to the device, the platform and
 * the time budgets. Owned by one {@code MobileDevice} and used from one thread at a time.
 */
public final class MobileSession {

    private static final Logger LOGGER = LoggerFactory.getLogger(MobileSession.class);
    /** Alerts handled per tree read; more means the tap does not close them. */
    private static final int MAX_ALERTS = 3;

    private final MobileDriver driver;
    private final MobileDeviceInfo device;
    private final MobilePlatform platform;
    private final Duration actionTimeout;
    private final Duration pollInterval;
    private final Duration rpcTimeout;
    private final String alerts;

    public MobileSession(MobileDriver driver, MobileDeviceInfo device, MobilePlatform platform,
                         Duration actionTimeout, Duration pollInterval, Duration rpcTimeout) {
        this(driver, device, platform, actionTimeout, pollInterval, rpcTimeout, "none");
    }

    /** {@code alerts}: {@code none}, {@code accept} or {@code dismiss} ({@code mobile.alerts}). */
    public MobileSession(MobileDriver driver, MobileDeviceInfo device, MobilePlatform platform,
                         Duration actionTimeout, Duration pollInterval, Duration rpcTimeout, String alerts) {
        this.alerts = alerts;
        this.driver = driver;
        this.device = device;
        this.platform = platform;
        this.actionTimeout = actionTimeout;
        this.pollInterval = pollInterval;
        this.rpcTimeout = rpcTimeout;
    }

    public MobileDriver driver() {
        return driver;
    }

    public MobileDeviceInfo device() {
        return device;
    }

    public MobilePlatform platform() {
        return platform;
    }

    /** Default auto-wait budget of an action or assertion ({@code mobile.timeouts.actionMs}). */
    public Duration actionTimeout() {
        return actionTimeout;
    }

    public Duration pollInterval() {
        return pollInterval;
    }

    /** Upper bound of one request ({@code mobile.timeouts.rpcMs}). */
    public Duration rpcTimeout() {
        return rpcTimeout;
    }

    /**
     * The current view tree. With {@code mobile.alerts} set, a system alert covering the app (an
     * {@code Alert} element) is accepted or dismissed first and the tree read again.
     */
    public List<MobileNode> viewTree(Duration timeout) {
        List<MobileNode> tree = driver.viewTree(timeout);
        for (int i = 0; i < MAX_ALERTS && !alerts.equals("none"); i++) {
            MobileNode alert = LocatorEngine.flatten(tree).stream()
                    .filter(n -> LocatorEngine.bareTypeName(n.type()).equals("alert")).findFirst().orElse(null);
            MobileNode button = alert != null ? alertButton(alert, alerts.equals("accept"))
                    : androidPermissionButton(tree, alerts.equals("accept"));
            if (button == null) {
                break;
            }
            LOGGER.info("[TestFly] mobile.alerts={}: tapping \"{}\" on {}", alerts, button.displayText(),
                    alert != null ? "alert \"" + alert.displayText() + "\"" : "the Android permission dialog");
            driver.tap((int) Math.round(button.bounds().centerX()), (int) Math.round(button.bounds().centerY()), timeout);
            tree = driver.viewTree(timeout);
        }
        return tree;
    }

    // shortcut: English button labels only (Allow/OK, Don't Allow/Not/Cancel); other languages fall back
    // to the last (accept) or first (dismiss) button, upgrade with localized labels when a project needs them.
    static MobileNode alertButton(MobileNode alert, boolean accept) {
        List<MobileNode> buttons = LocatorEngine.flatten(alert.children()).stream()
                .filter(n -> LocatorEngine.bareTypeName(n.type()).equals("button")).toList();
        if (buttons.isEmpty()) {
            return null;
        }
        for (MobileNode b : buttons) {
            String label = b.displayText().toLowerCase(Locale.ROOT).replace('\u2019', '\'');
            boolean negative = label.contains("don't") || label.contains("not ") || label.startsWith("cancel");
            if (accept ? !negative && (label.startsWith("allow") || label.equals("ok")) : negative) {
                return b;
            }
        }
        return accept ? buttons.get(buttons.size() - 1) : buttons.get(0);
    }

    // Android's permission dialog (permissioncontroller), by resource-id so it works in every device language.
    private static final List<String> PERMISSION_ALLOW = List.of("permission_allow_foreground_only_button",
            "permission_allow_button", "permission_allow_one_time_button");
    private static final List<String> PERMISSION_DENY = List.of("permission_deny_button",
            "permission_deny_and_dont_ask_again_button");

    static MobileNode androidPermissionButton(List<MobileNode> tree, boolean accept) {
        List<MobileNode> buttons = LocatorEngine.flatten(tree).stream()
                .filter(n -> n.identifier() != null && n.identifier().startsWith("com.android.permissioncontroller:id/"))
                .toList();
        for (String wanted : accept ? PERMISSION_ALLOW : PERMISSION_DENY) {
            for (MobileNode b : buttons) {
                if (b.identifier().endsWith(":id/" + wanted)) {
                    return b;
                }
            }
        }
        return null;
    }

    /** {@code min(rpcMs, time left until deadline)}, never below 1 ms. */
    public Duration requestTimeout(long deadlineNanos) {
        long left = deadlineNanos - System.nanoTime();
        long bounded = Math.min(rpcTimeout.toNanos(), left);
        return Duration.ofNanos(Math.max(bounded, 1_000_000L));
    }
}
