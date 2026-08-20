package io.testfly.extension;

import io.testfly.api.TestFlyApi;
import io.testfly.config.TestFlyConfig;

/**
 * Extension point for TestFly plugins.
 *
 * <p>Plugins are discovered automatically via Java SPI. Create a file at:
 * <pre>META-INF/services/io.testfly.extension.TestFlyPlugin</pre>
 * containing the fully-qualified class name of each implementation.
 *
 * <p>Plugins can also be registered programmatically before framework boot:
 * <pre>PluginRegistry.register(new MyPlugin(), config);</pre>
 */
@TestFlyApi(since = "0.3.0")
public interface TestFlyPlugin {

    /** Unique human-readable name used in log messages. */
    String getName();

    /**
     * Minimum TestFly version this plugin requires.
     *
     * <p>The framework checks this before calling {@link #onLoad}. If the running
     * version is older, an {@link IncompatiblePluginException} is thrown and the
     * plugin is skipped.
     *
     * <p>Override this to declare a minimum version:
     * <pre>
     * public String minFrameworkVersion() { return "0.7.0"; }
     * </pre>
     *
     * @return minimum required version string, e.g. {@code "0.7.0"}
     */
    default String minFrameworkVersion() { return "0.0.0"; }

    /**
     * Called once after the framework config is loaded and validated.
     * Use this to read config values and initialise plugin state.
     */
    default void onLoad(TestFlyConfig config) {}

    /**
     * Called once when the test suite finishes (after all reports are generated).
     * Use this to flush resources, close connections, etc.
     */
    default void onUnload() {}
}
