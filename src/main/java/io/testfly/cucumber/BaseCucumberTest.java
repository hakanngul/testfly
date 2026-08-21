package io.testfly.cucumber;

import io.testfly.api.TestFlyApi;
import io.testfly.config.TestFlyConfig;
import io.testfly.internal.TestFlyContext;
import io.testfly.lifecycle.FrameworkBootstrap;
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
 *   <li>When {@code reporting.reportportal.enabled=true} and
 *       {@code agent-java-cucumber7} is on the classpath, Given/When/Then steps
 *       are automatically reported to ReportPortal as nested items
 *       (Feature &gt; Scenario &gt; Step).</li>
 * </ul>
 *
 * <p>Step definition classes should extend {@link BaseCucumberSteps} to get
 * {@code getDriver()}, {@code open()}, {@code $()} and {@code assertThat()}.
 */
@TestFlyApi(since = "1.9.0")
@Listeners({SuiteExecutionListener.class, TestExecutionListener.class})
public abstract class BaseCucumberTest extends AbstractTestNGCucumberTests {

    /**
     * ReportPortal Cucumber 7 plugin class name.
     * When this class is on the classpath and ReportPortal is enabled,
     * it is auto-registered as a Cucumber plugin so Given/When/Then steps
     * appear as nested items in ReportPortal.
     */
    private static final String RP_CUCUMBER7_PLUGIN =
            "com.epam.reportportal.cucumber.ScenarioReporter";

    static {
        registerReportPortalCucumberPlugin();
    }

    /**
     * Auto-registers the ReportPortal Cucumber 7 plugin when:
     * <ol>
     *   <li>The agent class is on the classpath</li>
     *   <li>{@code reporting.reportportal.enabled=true} in {@code testfly.yml}</li>
     * </ol>
     *
     * <p>Uses the {@code cucumber.plugin} system property so users don't need to
     * add the plugin to their {@code @CucumberOptions} manually.
     */
    private static void registerReportPortalCucumberPlugin() {
        try {
            Class.forName(RP_CUCUMBER7_PLUGIN);
        } catch (ClassNotFoundException e) {
            return;
        }

        FrameworkBootstrap.initialize();

        if (!TestFlyContext.isInitialized()) return;
        TestFlyConfig config = TestFlyContext.getConfig();
        TestFlyConfig.Reporting reporting = config.getReporting();
        if (reporting == null
                || reporting.getReportPortal() == null
                || !reporting.getReportPortal().isEnabled()) {
            return;
        }

        String existing = System.getProperty("cucumber.plugin", "");
        if (!existing.contains(RP_CUCUMBER7_PLUGIN)) {
            String value = existing.isEmpty()
                    ? RP_CUCUMBER7_PLUGIN
                    : existing + ", " + RP_CUCUMBER7_PLUGIN;
            System.setProperty("cucumber.plugin", value);
            System.out.println("[TestFly] ReportPortal Cucumber 7 plugin auto-registered");
        }
    }
}
