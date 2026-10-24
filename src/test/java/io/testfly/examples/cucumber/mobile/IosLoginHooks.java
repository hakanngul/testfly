package io.testfly.examples.cucumber.mobile;

import io.cucumber.java.Before;

/**
 * Selects the {@code mobile-login} TestFly profile when none was given, so the scenarios also run
 * from tools that cannot pass {@code -Dtestfly.profile} (for example the Cucumber Java Runner
 * extension). An explicit {@code -Dtestfly.profile=...} always wins.
 *
 * <p>Runs before {@code io.testfly.cucumber.CucumberHooks} (order 1000), which loads the config and
 * opens the device.
 */
public class IosLoginHooks {

    private static final String PROFILE = "mobile-login";

    @Before(order = 1)
    public void selectProfile() {
        String current = System.getProperty("testfly.profile");
        if (current == null || current.isBlank()) {
            System.setProperty("testfly.profile", PROFILE);
        }
    }
}
