package io.testfly.mobile;

import io.testfly.mobile.internal.MobileSessions;

import java.lang.reflect.Field;
import java.util.Objects;

/**
 * Base class of fluent mobile page objects (incubating, ADR 0002).
 *
 * <pre>
 * public class HomePage extends MobilePage {
 *     &#64;MobileFind(ios = "HomeScreen.loginButton", android = "com.app:id/btnLogin")
 *     &#64;WaitFor
 *     private MobileLocator loginButton;
 *
 *     public LoginPage openLogin() {
 *         loginButton.tap();
 *         return new LoginPage();
 *     }
 * }
 *
 * new HomePage().openLogin().signIn(user, password);
 * </pre>
 *
 * The constructor sets the {@link MobileFind} fields for the running platform, waits for the
 * {@link WaitFor} fields to be visible, then calls {@link #onLoad()}. Two Java rules follow from
 * doing this in the constructor:
 * <ul>
 *   <li>Do not give {@code @MobileFind} fields an initial value: a subclass field initializer runs
 *       after this constructor and would overwrite the locator.</li>
 *   <li>{@link #onLoad()} runs before the subclass's own field initializers.</li>
 * </ul>
 */
public abstract class MobilePage implements MobileSupport {

    private final MobileScreen screen;

    /** A page on the current test's device. */
    protected MobilePage() {
        this(MobileSessions.current().screen());
    }

    /** A page on {@code screen}, e.g. outside a test runner or in unit tests. */
    protected MobilePage(MobileScreen screen) {
        this.screen = Objects.requireNonNull(screen, "screen");
        MobilePages.init(this, screen);
        for (Class<?> c = getClass(); c != MobilePage.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (field.isAnnotationPresent(WaitFor.class)) {
                    waitFor(field);
                }
            }
        }
        onLoad();
    }

    /** Called once the {@link WaitFor} fields are visible; e.g. close a banner that covers the page. */
    protected void onLoad() {
    }

    /** Taps {@code locator} and waits for it to disappear when it is shown now; returns whether it was. */
    protected boolean tapIfShown(MobileLocator locator) {
        if (!locator.isVisible()) {
            return false;
        }
        locator.tap();
        locator.waitForHidden();
        return true;
    }

    /** The screen this page was created on. */
    @Override
    public MobileScreen screen() {
        return screen;
    }

    private void waitFor(Field field) {
        String name = field.getDeclaringClass().getSimpleName() + "." + field.getName();
        if (!field.isAnnotationPresent(MobileFind.class)) {
            throw new MobileException("@WaitFor field " + name + " needs @MobileFind");
        }
        try {
            field.setAccessible(true);
            ((MobileLocator) field.get(this)).waitForVisible();
        } catch (IllegalAccessException e) {
            throw new MobileException("Cannot read @WaitFor field " + name + ": " + e, e);
        }
    }
}
