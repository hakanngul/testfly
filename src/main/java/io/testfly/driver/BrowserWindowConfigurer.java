package io.testfly.driver;

import io.testfly.config.TestFlyConfig;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebDriver;

import java.util.Locale;

/**
 * Validates and applies {@code browser.window} to a freshly created session.
 *
 * <p>Sizing uses the W3C WebDriver window commands after the session starts,
 * so it behaves the same for every browser (Chrome, Edge, Firefox, Safari) and
 * every execution mode (local, Grid, cloud), unlike browser-specific
 * command-line arguments.
 */
public final class BrowserWindowConfigurer {

    /** Window used for maximized/fullscreen in headless sessions, which have no screen. */
    static final int HEADLESS_WIDTH = 1920;
    static final int HEADLESS_HEIGHT = 1080;

    private BrowserWindowConfigurer() {
    }

    /**
     * Rejects inconsistent {@code browser.window} settings.
     *
     * @throws IllegalStateException when the mode is unknown, when width/height
     *                               are given for a mode that ignores them, or when
     *                               {@code size} lacks a positive width and height
     */
    public static void validate(TestFlyConfig.Window window) {
        if (window == null) {
            return;
        }
        String mode = normalizedMode(window);
        switch (mode) {
            case "default", "maximized", "fullscreen" -> {
                if (window.getWidth() != null || window.getHeight() != null) {
                    throw new IllegalStateException(
                            "browser.window.width/height are only used with mode: size, but mode is '" + mode
                                    + "'. Remove width/height, or set mode: size.");
                }
            }
            case "size" -> {
                if (window.getWidth() == null || window.getHeight() == null) {
                    throw new IllegalStateException(
                            "browser.window mode: size requires both width and height.");
                }
                if (window.getWidth() <= 0 || window.getHeight() <= 0) {
                    throw new IllegalStateException(
                            "browser.window width and height must be positive, got: "
                                    + window.getWidth() + "x" + window.getHeight());
                }
            }
            default -> throw new IllegalStateException(
                    "browser.window.mode must be 'default', 'maximized', 'fullscreen', or 'size', got: '"
                            + window.getMode() + "'");
        }
    }

    /**
     * Applies the configured window mode to {@code driver}. A browser that
     * rejects the window command only logs a warning; the test keeps running
     * with the browser's own window.
     */
    public static void apply(WebDriver driver, TestFlyConfig.Browser browser) {
        if (driver == null || browser == null || browser.getWindow() == null) {
            return;
        }
        TestFlyConfig.Window window = browser.getWindow();
        String mode = normalizedMode(window);
        try {
            switch (mode) {
                case "maximized", "fullscreen" -> {
                    if (browser.isHeadless()) {
                        System.out.println("[TestFly] Headless session has no screen; browser.window mode '" + mode
                                + "' uses " + HEADLESS_WIDTH + "x" + HEADLESS_HEIGHT + ".");
                        driver.manage().window().setSize(new Dimension(HEADLESS_WIDTH, HEADLESS_HEIGHT));
                    } else if ("maximized".equals(mode)) {
                        driver.manage().window().maximize();
                    } else {
                        driver.manage().window().fullscreen();
                    }
                }
                case "size" -> {
                    if (window.getWidth() != null && window.getHeight() != null) {
                        driver.manage().window().setSize(new Dimension(window.getWidth(), window.getHeight()));
                    }
                }
                default -> {
                    // "default": keep the browser's own window
                }
            }
        } catch (Exception e) {
            System.err.println("[TestFly] Could not apply browser.window mode '" + mode + "': " + e.getMessage());
        }
    }

    private static String normalizedMode(TestFlyConfig.Window window) {
        String mode = window.getMode();
        return mode == null || mode.isBlank() ? "default" : mode.trim().toLowerCase(Locale.ROOT);
    }
}
