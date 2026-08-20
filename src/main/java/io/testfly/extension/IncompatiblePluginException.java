package io.testfly.extension;

import io.testfly.api.TestFlyApi;

/**
 * Thrown when a plugin's minimum framework version requirement is not met.
 *
 * @see FrameworkVersion#requireAtLeast(String)
 */
@TestFlyApi(since = "0.7.0")
public class IncompatiblePluginException extends RuntimeException {

    public IncompatiblePluginException(String message) {
        super(message);
    }
}
