package io.testfly.unit.loadtest;

import io.testfly.config.TestFlyConfig;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * Tests the duration-string parser used by {@code loadtest.rampUp}, {@code hold}, {@code cooldown}.
 */
@Test(singleThreaded = true)
public class DurationParsingTest {

    @Test
    public void testSeconds() {
        assertEquals(TestFlyConfig.LoadTest.parseDurationSeconds("30s", 0), 30);
        assertEquals(TestFlyConfig.LoadTest.parseDurationSeconds("1s", 0), 1);
        assertEquals(TestFlyConfig.LoadTest.parseDurationSeconds("0s", 0), 0);
    }

    @Test
    public void testMinutes() {
        assertEquals(TestFlyConfig.LoadTest.parseDurationSeconds("2m", 0), 120);
        assertEquals(TestFlyConfig.LoadTest.parseDurationSeconds("1m", 0), 60);
    }

    @Test
    public void testHours() {
        assertEquals(TestFlyConfig.LoadTest.parseDurationSeconds("1h", 0), 3600);
        assertEquals(TestFlyConfig.LoadTest.parseDurationSeconds("2h", 0), 7200);
    }

    @Test
    public void testMilliseconds() {
        assertEquals(TestFlyConfig.LoadTest.parseDurationSeconds("500ms", 0), 0); // 500ms / 1000 = 0
        assertEquals(TestFlyConfig.LoadTest.parseDurationSeconds("2000ms", 0), 2);
    }

    @Test
    public void testPlainInteger() {
        assertEquals(TestFlyConfig.LoadTest.parseDurationSeconds("45", 0), 45);
    }

    @Test
    public void testNullAndBlank() {
        assertEquals(TestFlyConfig.LoadTest.parseDurationSeconds(null, 10), 10);
        assertEquals(TestFlyConfig.LoadTest.parseDurationSeconds("", 10), 10);
        assertEquals(TestFlyConfig.LoadTest.parseDurationSeconds("   ", 10), 10);
    }

    @Test
    public void testInvalidFallsBackToDefault() {
        assertEquals(TestFlyConfig.LoadTest.parseDurationSeconds("abc", 7), 7);
        assertEquals(TestFlyConfig.LoadTest.parseDurationSeconds("10x", 7), 7);
    }

    @Test
    public void testCaseInsensitive() {
        assertEquals(TestFlyConfig.LoadTest.parseDurationSeconds("30S", 0), 30);
        assertEquals(TestFlyConfig.LoadTest.parseDurationSeconds("2M", 0), 120);
        assertEquals(TestFlyConfig.LoadTest.parseDurationSeconds("1H", 0), 3600);
    }

    @Test
    public void testWhitespaceTolerance() {
        assertEquals(TestFlyConfig.LoadTest.parseDurationSeconds(" 30s ", 0), 30);
        assertEquals(TestFlyConfig.LoadTest.parseDurationSeconds(" 2 m ", 0), 120);
    }

    @Test
    public void testGetterIntegration() {
        TestFlyConfig.LoadTest lt = new TestFlyConfig.LoadTest();
        lt.setRampUp("45s");
        lt.setHold("3m");
        lt.setCooldown("15s");

        assertEquals(lt.getRampUpSeconds(), 45);
        assertEquals(lt.getHoldSeconds(), 180);
        assertEquals(lt.getCooldownSeconds(), 15);
    }
}
