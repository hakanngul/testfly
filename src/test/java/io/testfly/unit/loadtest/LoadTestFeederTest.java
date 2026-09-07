package io.testfly.unit.loadtest;

import io.testfly.loadtest.LoadTestFeeder;
import org.testng.annotations.Test;

import java.util.Map;

import static org.testng.Assert.*;

/**
 * Tests built-in {@link LoadTestFeeder} implementations.
 */
@Test(singleThreaded = true)
public class LoadTestFeederTest {

    @Test
    public void testRandomFeeder() {
        LoadTestFeeder feeder = LoadTestFeeder.random("userId", 1, 100);

        assertTrue(feeder.hasNext());
        for (int i = 0; i < 50; i++) {
            Map<String, Object> vars = feeder.next();
            assertNotNull(vars);
            assertTrue(vars.containsKey("userId"));
            int val = (int) vars.get("userId");
            assertTrue(val >= 1 && val <= 100, "Value " + val + " out of range [1,100]");
        }
    }

    @Test
    public void testUuidFeeder() {
        LoadTestFeeder feeder = LoadTestFeeder.uuid("requestId");

        Map<String, Object> first = feeder.next();
        Map<String, Object> second = feeder.next();

        assertNotNull(first.get("requestId"));
        assertNotNull(second.get("requestId"));
        assertNotEquals(first.get("requestId"), second.get("requestId"));
        assertTrue(feeder.hasNext());
    }

    @Test
    public void testSequenceFeeder() {
        LoadTestFeeder feeder = LoadTestFeeder.sequence("orderId", 1000, 5);

        assertEquals(feeder.next().get("orderId"), 1000L);
        assertEquals(feeder.next().get("orderId"), 1005L);
        assertEquals(feeder.next().get("orderId"), 1010L);

        feeder.reset();
        assertEquals(feeder.next().get("orderId"), 1000L);
    }

    @Test
    public void testConstantFeeder() {
        LoadTestFeeder feeder = LoadTestFeeder.constant("env", "staging");

        assertEquals(feeder.next().get("env"), "staging");
        assertEquals(feeder.next().get("env"), "staging");
        assertTrue(feeder.hasNext());
    }

    @Test
    public void testCsvFeederMissingFileReturnsEmpty() {
        LoadTestFeeder feeder = LoadTestFeeder.csv("nonexistent.csv");

        assertFalse(feeder.hasNext());
        assertTrue(feeder.next().isEmpty());
    }

    @Test
    public void testJsonFeederMissingFileReturnsEmpty() {
        LoadTestFeeder feeder = LoadTestFeeder.json("nonexistent.json");

        assertFalse(feeder.hasNext());
        assertTrue(feeder.next().isEmpty());
    }
}
