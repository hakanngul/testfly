package io.testfly.unit;

import io.testfly.driver.CapabilityValidator;
import org.testng.annotations.Test;

import java.util.Map;

/**
 * Unit tests for {@link CapabilityValidator}. Firefox/Chrome namespace clashes
 * fail fast as before; other cross-browser prefixes only warn so configs that
 * already run keep running.
 */
public class CapabilityValidatorTest {

    @Test
    public void validate_nullOrEmpty_isNoOp() {
        CapabilityValidator.validate("chrome", null);
        CapabilityValidator.validate("chrome", Map.of());
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void validate_firefoxWithChromeCapability_throws() {
        CapabilityValidator.validate("firefox", Map.of("goog:chromeOptions", Map.of()));
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void validate_chromeWithFirefoxCapability_throws() {
        CapabilityValidator.validate("chrome", Map.of("moz:firefoxOptions", Map.of()));
    }

    @Test
    public void validate_safariWithChromeCapability_warnsOnly() {
        CapabilityValidator.validate("safari", Map.of("goog:chromeOptions", Map.of()));
    }

    @Test
    public void validate_edgeWithFirefoxCapability_warnsOnly() {
        CapabilityValidator.validate("edge", Map.of("moz:firefoxOptions", Map.of()));
    }

    @Test
    public void validate_ownAndStandardCapabilities_pass() {
        CapabilityValidator.validate("safari", Map.of("safari:automaticInspection", false, "acceptInsecureCerts", true));
        CapabilityValidator.validate("edge", Map.of("ms:edgeOptions", Map.of()));
        CapabilityValidator.validate(null, Map.of("goog:chromeOptions", Map.of()));
    }
}
