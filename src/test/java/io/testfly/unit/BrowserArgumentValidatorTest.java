package io.testfly.unit;

import io.testfly.driver.BrowserArgumentValidator;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.List;

import static org.testng.Assert.*;

/**
 * Unit tests for {@link BrowserArgumentValidator}.
 */
public class BrowserArgumentValidatorTest {

    @Test
    public void validate_nullArguments_isNoOp() {
        BrowserArgumentValidator.validate("chrome", null);
        // no exception expected
    }

    @Test
    public void validate_emptyArguments_isNoOp() {
        BrowserArgumentValidator.validate("chrome", Collections.emptyList());
        // no exception expected
    }

    @Test
    public void validate_chromeValidArguments_isNoOp() {
        BrowserArgumentValidator.validate("chrome", List.of("--start-maximized", "--disable-gpu"));
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void validate_chromeFirefoxPrivateFlag_throws() {
        BrowserArgumentValidator.validate("chrome", List.of("-private"));
    }

    @Test
    public void validate_firefoxValidArguments_isNoOp() {
        BrowserArgumentValidator.validate("firefox", List.of("-headless", "--width=1920"));
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void validate_firefoxRemoteAllowOrigins_throws() {
        BrowserArgumentValidator.validate("firefox", List.of("--remote-allow-origins=*"));
    }

    @Test
    public void validate_edgeValidArguments_isNoOp() {
        BrowserArgumentValidator.validate("edge", List.of("--start-maximized"));
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void validate_edgePrivateFlag_throws() {
        BrowserArgumentValidator.validate("edge", List.of("-private"));
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void validate_safariAnyArguments_throws() {
        BrowserArgumentValidator.validate("safari", List.of("--start-maximized"));
    }

    @Test
    public void validate_unknownBrowser_isNoOp() {
        // Unknown browsers pass through — validation is browser-specific.
        BrowserArgumentValidator.validate("opera", List.of("--some-flag"));
    }
}
