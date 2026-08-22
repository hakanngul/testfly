package io.testfly.lifecycle;

import io.testfly.ci.CiEnvironmentDetector;
import io.testfly.config.ConfigurationLoader;
import io.testfly.config.DotEnvLoader;
import io.testfly.config.TestFlyConfig;
import io.testfly.healing.HealingCache;
import io.testfly.config.TestFlyDefaults;
import io.testfly.driver.DriverProviderRegistry;
import io.testfly.execution.ExecutionValidator;
import io.testfly.extension.PluginRegistry;
import io.testfly.hooks.HookRegistry;
import io.testfly.internal.TestFlyContext;
import io.testfly.config.TestFlyConfig.Notifications;
import io.testfly.reporting.AllureReportAdapter;
import io.testfly.reporting.NotificationAdapter;
import io.testfly.reporting.ReportAdapterRegistry;
import io.testfly.reporting.reportportal.ReportPortalAttachmentSender;
import io.testfly.reporting.reportportal.ReportPortalPropertiesWriter;
import io.testfly.reporting.reportportal.ReportPortalReportAdapter;

/**
 * FrameworkBootstrap is responsible for initializing TestFly
 * before any TestNG execution begins.
 *
 * This class must be invoked exactly once per test suite.
 */
public final class FrameworkBootstrap {

    private FrameworkBootstrap() {
        // utility class
    }

    public static void initialize() {
        if (TestFlyContext.isInitialized()) {
            return;
        }
        // Load .env BEFORE config so ${VAR} placeholders can resolve
        DotEnvLoader.load();
        // Load persistent healing cache so known-good locators are tried first
        HealingCache.load();
        TestFlyConfig config = ConfigurationLoader.load();
        TestFlyDefaults.applyMissing(config);
        applyCiOverrides(config);
        ExecutionValidator.validate(config.getExecution());

        TestFlyContext.initialize(config);

        // Honor a custom test-id attribute for the accessibility-first locators
        if (config.getLocators() != null && config.getLocators().getTestIdAttribute() != null) {
            io.testfly.locator.Locator.setTestIdAttribute(config.getLocators().getTestIdAttribute());
        }

        // Load all SPI-registered extension points
        DriverProviderRegistry.loadAll();
        HookRegistry.loadAll();
        ReportAdapterRegistry.loadAll();
        PluginRegistry.loadAll(config);

        System.out.println("[TestFly] 🤖 AI test authoring: pip install testfly-mcp  →  https://pypi.org/project/testfly-mcp");

        // Opt-in built-in adapters
        TestFlyConfig.Reporting reporting = config.getReporting();
        if (reporting != null && reporting.isAllureEnabled()) {
            ReportAdapterRegistry.register(new AllureReportAdapter());
            System.out.println("[TestFly] Allure adapter enabled → target/allure-results/");
        }

        if (reporting != null && reporting.getReportPortal() != null && reporting.getReportPortal().isEnabled()) {
            try {
                ReportPortalPropertiesWriter.applyAsSystemProperties(config);
                ReportAdapterRegistry.register(new ReportPortalReportAdapter());
                ReportAdapterRegistry.register(new ReportPortalAttachmentSender());
                System.out.println("[TestFly] ReportPortal adapter enabled → "
                        + reporting.getReportPortal().getEndpoint());
            } catch (IllegalArgumentException e) {
                System.err.println("[TestFly] ReportPortal adapter disabled: " + e.getMessage());
            }
        }

        Notifications notifs = config.getNotifications();
        if (notifs != null) {
            boolean hasSlack = notifs.getSlack() != null
                    && notifs.getSlack().getWebhookUrl() != null
                    && !notifs.getSlack().getWebhookUrl().isBlank();
            boolean hasTeams = notifs.getTeams() != null
                    && notifs.getTeams().getWebhookUrl() != null
                    && !notifs.getTeams().getWebhookUrl().isBlank();
            if (hasSlack || hasTeams) {
                ReportAdapterRegistry.register(new NotificationAdapter(notifs));
                System.out.println("[TestFly] Notification adapter enabled"
                        + (hasSlack ? " [Slack]" : "") + (hasTeams ? " [Teams]" : ""));
            }
        }
    }

    /**
     * When running in CI, auto-apply headless mode and tune thread count
     * to available CPU cores — unless the user has explicitly configured them.
     */
    private static void applyCiOverrides(TestFlyConfig config) {
        if (!CiEnvironmentDetector.isCI()) {
            return;
        }

        System.out.println("[TestFly] CI environment detected: "
                + CiEnvironmentDetector.ciName());

        // Force headless — CI agents never have a display
        if (!config.getBrowser().isHeadless()) {
            config.getBrowser().setHeadless(true);
            System.out.println("[TestFly] CI override: browser.headless=true");
        }

        // Auto-tune thread count to CPU cores when the user left the default (1)
        TestFlyConfig.Execution execution = config.getExecution();
        if (execution.getThreadCount() == 1 && !"none".equalsIgnoreCase(execution.getParallel())) {
            int recommended = CiEnvironmentDetector.recommendedThreadCount(
                    execution.getMaxActiveSessions());
            if (recommended > 1) {
                execution.setThreadCount(recommended);
                System.out.println("[TestFly] CI override: threadCount=" + recommended
                        + " (derived from available CPU cores)");
            }
        }

        if (CiEnvironmentDetector.isContainer()) {
            System.out.println("[TestFly] Container environment detected — "
                    + "Docker/sandbox flags will be applied to browser options.");
        }
    }
}
