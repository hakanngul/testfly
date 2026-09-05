package io.testfly.test.support;

import io.testfly.api.TestFlyApi;
import io.testfly.browser.ConsoleErrorCollector;
import io.testfly.driver.DriverManager;
import io.testfly.internal.TestFlyContext;
import io.testfly.steps.StepLogger;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

/**
 * Provides navigation helpers ({@code open()}, {@code open(String)}, {@code getDriver()}, {@code getWait()})
 * as interface default methods so {@code BaseTest}, {@code BaseJUnit5Test},
 * {@code BaseCucumberSteps} and {@code BaseConditions} share a single implementation.
 *
 * <p>Canonical behaviour:
 * <ul>
 *   <li>{@code open()} navigates to {@code execution.baseUrl} from {@code testfly.yml}</li>
 *   <li>{@code open(path)} concatenates {@code baseUrl + path} with slash-normalisation</li>
 *   <li>Both log via {@link StepLogger} and inject {@link ConsoleErrorCollector} shim when enabled</li>
 *   <li>{@code getDriver()} delegates to {@link DriverManager#getDriver()}</li>
 *   <li>{@code getWait()} builds a {@link WebDriverWait} from {@code timeouts.explicit}</li>
 * </ul>
 *
 * @since 1.10.0
 */
@TestFlyApi(since = "1.10.0")
public interface NavigationSupport {

    /** Returns the framework-managed {@link WebDriver} for the calling thread. */
    default WebDriver getDriver() {
        return DriverManager.getDriver();
    }

    /** Returns a {@link WebDriverWait} using the explicit timeout from {@code testfly.yml}. */
    default WebDriverWait getWait() {
        int timeout = TestFlyContext.getConfig().getTimeouts().getExplicit();
        return new WebDriverWait(getDriver(), Duration.ofSeconds(timeout));
    }

    /** Navigates to {@code execution.baseUrl} from {@code testfly.yml}. */
    default void open() {
        String url = baseUrl();
        StepLogger.step("Open " + url);
        getDriver().get(url);
        if (ConsoleErrorCollector.isEnabled()) ConsoleErrorCollector.injectShim();
    }

    /** Navigates to {@code baseUrl + path} with slash normalisation, or directly to {@code path} if it is an absolute URL. */
    default void open(String path) {
        String url;
        if (path.startsWith("http://") || path.startsWith("https://")) {
            url = path;
        } else {
            String base = baseUrl();
            if (base.endsWith("/") && path.startsWith("/")) {
                url = base.substring(0, base.length() - 1) + path;
            } else if (!base.endsWith("/") && !path.startsWith("/")) {
                url = base + "/" + path;
            } else {
                url = base + path;
            }
        }
        StepLogger.step("Open " + url);
        getDriver().get(url);
        if (ConsoleErrorCollector.isEnabled()) ConsoleErrorCollector.injectShim();
    }

    private String baseUrl() {
        String url = TestFlyContext.getConfig().getExecution().getBaseUrl();
        if (url == null || url.isEmpty()) {
            throw new IllegalStateException("execution.baseUrl is not set in testfly.yml");
        }
        return url;
    }
}
