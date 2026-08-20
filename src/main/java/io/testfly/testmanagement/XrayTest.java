package io.testfly.testmanagement;

import io.testfly.api.TestFlyApi;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Links a test method or class to one or more Xray test issue keys.
 *
 * <pre>{@code
 * @Test
 * @XrayTest("PROJ-123")
 * public void loginTest() { ... }
 *
 * // Multiple test keys
 * @Test
 * @XrayTest({"PROJ-123", "PROJ-456"})
 * public void checkoutTest() { ... }
 * }</pre>
 */
@TestFlyApi(since = "3.0.0")
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface XrayTest {
    /** One or more Xray test issue keys (e.g. "PROJ-123"). */
    String[] value();
}
