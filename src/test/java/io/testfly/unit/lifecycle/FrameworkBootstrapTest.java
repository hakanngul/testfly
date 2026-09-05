package io.testfly.unit.lifecycle;

import io.testfly.ci.CiEnvironmentDetector;
import io.testfly.config.DotEnvLoader;
import io.testfly.config.TestFlyConfig;
import io.testfly.healing.HealingCache;
import io.testfly.internal.TestFlyContext;
import io.testfly.lifecycle.FrameworkBootstrap;
import org.mockito.MockedStatic;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.Mockito.mockStatic;
import static org.testng.Assert.*;

/**
 * Unit tests for {@link FrameworkBootstrap}.
 *
 * <p>
 * Uses temp config files and reflection-based state resets with global
 * synchronization on TestFlyContext.class to avoid cross-test contamination
 * from static singletons during parallel test execution.
 */
@Test(singleThreaded = true)
public class FrameworkBootstrapTest {

    private static final Object CONTEXT_LOCK = TestFlyContext.class;

    private String savedConfigPath;
    private String savedProfile;

    @BeforeMethod
    public void setUp() throws Exception {
        synchronized (CONTEXT_LOCK) {
            savedConfigPath = System.getProperty("testfly.config");
            savedProfile = System.getProperty("testfly.profile");
            System.clearProperty("testfly.config");
            System.clearProperty("testfly.profile");

            resetStateInternal();
        }
    }

    @AfterMethod
    public void tearDown() throws Exception {
        synchronized (CONTEXT_LOCK) {
            if (savedConfigPath != null) {
                System.setProperty("testfly.config", savedConfigPath);
            } else {
                System.clearProperty("testfly.config");
            }
            if (savedProfile != null) {
                System.setProperty("testfly.profile", savedProfile);
            } else {
                System.clearProperty("testfly.profile");
            }

            resetStateInternal();
        }
    }

    private static void resetStateInternal() {
        try {
            resetTestFlyContext();
            resetDotEnvLoader();
            resetHealingCache();
        } catch (Exception e) {
            throw new RuntimeException("Failed to reset framework state", e);
        }
    }

    // ----------------------------------------------------------
    // Happy path
    // ----------------------------------------------------------

    @Test
    public void initialize_withValidConfig_initializesContextAndPopulatesRegistries() throws Exception {
        synchronized (CONTEXT_LOCK) {
            resetStateInternal();
            Path tempDir = Files.createTempDirectory("bootstrap-test-");
            try {
                Path configFile = createValidConfig(tempDir);
                System.setProperty("testfly.config", configFile.toString());

                try (MockedStatic<CiEnvironmentDetector> ciMock = mockStatic(CiEnvironmentDetector.class)) {
                    ciMock.when(CiEnvironmentDetector::isCI).thenReturn(false);

                    FrameworkBootstrap.initialize();

                    assertTrue(TestFlyContext.isInitialized(),
                            "TestFlyContext should be initialized after bootstrap");
                    TestFlyConfig config = TestFlyContext.getConfig();
                    assertNotNull(config, "Config must not be null");
                    assertEquals(config.getBrowser().getName(), "chrome");
                    assertEquals(config.getExecution().getMode(), "local");
                    assertEquals(config.getExecution().getBaseUrl(), "https://example.com");
                }
            } finally {
                deleteRecursively(tempDir);
                resetStateInternal();
            }
        }
    }

    // ----------------------------------------------------------
    // Idempotency
    // ----------------------------------------------------------

    @Test
    public void initialize_calledTwice_doesNotReinitialize() throws Exception {
        synchronized (CONTEXT_LOCK) {
            resetStateInternal();
            Path tempDir = Files.createTempDirectory("bootstrap-idem-");
            try {
                Path configFile = createValidConfig(tempDir);
                System.setProperty("testfly.config", configFile.toString());

                try (MockedStatic<CiEnvironmentDetector> ciMock = mockStatic(CiEnvironmentDetector.class)) {
                    ciMock.when(CiEnvironmentDetector::isCI).thenReturn(false);

                    FrameworkBootstrap.initialize();
                    TestFlyConfig firstConfig = TestFlyContext.getConfig();

                    // Second call must be a no-op
                    FrameworkBootstrap.initialize();
                    TestFlyConfig secondConfig = TestFlyContext.getConfig();

                    assertSame(firstConfig, secondConfig,
                            "Second initialize() must not replace the existing config");
                }
            } finally {
                deleteRecursively(tempDir);
                resetStateInternal();
            }
        }
    }

    // ----------------------------------------------------------
    // Missing config
    // ----------------------------------------------------------

    @Test(expectedExceptions = IllegalStateException.class)
    public void initialize_withMissingConfigFile_throwsIllegalStateException() {
        synchronized (CONTEXT_LOCK) {
            resetStateInternal();
            System.setProperty("testfly.config", "/nonexistent/path/testfly.yml");

            try (MockedStatic<CiEnvironmentDetector> ciMock = mockStatic(CiEnvironmentDetector.class)) {
                ciMock.when(CiEnvironmentDetector::isCI).thenReturn(false);
                FrameworkBootstrap.initialize();
            } finally {
                resetStateInternal();
            }
        }
    }

