package io.testfly.driver;

import java.util.List;

public class BrowserArgumentValidator {
    private  BrowserArgumentValidator() {
    }

    public static void validate(String browser, List<String> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return;
        }

        if ("firefox".equalsIgnoreCase(browser)) {
            validateFirefox(arguments);
        }

        if ("chrome".equalsIgnoreCase(browser) || "edge".equalsIgnoreCase(browser)) {
            validateChromium(arguments);
        }

        if ("safari".equalsIgnoreCase(browser)) {
            validateSafari(arguments);
        }
    }

    private static void validateFirefox( List<String> arguments) {
        for (String argument : arguments) {
            if ((argument.startsWith("--remote-allow-origins"))) {
                throw new IllegalStateException(
                        "Invalid argument'" + argument + "' for Firefox."
                );
            }
        }
    }

    private static void validateChromium(List<String> arguments) {
        for (String argument : arguments) {
            if ((argument.startsWith("-private"))) {
                throw new IllegalStateException(
                        "Invalid argument'" + argument + "' for Chromium-based browser."
                );
            }
        }
    }

    private static void validateSafari(List<String> arguments) {
        // Safari does not expose command-line arguments through Selenium.
        // Rejecting them here prevents silent misconfiguration.
        throw new IllegalStateException(
                "Safari does not support browser.arguments via Selenium. " +
                "Remove the arguments list from configuration or use capabilities instead."
        );
    }

}
