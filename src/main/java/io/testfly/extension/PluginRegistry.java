package io.testfly.extension;

import io.testfly.config.TestFlyConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Loads and manages {@link TestFlyPlugin} instances.
 *
 * <p>SPI plugins are discovered automatically; programmatic plugins can be
 * added via {@link #register(TestFlyPlugin, TestFlyConfig)} before
 * {@link #loadAll(TestFlyConfig)} is called.
 */
public final class PluginRegistry {

    private static final List<TestFlyPlugin> plugins = new ArrayList<>();

    private PluginRegistry() {}

    /**
     * Discovers all SPI-registered plugins, calls {@code onLoad}, and logs each one.
     * Safe to call multiple times — subsequent calls are no-ops.
     */
    public static synchronized void loadAll(TestFlyConfig config) {
        if (!plugins.isEmpty()) return;
        ServiceLoader<TestFlyPlugin> loader = ServiceLoader.load(TestFlyPlugin.class);
        for (TestFlyPlugin plugin : loader) {
            if (!checkVersion(plugin)) continue;
            plugins.add(plugin);
            plugin.onLoad(config);
            System.out.println("[TestFly] Plugin loaded: " + plugin.getName());
        }
    }

    /**
     * Programmatically registers and immediately activates a plugin.
     * Must be called before framework boot to guarantee correct ordering.
     */
    public static synchronized void register(TestFlyPlugin plugin, TestFlyConfig config) {
        if (!checkVersion(plugin)) return;
        plugins.add(plugin);
        plugin.onLoad(config);
        System.out.println("[TestFly] Plugin registered: " + plugin.getName());
    }

    /** Calls {@code onUnload} on every plugin and clears the registry. */
    public static synchronized void unloadAll() {
        for (TestFlyPlugin plugin : plugins) {
            try {
                plugin.onUnload();
            } catch (Exception e) {
                System.err.println(
                    "[TestFly] Plugin unload error [" + plugin.getName() + "]: " + e.getMessage()
                );
            }
        }
        plugins.clear();
    }

    /** Returns an unmodifiable snapshot of the currently loaded plugins. */
    public static List<TestFlyPlugin> getPlugins() {
        return Collections.unmodifiableList(plugins);
    }

    private static boolean checkVersion(TestFlyPlugin plugin) {
        String required = plugin.minFrameworkVersion();
        if (FrameworkVersion.isOlderThan(FrameworkVersion.get(), required)) {
            System.err.println(
                "[TestFly] Plugin skipped [" + plugin.getName() + "]: requires >= " +
                required + ", running " + FrameworkVersion.get()
            );
            return false;
        }
        return true;
    }
}
