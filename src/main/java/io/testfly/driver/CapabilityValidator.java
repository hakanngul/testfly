package io.testfly.driver;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CapabilityValidator {
    private CapabilityValidator(){}

    /** Vendor capability prefixes that belong to another browser and have no effect here. */
    private static final Map<String, List<String>> FOREIGN_PREFIXES = Map.of(
            "chrome", List.of("moz:", "safari:"),
            "edge", List.of("moz:", "safari:"),
            "firefox", List.of("goog:", "ms:", "safari:"),
            "safari", List.of("goog:", "moz:", "ms:"));

    public static void validate(String browser, Map<String,Object> capabilities){
        if (capabilities ==null || capabilities.isEmpty()) {
            return;
        }
        for (String key : capabilities.keySet()) {
            validateNameSpace(browser, key);
        }
    }

    private static void validateNameSpace(String browser, String key){
//        block chrome namespaces in firefox
        if ("firefox".equalsIgnoreCase(browser) && key.startsWith("goog:")) {
            throw new IllegalStateException(
                    "Capability '" + key + "' is not valid for Firefox"
            );
        }

//        block Firefox namespace in chrome
        if ("chrome".equalsIgnoreCase(browser) && key.startsWith("moz:")) {
            throw new IllegalStateException(
                    "Capability '" + key + "' is not valid for Chrome"
            );
        }

        // Other cross-browser prefixes only warn, so configs that already run keep running.
        if (browser == null) {
            return;
        }
        for (String prefix : FOREIGN_PREFIXES.getOrDefault(browser.toLowerCase(Locale.ROOT), List.of())) {
            if (key.startsWith(prefix)) {
                System.err.println("[TestFly] Capability '" + key + "' belongs to another browser and has no effect on "
                        + browser + ".");
                return;
            }
        }
    }
}
