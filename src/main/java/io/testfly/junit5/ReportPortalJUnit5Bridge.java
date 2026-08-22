package io.testfly.junit5;

import org.junit.jupiter.api.extension.ExtensionContext;

import java.lang.reflect.Method;

/**
 * Bridge between TestFly's {@link TestFlyExtension} and ReportPortal's
 * {@code ReportPortalExtension} (from {@code agent-java-junit5}).
 *
 * <p>Uses reflection to load the RP extension so TestFly compiles and runs
 * even when {@code agent-java-junit5} is not on the consumer's classpath.
 *
 * <p>Lifecycle delegation order:
 * <ol>
 *   <li>{@code beforeAll}  — RP creates the launch (reads rp.* system properties)</li>
 *   <li>{@code beforeEach} — RP starts a test item</li>
 *   <li>{@code afterTestExecution} — RP finishes the test item with status</li>
 *   <li>{@code afterAll}   — RP finishes the launch</li>
 * </ol>
 *
 * <p>The {@code testFailed} and {@code testSuccessful} callbacks from the
 * {@code TestWatcher} interface are also delegated so RP captures the correct
 * test outcome.
 */
final class ReportPortalJUnit5Bridge {

    private static final String RP_EXTENSION_CLASS =
            "com.epam.reportportal.junit5.ReportPortalExtension";

    private final Object rpExtension;

    ReportPortalJUnit5Bridge() {
        this.rpExtension = createRpExtension();
        if (rpExtension != null) {
            System.out.println("[TestFly] ReportPortal JUnit 5 agent loaded");
        }
    }

    boolean isAvailable() {
        return rpExtension != null;
    }

    // ── Lifecycle delegation ──────────────────────────────────────────────────

    void beforeAll(ExtensionContext context) {
        invoke("beforeAll", context);
    }

    void beforeEach(ExtensionContext context) {
        invoke("beforeEach", context);
    }

    void afterTestExecution(ExtensionContext context) {
        invoke("afterTestExecution", context);
    }

    void testFailed(ExtensionContext context, Throwable cause) {
        invoke("testFailed", context, cause);
    }

    void testSuccessful(ExtensionContext context) {
        invoke("testSuccessful", context);
    }

    void testDisabled(ExtensionContext context, java.util.Optional<String> reason) {
        invoke("testDisabled", context, reason);
    }

    void afterAll(ExtensionContext context) {
        invoke("afterAll", context);
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private static Object createRpExtension() {
        try {
            Class<?> clazz = Class.forName(RP_EXTENSION_CLASS);
            return clazz.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException e) {
            // agent-java-junit5 not on classpath — silent skip
            return null;
        } catch (Exception e) {
            System.err.println("[TestFly] Failed to load ReportPortal JUnit 5 extension: "
                    + e.getMessage());
            return null;
        }
    }

    private void invoke(String methodName, Object... args) {
        if (rpExtension == null) return;
        try {
            Class<?>[] paramTypes = new Class<?>[args.length];
            for (int i = 0; i < args.length; i++) {
                paramTypes[i] = args[i] != null ? args[i].getClass() : Object.class;
            }
            // Fix: ExtensionContext is an interface, use the interface class
            for (int i = 0; i < paramTypes.length; i++) {
                if (ExtensionContext.class.isAssignableFrom(paramTypes[i])) {
                    paramTypes[i] = ExtensionContext.class;
                }
            }
            Method method = rpExtension.getClass().getMethod(methodName, paramTypes);
            method.invoke(rpExtension, args);
        } catch (NoSuchMethodException e) {
            // Method not found in this RP agent version — skip silently
        } catch (Exception e) {
            System.err.println("[TestFly] ReportPortal '" + methodName + "' failed: "
                    + e.getMessage());
        }
    }
}
