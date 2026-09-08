package io.testfly.unit.loadtest;

import io.testfly.loadtest.LoadScenario;
import io.testfly.loadtest.LoadTestConfig;
import io.testfly.loadtest.LoadTestRunner;
import io.testfly.loadtest.internal.GatlingBridge;
import io.testfly.loadtest.internal.GatlingEngine;
import io.testfly.loadtest.internal.JdkLoadEngine;
import io.testfly.loadtest.internal.LoadTestEngine;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.testng.Assert.*;

/**
 * Tests engine selection logic in {@link LoadTestRunner}.
 *
 * <p>Verifies acceptance criteria for Sprint 3:
 * <ul>
 *   <li>{@code engine: jdk} always selects {@link JdkLoadEngine}</li>
 *   <li>{@code engine: gatling} selects {@link GatlingEngine} when available</li>
 *   <li>{@code engine: gatling} + Gatling not available throws {@link IllegalStateException} with dependency hint</li>
 *   <li>{@code engine: auto} selects {@link GatlingEngine} when available, falls back to {@link JdkLoadEngine}</li>
 *   <li>Unknown engine names throw {@link IllegalArgumentException}</li>
 * </ul>
 */
@Test(singleThreaded = true)
public class EngineSelectionTest {

    private LoadTestEngine selectEngine(String engineName) throws Throwable {
        LoadScenario scenario = LoadScenario.named("Engine Test")
                .baseUrl("http://localhost:8080")
                .engine(engineName);
        LoadTestConfig config = LoadTestConfig.resolveFor(scenario, null, null);
        Method method = LoadTestRunner.class.getDeclaredMethod("selectEngine", LoadTestConfig.class);
        method.setAccessible(true);
        try {
            return (LoadTestEngine) method.invoke(null, config);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    @Test
    public void testJdkEngineAlwaysSelectedWhenConfigured() throws Throwable {
        LoadTestEngine engine = selectEngine("jdk");
        assertNotNull(engine);
        assertEquals(engine.name(), "jdk");
        assertTrue(engine instanceof JdkLoadEngine);
    }

    @Test
    public void testGatlingEngineSelectedWhenAvailable() throws Throwable {
        try (MockedStatic<GatlingBridge> bridge = Mockito.mockStatic(GatlingBridge.class)) {
            bridge.when(GatlingBridge::isAvailable).thenReturn(true);

            LoadTestEngine engine = selectEngine("gatling");
            assertNotNull(engine);
            assertEquals(engine.name(), "gatling");
            assertTrue(engine instanceof GatlingEngine);
        }
    }

    @Test
    public void testGatlingEngineThrowsWhenGatlingAbsent() {
        try (MockedStatic<GatlingBridge> bridge = Mockito.mockStatic(GatlingBridge.class)) {
            bridge.when(GatlingBridge::isAvailable).thenReturn(false);
            bridge.when(GatlingBridge::missingDependencyMessage).thenReturn("Missing Gatling dependency");

            IllegalStateException ex = expectThrows(IllegalStateException.class, () -> selectEngine("gatling"));
            assertTrue(ex.getMessage().contains("Missing Gatling dependency"));
        }
    }

    @Test
    public void testAutoEngineSelectsGatlingWhenAvailable() throws Throwable {
        try (MockedStatic<GatlingBridge> bridge = Mockito.mockStatic(GatlingBridge.class)) {
            bridge.when(GatlingBridge::isAvailable).thenReturn(true);

            LoadTestEngine engine = selectEngine("auto");
            assertNotNull(engine);
            assertEquals(engine.name(), "gatling");
            assertTrue(engine instanceof GatlingEngine);
        }
    }

    @Test
    public void testAutoEngineFallsBackToJdkWhenGatlingAbsent() throws Throwable {
        try (MockedStatic<GatlingBridge> bridge = Mockito.mockStatic(GatlingBridge.class)) {
            bridge.when(GatlingBridge::isAvailable).thenReturn(false);

            LoadTestEngine engine = selectEngine("auto");
            assertNotNull(engine);
            assertEquals(engine.name(), "jdk");
            assertTrue(engine instanceof JdkLoadEngine);
        }
    }

    @Test
    public void testUnknownEngineThrows() {
        IllegalArgumentException ex = expectThrows(IllegalArgumentException.class, () -> selectEngine("nonexistent"));
        assertTrue(ex.getMessage().contains("Unknown engine: 'nonexistent'"));
    }

    @Test
    public void testGatlingEngineNameAndAvailability() {
        GatlingEngine engine = new GatlingEngine();
        assertEquals(engine.name(), "gatling");
        assertEquals(engine.isAvailable(), GatlingBridge.isAvailable());
    }

    @Test
    public void testJdkEngineNameAndAvailability() {
        JdkLoadEngine engine = new JdkLoadEngine();
        assertEquals(engine.name(), "jdk");
        assertTrue(engine.isAvailable());
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testGatlingEngineEmptyStepsValidation() {
        GatlingEngine engine = new GatlingEngine();
        LoadScenario scenario = LoadScenario.named("Empty").baseUrl("http://localhost:8080");
        LoadTestConfig config = LoadTestConfig.resolveFor(scenario, null, null);
        engine.execute(scenario, config);
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testGatlingEngineThrowsWhenNotAvailableOnExecute() {
        try (MockedStatic<GatlingBridge> bridge = Mockito.mockStatic(GatlingBridge.class)) {
            bridge.when(GatlingBridge::isAvailable).thenReturn(false);
            bridge.when(GatlingBridge::missingDependencyMessage).thenReturn("Not available");

            GatlingEngine engine = new GatlingEngine();
            LoadScenario scenario = LoadScenario.named("Test").baseUrl("http://localhost:8080").get("/health");
            LoadTestConfig config = LoadTestConfig.resolveFor(scenario, null, null);
            engine.execute(scenario, config);
        }
    }
}