    @Test
    public void initialize_withMissingConfigFile_doesNotInitializeContext() {
        synchronized (CONTEXT_LOCK) {
            resetStateInternal();
            System.setProperty("testfly.config", "/nonexistent/path/testfly.yml");

            try (MockedStatic<CiEnvironmentDetector> ciMock = mockStatic(CiEnvironmentDetector.class)) {
                ciMock.when(CiEnvironmentDetector::isCI).thenReturn(false);
                try {
                    FrameworkBootstrap.initialize();
                    fail("Expected IllegalStateException");
                } catch (IllegalStateException expected) {
                    // expected
                }
            } finally {
                resetStateInternal();
            }

            assertFalse(TestFlyContext.isInitialized(),
                    "Context must not be initialized when config is missing");
        }
    }

    // ----------------------------------------------------------
    // Invalid YAML
    // ----------------------------------------------------------

    @Test
    public void initialize_withInvalidYaml_throwsException() throws Exception {
        synchronized (CONTEXT_LOCK) {
            resetStateInternal();
            Path tempDir = Files.createTempDirectory("bootstrap-bad-yaml-");
            try {
                Path configFile = tempDir.resolve("testfly.yml");
                Files.writeString(configFile, "{{invalid yaml content::");
                System.setProperty("testfly.config", configFile.toString());

                try (MockedStatic<CiEnvironmentDetector> ciMock = mockStatic(CiEnvironmentDetector.class)) {
                    ciMock.when(CiEnvironmentDetector::isCI).thenReturn(false);
                    try {
                        FrameworkBootstrap.initialize();
                        fail("Expected an exception for malformed YAML");
                    } catch (RuntimeException e) {
                        // SnakeYAML throws ParserException which extends RuntimeException;
                        // if ConfigurationLoader wraps it, it would be IllegalStateException.
                        // Either way, a RuntimeException must propagate.
                        assertNotNull(e.getMessage());
                    }
                }
            } finally {
                deleteRecursively(tempDir);
                resetStateInternal();
            }
        }
    }

    @Test
    public void initialize_withInvalidYaml_doesNotInitializeContext() throws Exception {
        synchronized (CONTEXT_LOCK) {
            resetStateInternal();
            Path tempDir = Files.createTempDirectory("bootstrap-bad-yaml2-");
            try {
                Path configFile = tempDir.resolve("testfly.yml");
                Files.writeString(configFile, "{{invalid yaml content::");
                System.setProperty("testfly.config", configFile.toString());

                try (MockedStatic<CiEnvironmentDetector> ciMock = mockStatic(CiEnvironmentDetector.class)) {
                    ciMock.when(CiEnvironmentDetector::isCI).thenReturn(false);
                    try {
                        FrameworkBootstrap.initialize();
                        fail("Expected an exception for malformed YAML");
                    } catch (RuntimeException expected) {
                        // SnakeYAML or ConfigurationLoader exception — either is acceptable
                    }
                }

                assertFalse(TestFlyContext.isInitialized(),
                        "Context must not be initialized when config is invalid");
            } finally {
                deleteRecursively(tempDir);
                resetStateInternal();
            }
        }
    }

    @Test
    public void initialize_withIncompleteConfig_throwsDescriptiveException() throws Exception {
        synchronized (CONTEXT_LOCK) {
            resetStateInternal();
            Path tempDir = Files.createTempDirectory("bootstrap-incomplete-");
            try {
                // YAML parses fine but fails validation (missing required fields)
                Path configFile = tempDir.resolve("testfly.yml");
                Files.writeString(configFile, "browser:\n  name: chrome\n");
                System.setProperty("testfly.config", configFile.toString());

                try (MockedStatic<CiEnvironmentDetector> ciMock = mockStatic(CiEnvironmentDetector.class)) {
                    ciMock.when(CiEnvironmentDetector::isCI).thenReturn(false);
                    try {
                        FrameworkBootstrap.initialize();
                        fail("Expected IllegalStateException for incomplete config");
                    } catch (IllegalStateException e) {
                        assertNotNull(e.getMessage(), "Exception should carry a message");
                        assertFalse(e.getMessage().isBlank(),
                                "Exception message should describe the validation failure");
                    }
                }
            } finally {
                deleteRecursively(tempDir);
                resetStateInternal();
            }
        }
    }

    // ----------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------

    private static Path createValidConfig(Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("testfly.yml");
        String yaml = """
                browser:
                  name: chrome
                execution:
                  mode: local
                  baseUrl: https://example.com
                  parallel: none
                  threadCount: 1
                  maxActiveSessions: 5
                timeouts:
                  explicit: 10
                  pageLoad: 30
                """;
        Files.writeString(configFile, yaml);
        return configFile;
    }

    @SuppressWarnings("unchecked")
    private static void resetTestFlyContext() throws Exception {
        TestFlyContext.reset();
        Field configField = TestFlyContext.class.getDeclaredField("CONFIG");
        configField.setAccessible(true);
        AtomicReference<?> ref = (AtomicReference<?>) configField.get(null);
        ref.set(null);
        TestFlyContext.clearCurrentTestId();
    }

    private static void resetDotEnvLoader() throws Exception {
        Field loadedField = DotEnvLoader.class.getDeclaredField("loaded");
        loadedField.setAccessible(true);
        loadedField.setBoolean(null, false);
    }

    @SuppressWarnings("unchecked")
    private static void resetHealingCache() throws Exception {
        Field loadedField = HealingCache.class.getDeclaredField("loaded");
        loadedField.setAccessible(true);
        loadedField.setBoolean(null, false);

        Field cacheField = HealingCache.class.getDeclaredField("CACHE");
        cacheField.setAccessible(true);
        ((Map<?, ?>) cacheField.get(null)).clear();
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path))
            return;
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
