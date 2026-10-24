package io.testfly.mobile;

/** A locator matched nothing, or several elements where exactly one is required (strict mode). */
public class MobileLocatorException extends MobileException {

    public MobileLocatorException(String message) {
        super(message);
    }
}
