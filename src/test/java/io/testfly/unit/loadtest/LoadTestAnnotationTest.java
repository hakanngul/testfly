package io.testfly.unit.loadtest;

import io.testfly.loadtest.LoadTest;
import io.testfly.loadtest.LoadTestConfig;
import org.testng.annotations.Test;

import java.lang.reflect.Method;

import static org.testng.Assert.*;

/**
 * Tests {@code @LoadTest} annotation resolution and config merging.
 */
@Test(singleThreaded = true)
public class LoadTestAnnotationTest {

    // ── Test fixtures ────────────────────────────────────────────────────

    @LoadTest(users = 200, rampUp = "30s", hold = "2m")
    static class ClassLevelAnnotated {}

    static class NoAnnotation {}

    static class MethodLevelAnnotated {
        @LoadTest(users = 500)
        public void heavyTest() {}

        public void defaultTest() {}
    }

    @LoadTest(users = 100, rampUp = "20s", engine = "jdk")
    static class BothLevelsAnnotated {
        @LoadTest(users = 300, hold = "5m")
        public void overriddenTest() {}
    }

    // ── Tests ────────────────────────────────────────────────────────────

    @Test
    public void testClassLevelAnnotation() {
        LoadTestConfig config = LoadTestConfig.resolve(ClassLevelAnnotated.class, null);

        assertEquals(config.getUsers(), 200);
        assertEquals(config.getRampUp(), "30s");
        assertEquals(config.getHold(), "2m");
        // Unset fields fall through to hardcoded defaults
        assertEquals(config.getCooldown(), "5s");
        assertEquals(config.getEngine(), "auto");
    }

    @Test
    public void testNoAnnotation() {
        LoadTestConfig config = LoadTestConfig.resolve(NoAnnotation.class, null);

        // All hardcoded defaults
        assertEquals(config.getUsers(), 10);
        assertEquals(config.getRampUp(), "10s");
        assertEquals(config.getHold(), "30s");
        assertEquals(config.getCooldown(), "5s");
        assertEquals(config.getEngine(), "auto");
    }

    @Test
    public void testMethodLevelOverridesClass() throws Exception {
        Method heavy = MethodLevelAnnotated.class.getMethod("heavyTest");
        Method defaultM = MethodLevelAnnotated.class.getMethod("defaultTest");

        LoadTestConfig heavyConfig = LoadTestConfig.resolve(MethodLevelAnnotated.class, heavy);
        assertEquals(heavyConfig.getUsers(), 500);

        LoadTestConfig defaultConfig = LoadTestConfig.resolve(MethodLevelAnnotated.class, defaultM);
        assertEquals(defaultConfig.getUsers(), 10); // hardcoded default
    }

    @Test
    public void testMethodOverridesClassFields() throws Exception {
        Method m = BothLevelsAnnotated.class.getMethod("overriddenTest");
        LoadTestConfig config = LoadTestConfig.resolve(BothLevelsAnnotated.class, m);

        // Method sets users=300, hold="5m" — overrides class
        assertEquals(config.getUsers(), 300);
        assertEquals(config.getHold(), "5m");

        // Class sets rampUp="20s", engine="jdk" — method doesn't override these
        assertEquals(config.getRampUp(), "20s");
        assertEquals(config.getEngine(), "jdk");
    }

    @Test
    public void testUsersClampedToMaxUsers() {
        LoadTestConfig config = LoadTestConfig.resolve(ClassLevelAnnotated.class, null);
        // maxUsers default is 1000, users=200 → no clamping
        assertEquals(config.getUsers(), 200);
    }

    @Test
    public void testDurationSecondsFromAnnotation() {
        LoadTestConfig config = LoadTestConfig.resolve(ClassLevelAnnotated.class, null);

        assertEquals(config.getRampUpSeconds(), 30);
        assertEquals(config.getHoldSeconds(), 120); // 2m = 120s
        assertEquals(config.getCooldownSeconds(), 5);
    }

    @Test
    public void testToStringContainsKeyFields() {
        LoadTestConfig config = LoadTestConfig.resolve(ClassLevelAnnotated.class, null);
        String s = config.toString();

        assertTrue(s.contains("users=200"));
        assertTrue(s.contains("rampUp=30s"));
        assertTrue(s.contains("engine=auto"));
    }
}
