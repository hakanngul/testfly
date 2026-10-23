package io.testfly.mobile;

import io.testfly.mobile.internal.LocatorEngine;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Objects;

/**
 * Fills the {@link MobileFind} fields of page objects (incubating, ADR 0002). Locators are lazy, so
 * a page can be created once per test and its fields used for every screen state.
 */
public final class MobilePages {

    private MobilePages() {
    }

    /** Creates {@code type} with its no-argument constructor and fills its fields for {@code screen}. */
    public static <T> T create(Class<T> type, MobileScreen screen) {
        Objects.requireNonNull(type, "type");
        T page;
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            page = constructor.newInstance();
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new MobileException("Cannot create page " + type.getName() + "; it needs a no-argument constructor: " + e, e);
        }
        return init(page, screen);
    }

    /**
     * Sets every {@link MobileFind} field of {@code page} (superclasses included) to a locator for
     * the platform of {@code screen}. A field without a selector for that platform still gets a
     * locator, which fails when used.
     */
    public static <T> T init(T page, MobileScreen screen) {
        Objects.requireNonNull(page, "page");
        Objects.requireNonNull(screen, "screen");
        MobilePlatform platform = screen.platform();
        for (Class<?> c = page.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                MobileFind find = field.getAnnotation(MobileFind.class);
                if (find == null) {
                    continue;
                }
                String name = c.getSimpleName() + "." + field.getName();
                if (field.getType() != MobileLocator.class || Modifier.isStatic(field.getModifiers())) {
                    throw new MobileException("@MobileFind field " + name + " must be a non-static MobileLocator");
                }
                String selector = platform == MobilePlatform.IOS ? find.ios() : find.android();
                if (selector.isEmpty()) {
                    selector = find.value();
                }
                MobileLocator locator = selector.isEmpty()
                        ? screen.locator(LocatorEngine.missing(name + ": no " + platform.wireName() + " selector in @MobileFind"))
                        : screen.locate(selector);
                try {
                    field.setAccessible(true);
                    field.set(page, locator);
                } catch (ReflectiveOperationException | RuntimeException e) {
                    throw new MobileException("Cannot set @MobileFind field " + name + ": " + e, e);
                }
            }
        }
        return page;
    }
}
