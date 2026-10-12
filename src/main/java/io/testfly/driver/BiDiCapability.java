package io.testfly.driver;

import io.testfly.config.TestFlyConfig;
import org.openqa.selenium.MutableCapabilities;

/** Asks the browser for a WebDriver BiDi connection when {@code browser.bidi} is on. */
final class BiDiCapability {

    private BiDiCapability() {
    }

    static void applyIfConfigured(MutableCapabilities options, TestFlyConfig config) {
        if (config != null && config.getBrowser() != null && config.getBrowser().isBidi()) {
            options.setCapability("webSocketUrl", true);
        }
    }
}
