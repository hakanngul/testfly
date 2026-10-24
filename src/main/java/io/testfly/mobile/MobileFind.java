package io.testfly.mobile;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a {@link MobileLocator} field of a page object for iOS and Android (incubating, ADR 0002).
 * {@link MobilePages#init} picks the selector of the running platform, else {@link #value()}.
 *
 * <pre>
 * &#64;MobileFind(ios = "LoginScreen.loginButton", android = "com.example:id/btnLogin")
 * MobileLocator loginButton;
 *
 * &#64;MobileFind("role=button;name=Sign in")   // same selector on both platforms
 * MobileLocator signIn;
 * </pre>
 *
 * Selectors: a bare string is a test id (iOS accessibility identifier, Android resource-id);
 * otherwise {@code testid=}/{@code id=}, {@code accessibility=}, {@code text=}, {@code label=},
 * {@code placeholder=}, {@code type=}, {@code role=<MobileRole>[;name=…]}, {@code xpath=},
 * {@code chain=} or {@code predicate=}. See {@link MobileScreen#locate(String)}.
 *
 * <p>Typed attributes, like Appium's {@code @iOSXCUITFindBy}/{@code @AndroidFindBy}, say the same
 * without a prefix; set at most one per platform:
 *
 * <pre>
 * &#64;MobileFind(iosAccessibility = "slotsButtonView", androidId = "com.example:id/cardViewSlots")
 * &#64;MobileFind(iosClassChain = "**&#47;XCUIElementTypeCell[`name == 'slots'`]", androidXpath = "//*[&#64;text='Slots']")
 * </pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface MobileFind {

    /** Selector for both platforms; used when the platform's own selector is empty. */
    String value() default "";

    /** iOS selector. */
    String ios() default "";

    /** Android selector. */
    String android() default "";

    /** iOS accessibility identifier / name (Appium {@code accessibility}). */
    String iosAccessibility() default "";

    /** iOS identifier (Appium {@code id}). */
    String iosId() default "";

    /** iOS XPath over the view tree. */
    String iosXpath() default "";

    /** iOS class chain (Appium {@code iOSClassChain}), common subset. */
    String iosClassChain() default "";

    /** iOS NSPredicate (Appium {@code iOSNsPredicate}), common subset. */
    String iosPredicate() default "";

    /** iOS element type, with or without the {@code XCUIElementType} prefix. */
    String iosClassName() default "";

    /** Android content-desc (Appium {@code accessibility}). */
    String androidAccessibility() default "";

    /** Android resource-id, full ({@code pkg:id/name}) or short (Appium {@code id}). */
    String androidId() default "";

    /** Android XPath over the view tree. */
    String androidXpath() default "";

    /** Android class, e.g. {@code android.widget.TextView} (Appium {@code className}). */
    String androidClassName() default "";
}
