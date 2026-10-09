package io.testfly.loadtest.internal;

import io.testfly.loadtest.BaseLoadTest;
import io.testfly.loadtest.LoadTest;
import io.testfly.loadtest.LoadTestRunner;
import io.testfly.test.NoBrowser;
import io.testfly.test.support.LoadTestSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single source of truth for "is this a load test / must it stay browser-free?".
 *
 * <p>
 * Only explicit signals are used:
 * <ul>
 * <li>the class extends {@link BaseLoadTest} or implements
 * {@link LoadTestSupport}</li>
 * <li>the class or method is annotated with {@link LoadTest}</li>
 * <li>the class or method is annotated with {@link NoBrowser} (browser-free
 * only, see {@link #skipsBrowser(Class, Method)})</li>
 * <li>a load test is currently executing on this thread
 * ({@link LoadTestRunner#isExecuting()})</li>
 * </ul>
 *
 * <p>
 * Class, package or tag <em>names</em> are never used. Earlier versions treated
 * any class whose name contained {@code "loadtest"} as a load test, which also
 * matched {@code FileUploadTest}, {@code DownloadTest} or the package
 * {@code com.acme.uploadtests} and silently denied them a WebDriver. To help
 * with migration, a one-time WARN is logged for every class that the old
 * heuristic would have matched.
 */
public final class LoadTestDetector {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoadTestDetector.class);
    private static final String NAME_MARKER = "loadtest";
    private static final String LOAD_TEST_TAG = "@loadtest";

    /** Classes / tags already checked for the removed name heuristic. */
    private static final Set<Object> CHECKED = ConcurrentHashMap.newKeySet();
    private static final Set<String> LEGACY_WARNINGS = ConcurrentHashMap.newKeySet();

    private LoadTestDetector() {
    }

    /**
     * True when the class/method carries an explicit load-test signal:
     * {@link BaseLoadTest}, {@link LoadTestSupport} or {@link LoadTest}.
     *
     * @param testClass  the test class (may be {@code null})
     * @param testMethod the test method (may be {@code null})
     */
    public static boolean isLoadTest(Class<?> testClass, Method testMethod) {
        boolean loadTest = (testClass != null && (BaseLoadTest.class.isAssignableFrom(testClass)
                || LoadTestSupport.class.isAssignableFrom(testClass)
                || testClass.isAnnotationPresent(LoadTest.class)))
                || (testMethod != null && testMethod.isAnnotationPresent(LoadTest.class));
        if (!loadTest && testClass != null) {
            warnIfLegacyNameMatch(testClass);
        }
        return loadTest;
    }

    /** True when the class or method is annotated with {@link NoBrowser}. */
    public static boolean isNoBrowser(Class<?> testClass, Method testMethod) {
        return (testMethod != null && testMethod.isAnnotationPresent(NoBrowser.class))
                || (testClass != null && testClass.isAnnotationPresent(NoBrowser.class));
    }

    /** True when the test must not create or use a WebDriver because of load-test or {@link NoBrowser}. */
    public static boolean skipsBrowser(Class<?> testClass, Method testMethod) {
        return isNoBrowser(testClass, testMethod) || isLoadTest(testClass, testMethod);
    }

    /**
     * True when browser sessions are forbidden right now: a load test is
     * executing on this thread, or the given test is a load test.
     */
    public static boolean isLoadTestActive(Class<?> testClass, Method testMethod) {
        return LoadTestRunner.isExecuting() || isLoadTest(testClass, testMethod);
    }

    /**
     * True for the explicit Cucumber tag {@code @loadtest} (case-insensitive).
     * Tags that merely <em>contain</em> "loadtest" are no longer matched; a
     * one-time WARN is logged for them.
     */
    public static boolean isLoadTestTag(String tag) {
        if (tag == null) {
            return false;
        }
        String lower = tag.toLowerCase(Locale.ROOT);
        if (lower.equals(LOAD_TEST_TAG)) {
            return true;
        }
        if (lower.contains(NAME_MARKER) && CHECKED.add(lower) && LEGACY_WARNINGS.add(tag)) {
            LOGGER.warn("[TestFly] Cucumber tag '{}' contains 'loadtest' and was previously treated as a "
                    + "load-test tag (no WebDriver). The name-based heuristic was removed: only the exact tag "
                    + "'@loadtest' or '@nobrowser' now skips the browser.", tag);
        }
        return false;
    }

    /**
     * Class names for which the "name-heuristic removed" warning has been logged
     * (diagnostics and tests).
     */
    public static Set<String> legacyNameWarnings() {
        return Collections.unmodifiableSet(LEGACY_WARNINGS);
    }

    private static void warnIfLegacyNameMatch(Class<?> testClass) {
        if (!CHECKED.add(testClass)) {
            return;
        }
        String className = testClass.getName();
        String pkg = testClass.getPackageName();
        if (className.toLowerCase(Locale.ROOT).contains(NAME_MARKER)
                || pkg.toLowerCase(Locale.ROOT).contains(NAME_MARKER)) {
            LEGACY_WARNINGS.add(className);
            LOGGER.warn("[TestFly] {} matches the removed name-based load-test heuristic ('loadtest' in the "
                    + "class or package name). It is no longer treated as a load test by name, so it gets a "
                    + "WebDriver unless it is an API test or marked @NoBrowser. If it is a load test, extend "
                    + "BaseLoadTest, implement LoadTestSupport, or annotate it with @LoadTest or @NoBrowser.",
                    className);
        }
    }
}
