package io.testfly.cucumber;

import io.testfly.api.TestFlyApi;
import io.testfly.listeners.SuiteExecutionListener;
import io.testfly.listeners.TestExecutionListener;
import io.cucumber.testng.AbstractTestNGCucumberTests;
import org.testng.annotations.Listeners;

/**
 * Base class for Cucumber runner classes in TestFly.
 *
 * <p>Annotate your runner with {@code @CucumberOptions} and extend this class:
 * <pre>
 * {@literal @}CucumberOptions(
 *     features = "src/test/resources/features",
 *     glue     = {"com.myapp.steps", "io.testfly.cucumber"},
 *     plugin   = {"pretty", "io.testfly.cucumber.CucumberStepLogger"}
 * )
 * public class CucumberRunner extends BaseCucumberTest {}
 * </pre>
 *
 * <p>The framework lifecycle is fully automatic:
 * <ul>
 *   <li>Driver created and destroyed per scenario by {@link CucumberHooks}.</li>
 *   <li>Metrics, screenshots, step timeline, and HTML report handled by framework listeners.</li>
 *   <li>All {@code testfly.yml} settings (browser, timeouts, retry, etc.) apply.</li>
 * </ul>
 *
 * <p>Step definition classes should extend {@link BaseCucumberSteps} to get
 * {@code getDriver()}, {@code open()}, {@code $()} and {@code assertThat()}.
 */
@TestFlyApi(since = "1.9.0")
@Listeners({SuiteExecutionListener.class, TestExecutionListener.class})
public abstract class BaseCucumberTest extends AbstractTestNGCucumberTests {
    // Lifecycle is handled by CucumberHooks (driver) and SuiteExecutionListener (reports).
}
