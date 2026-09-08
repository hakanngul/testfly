package io.testfly.unit.loadtest;

import io.testfly.ci.CiEnvironmentDetector;
import io.testfly.config.DotEnvLoader;
import io.testfly.healing.HealingCache;
import io.testfly.internal.TestFlyContext;
import io.testfly.lifecycle.FrameworkBootstrap;
import io.testfly.reporting.ReportAdapter;
import io.testfly.reporting.ReportAdapterRegistry;
import io.testfly.reporting.ReportPaths;
import org.mockito.MockedStatic;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.Mockito.mockStatic;
import static org.testng.Assert.*;

/**
 * Unit tests verifying config-gated registration of {@link io.testfly.loadtest.LoadTestReportAdapter}
 * during {@link FrameworkBootstrap#initialize()}.
 */
@Test(singleThreaded = true)
public class FrameworkBootstrapLoadTestTest {

    private static final Object GLOBAL_REPORT_LOCK = ReportPaths.class;
    private static final Object CONTEXT_LOCK = TestFlyContext.class;

    private String savedConfigPath;
    private Path tempDir;

    @BeforeMethod
    public void setUp() throws Exception {
        synchronized (GLOBAL_REPORT_LOCK) {
            synchronized (CONTEXT_LOCK) {
                savedConfigPath = System.getProperty("testfly.config");
                System.clearProperty("testfly.config");
                tempDir = Files.createTempDirectory("bootstrap-loadtest-");
                resetState();
            }
        }
    }

    @AfterMethod
    public void tearDown() throws Exception {
        synchronized (GLOBAL_REPORT_LOCK) {
            synchronized (CONTEXT_LOCK) {
                if (savedConfigPath != null) {
                    System.setProperty("testfly.config", savedConfigPath);
                } else {
                    System.clearProperty("testfly.config");
                }
                if (tempDir != null && Files.exists(tempDir)) {
                    Files.walk(tempDir)
                            .sorted(Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(File::delete);
                }
                resetState();
            }
        }
    }

    private static void resetState() throws Exception {
        // Reset TestFlyContext
        TestFlyContext.reset();
        Field configField = TestFlyContext.class.getDeclaredField("CONFIG");
        configField.setAccessible(true);
        AtomicReference<?> ref = (AtomicReference<?>) configField.get(null);
        ref.set(null);
        TestFlyContext.clearCurrentTestId();

        // Reset DotEnvLoader
        Field loadedEnv = DotEnvLoader.class.getDeclaredField("loaded");
        loadedEnv.setAccessible(true);
        loadedEnv.set(null, false);

        // Reset HealingCache
        Field loadedHealing = HealingCache.class.getDeclaredField("loaded");
        loadedHealing.setAccessible(true);
        loadedHealing.set(null, false);
        Field cacheField = HealingCache.class.getDeclaredField("CACHE");
        cacheField.setAccessible(true);
        ((Map<?, ?>) cacheField.get(null)).clear();

        // Reset ReportAdapterRegistry
        synchronized (ReportAdapterRegistry.class) {
            Field adapters = ReportAdapterRegistry.class.getDeclaredField("adapters");
            adapters.setAccessible(true);
            ((List<?>) adapters.get(null)).clear();

            Field loaded = ReportAdapterRegistry.class.getDeclaredField("loaded");
            loaded.setAccessible(true);
            loaded.set(null, false);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<ReportAdapter> getAdapters() throws Exception {
        synchronized (ReportAdapterRegistry.class) {
            Field field = ReportAdapterRegistry.class.getDeclaredField("adapters");
            field.setAccessible(true);
            return (List<ReportAdapter>) field.get(null);
        }
    }

    @Test
    public void bootstrap_withReportEnabledTrue_registersLoadTestReportAdapter() throws Exception {
        synchronized (GLOBAL_REPORT_LOCK) {
            synchronized (CONTEXT_LOCK) {
                String yaml = """
                        execution:
                          mode: local
                          baseUrl: https://example.com
                        browser:
                          name: chrome
                        timeouts:
                          explicit: 10
                          pageLoad: 30
                        loadtest:
                          reportEnabled: true
                        """;
                Path configFile = tempDir.resolve("testfly.yml");
                Files.writeString(configFile, yaml);
                System.setProperty("testfly.config", configFile.toString());

                try (MockedStatic<CiEnvironmentDetector> ciMock = mockStatic(CiEnvironmentDetector.class)) {
                    ciMock.when(CiEnvironmentDetector::isCI).thenReturn(false);

                    FrameworkBootstrap.initialize();

                    List<ReportAdapter> adapters = getAdapters();
                    boolean registered = adapters.stream().anyMatch(a -> "loadtest".equals(a.getName()));
                    assertTrue(registered, "LoadTestReportAdapter should be registered when loadtest.reportEnabled: true");
                }
            }
        }
    }

    @Test
    public void bootstrap_withReportEnabledFalse_doesNotRegisterLoadTestReportAdapter() throws Exception {
        synchronized (GLOBAL_REPORT_LOCK) {
            synchronized (CONTEXT_LOCK) {
                String yaml = """
                        execution:
                          mode: local
                          baseUrl: https://example.com
                        browser:
                          name: chrome
                        timeouts:
                          explicit: 10
                          pageLoad: 30
                        loadtest:
                          reportEnabled: false
                        """;
                Path configFile = tempDir.resolve("testfly.yml");
                Files.writeString(configFile, yaml);
                System.setProperty("testfly.config", configFile.toString());

                try (MockedStatic<CiEnvironmentDetector> ciMock = mockStatic(CiEnvironmentDetector.class)) {
                    ciMock.when(CiEnvironmentDetector::isCI).thenReturn(false);

                    FrameworkBootstrap.initialize();

                    List<ReportAdapter> adapters = getAdapters();
                    boolean registered = adapters.stream().anyMatch(a -> "loadtest".equals(a.getName()));
                    assertFalse(registered, "LoadTestReportAdapter should NOT be registered when loadtest.reportEnabled: false");
                }
            }
        }
    }
}
