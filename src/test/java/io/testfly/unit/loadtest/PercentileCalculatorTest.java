package io.testfly.unit.loadtest;

import io.testfly.loadtest.internal.PercentileCalculator;
import org.testng.annotations.Test;

import java.util.stream.IntStream;

import static org.testng.Assert.assertEquals;

/**
 * Tests the {@link PercentileCalculator} utility.
 */
@Test(singleThreaded = true)
public class PercentileCalculatorTest {

    @Test
    public void testEmptyArray() {
        double[] empty = new double[0];
        assertEquals(PercentileCalculator.percentile(empty, 50), 0.0);
        assertEquals(PercentileCalculator.p50(empty), 0.0);
        assertEquals(PercentileCalculator.p90(empty), 0.0);
        assertEquals(PercentileCalculator.p95(empty), 0.0);
        assertEquals(PercentileCalculator.p99(empty), 0.0);
        assertEquals(PercentileCalculator.mean(empty), 0.0);
        assertEquals(PercentileCalculator.min(empty), 0.0);
        assertEquals(PercentileCalculator.max(empty), 0.0);
        assertEquals(PercentileCalculator.linearInterpolation(empty, 50), 0.0);
    }

    @Test
    public void testNullArray() {
        assertEquals(PercentileCalculator.percentile(null, 50), 0.0);
        assertEquals(PercentileCalculator.p50(null), 0.0);
        assertEquals(PercentileCalculator.mean(null), 0.0);
        assertEquals(PercentileCalculator.min(null), 0.0);
        assertEquals(PercentileCalculator.max(null), 0.0);
        assertEquals(PercentileCalculator.linearInterpolation(null, 50), 0.0);
    }

    @Test
    public void testSingleElementArray() {
        double[] single = new double[] { 42.0 };
        assertEquals(PercentileCalculator.percentile(single, 50), 42.0);
        assertEquals(PercentileCalculator.p50(single), 42.0);
        assertEquals(PercentileCalculator.p90(single), 42.0);
        assertEquals(PercentileCalculator.p95(single), 42.0);
        assertEquals(PercentileCalculator.p99(single), 42.0);
        assertEquals(PercentileCalculator.mean(single), 42.0);
        assertEquals(PercentileCalculator.min(single), 42.0);
        assertEquals(PercentileCalculator.max(single), 42.0);
        assertEquals(PercentileCalculator.linearInterpolation(single, 50), 42.0);
    }

    @Test
    public void testTenElementsArray() {
        double[] data = new double[] { 10, 20, 30, 40, 50, 60, 70, 80, 90, 100 };

        // Nearest rank: k = ceil(P / 100 * N)
        // p50: ceil(0.50 * 10) = 5 -> index 4 -> 50.0
        assertEquals(PercentileCalculator.p50(data), 50.0);
        // p90: ceil(0.90 * 10) = 9 -> index 8 -> 90.0
        assertEquals(PercentileCalculator.p90(data), 90.0);
        // p95: ceil(0.95 * 10) = 10 -> index 9 -> 100.0
        assertEquals(PercentileCalculator.p95(data), 100.0);
        // p99: ceil(0.99 * 10) = 10 -> index 9 -> 100.0
        assertEquals(PercentileCalculator.p99(data), 100.0);

        assertEquals(PercentileCalculator.min(data), 10.0);
        assertEquals(PercentileCalculator.max(data), 100.0);
        assertEquals(PercentileCalculator.mean(data), 55.0);
    }

    @Test
    public void testOneHundredElementsArray() {
        double[] data = IntStream.rangeClosed(1, 100).mapToDouble(i -> (double) i).toArray();

        // 100 elements: p50 index 49 (50.0), p90 index 89 (90.0), p95 index 94 (95.0), p99 index 98 (99.0)
        assertEquals(PercentileCalculator.p50(data), 50.0);
        assertEquals(PercentileCalculator.p90(data), 90.0);
        assertEquals(PercentileCalculator.p95(data), 95.0);
        assertEquals(PercentileCalculator.p99(data), 99.0);
        assertEquals(PercentileCalculator.min(data), 1.0);
        assertEquals(PercentileCalculator.max(data), 100.0);
        assertEquals(PercentileCalculator.mean(data), 50.5);
    }

    @Test
    public void testOddLengthArray() {
        double[] data = new double[] { 10, 20, 30, 40, 50 };

        // p50: ceil(0.50 * 5) = 3 -> index 2 -> 30.0
        assertEquals(PercentileCalculator.p50(data), 30.0);
        // p20: ceil(0.20 * 5) = 1 -> index 0 -> 10.0
        assertEquals(PercentileCalculator.percentile(data, 20), 10.0);
        // p80: ceil(0.80 * 5) = 4 -> index 3 -> 40.0
        assertEquals(PercentileCalculator.percentile(data, 80), 40.0);
    }

    @Test
    public void testBoundaryPercentiles() {
        double[] data = new double[] { 10, 20, 30 };
        assertEquals(PercentileCalculator.percentile(data, 0), 10.0);
        assertEquals(PercentileCalculator.percentile(data, 100), 30.0);
    }

    @Test
    public void testLinearInterpolation() {
        double[] data = new double[] { 10.0, 20.0 };
        // rank = 0.5 * 1 = 0.5 -> 10 + 0.5 * 10 = 15.0
        assertEquals(PercentileCalculator.linearInterpolation(data, 50), 15.0);

        double[] three = new double[] { 10.0, 20.0, 30.0 };
        // rank = 0.5 * 2 = 1.0 -> 20.0
        assertEquals(PercentileCalculator.linearInterpolation(three, 50), 20.0);
        // rank = 0.25 * 2 = 0.5 -> 10 + 0.5 * 10 = 15.0
        assertEquals(PercentileCalculator.linearInterpolation(three, 25), 15.0);
    }
}
