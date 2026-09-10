package io.testfly.unit.loadtest;

import io.testfly.loadtest.LoadTestFeeder;
import org.testng.annotations.Test;

import java.util.Map;

import static org.testng.Assert.*;

/**
 * Tests CSV and JSON file feeders using test resource files.
 */
@Test(singleThreaded = true)
public class LoadTestFeederFileTest {

    // ── CSV Feeder ───────────────────────────────────────────────────────

    @Test
    public void testCsvFeederReadsFile() {
        LoadTestFeeder feeder = LoadTestFeeder.csv("loadtest/users.csv");

        assertTrue(feeder.hasNext(), "CSV feeder should have rows");

        Map<String, Object> row1 = feeder.next();
        assertEquals(row1.get("username"), "alice");
        assertEquals(row1.get("password"), "pass123");
        assertEquals(row1.get("email"), "alice@example.com");

        Map<String, Object> row2 = feeder.next();
        assertEquals(row2.get("username"), "bob");

        Map<String, Object> row3 = feeder.next();
        assertEquals(row3.get("username"), "charlie");
    }

    @Test
    public void testCsvFeederCyclesRoundRobin() {
        LoadTestFeeder feeder = LoadTestFeeder.csv("loadtest/users.csv");

        // 3 rows → 4th call should cycle back to row 1
        feeder.next(); // alice
        feeder.next(); // bob
        feeder.next(); // charlie
        Map<String, Object> row4 = feeder.next(); // alice again

        assertEquals(row4.get("username"), "alice");
    }

    @Test
    public void testCsvFeederReset() {
        LoadTestFeeder feeder = LoadTestFeeder.csv("loadtest/users.csv");

        feeder.next(); // alice
        feeder.next(); // bob
        feeder.reset();

        Map<String, Object> afterReset = feeder.next();
        assertEquals(afterReset.get("username"), "alice");
    }

    @Test
    public void testCsvFeederMissingFile() {
        LoadTestFeeder feeder = LoadTestFeeder.csv("nonexistent.csv");

        assertFalse(feeder.hasNext());
        Map<String, Object> result = feeder.next();
        assertTrue(result.isEmpty());
    }

    // ── JSON Feeder ──────────────────────────────────────────────────────

    @Test
    public void testJsonFeederReadsFile() {
        LoadTestFeeder feeder = LoadTestFeeder.json("loadtest/products.json");

        assertTrue(feeder.hasNext(), "JSON feeder should have rows");

        Map<String, Object> row1 = feeder.next();
        assertEquals(row1.get("productId"), "1");
        assertEquals(row1.get("productName"), "Laptop");
        assertEquals(row1.get("price"), "999");

        Map<String, Object> row2 = feeder.next();
        assertEquals(row2.get("productName"), "Phone");
    }

    @Test
    public void testJsonFeederCyclesRoundRobin() {
        LoadTestFeeder feeder = LoadTestFeeder.json("loadtest/products.json");

        feeder.next(); // 1
        feeder.next(); // 2
        feeder.next(); // 3
        Map<String, Object> row4 = feeder.next(); // back to 1

        assertEquals(row4.get("productId"), "1");
    }

    @Test
    public void testJsonFeederReset() {
        LoadTestFeeder feeder = LoadTestFeeder.json("loadtest/products.json");

        feeder.next();
        feeder.next();
        feeder.reset();

        assertEquals(feeder.next().get("productId"), "1");
    }

    @Test
    public void testJsonFeederMissingFile() {
        LoadTestFeeder feeder = LoadTestFeeder.json("nonexistent.json");

        assertFalse(feeder.hasNext());
        assertTrue(feeder.next().isEmpty());
    }
}
