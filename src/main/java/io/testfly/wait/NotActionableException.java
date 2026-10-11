package io.testfly.wait;

import io.testfly.api.TestFlyApi;
import org.openqa.selenium.TimeoutException;

/**
 * Thrown when an element did not become actionable in time, for example because
 * it kept moving or stayed covered by an overlay. The message names the element
 * and the reason. Extends {@link TimeoutException} so existing handlers keep working.
 */
@TestFlyApi(since = "1.1.0")
public class NotActionableException extends TimeoutException {

    public NotActionableException(String message, Throwable cause) {
        super(message, cause);
    }
}
