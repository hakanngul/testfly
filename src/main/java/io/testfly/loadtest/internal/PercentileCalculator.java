package io.testfly.loadtest.internal;

/**
 * Utility for calculating statistical metrics (percentiles, mean, min, max)
 * from arrays of latency measurements.
 *
 * <p>Supports the standard nearest-rank method for percentiles (p50, p90, p95, p99)
 * as well as linear interpolation.
 */
public final class PercentileCalculator {

    private PercentileCalculator() {
    }

    /**
     * Calculates the p-th percentile using the nearest-rank method.
     *
     * <p>Formula: \(k = \lceil \frac{p}{100} \times N \rceil\), with index clamped to \([0, N-1]\).
     *
     * @param sorted array of values pre-sorted in ascending order
     * @param p percentile rank between 0.0 and 100.0
     * @return the calculated percentile value, or 0.0 if array is empty
     */
    public static double percentile(double[] sorted, double p) {
        return nearestRank(sorted, p);
    }

    /**
     * Calculates the p-th percentile using the nearest-rank method.
     */
    public static double nearestRank(double[] sorted, double p) {
        if (sorted == null || sorted.length == 0) {
            return 0.0;
        }
        if (sorted.length == 1) {
            return sorted[0];
        }
        int k = (int) Math.ceil((p / 100.0) * sorted.length);
        int index = Math.max(0, Math.min(k - 1, sorted.length - 1));
        return sorted[index];
    }

    /** Calculates the 50th percentile (median) using nearest rank. */
    public static double p50(double[] sorted) {
        return percentile(sorted, 50.0);
    }

    /** Calculates the 90th percentile using nearest rank. */
    public static double p90(double[] sorted) {
        return percentile(sorted, 90.0);
    }

    /** Calculates the 95th percentile using nearest rank. */
    public static double p95(double[] sorted) {
        return percentile(sorted, 95.0);
    }

    /** Calculates the 99th percentile using nearest rank. */
    public static double p99(double[] sorted) {
        return percentile(sorted, 99.0);
    }

    /**
     * Calculates the arithmetic mean of the given values.
     *
     * @param values array of values
     * @return mean value, or 0.0 if array is empty
     */
    public static double mean(double[] values) {
        if (values == null || values.length == 0) {
            return 0.0;
        }
        double sum = 0;
        for (double v : values) {
            sum += v;
        }
        return sum / values.length;
    }

    /**
     * Returns the minimum value from a sorted array.
     *
     * @param sorted array of values sorted in ascending order
     * @return minimum value, or 0.0 if array is empty
     */
    public static double min(double[] sorted) {
        if (sorted == null || sorted.length == 0) {
            return 0.0;
        }
        return sorted[0];
    }

    /**
     * Returns the maximum value from a sorted array.
     *
     * @param sorted array of values sorted in ascending order
     * @return maximum value, or 0.0 if array is empty
     */
    public static double max(double[] sorted) {
        if (sorted == null || sorted.length == 0) {
            return 0.0;
        }
        return sorted[sorted.length - 1];
    }

    /**
     * Calculates the percentile using linear interpolation between closest ranks.
     *
     * @param sorted array of values sorted in ascending order
     * @param p percentile rank between 0.0 and 100.0
     * @return interpolated percentile, or 0.0 if array is empty
     */
    public static double linearInterpolation(double[] sorted, double p) {
        if (sorted == null || sorted.length == 0) {
            return 0.0;
        }
        if (sorted.length == 1) {
            return sorted[0];
        }
        double rank = (p / 100.0) * (sorted.length - 1);
        int lower = (int) Math.floor(rank);
        int upper = (int) Math.ceil(rank);
        if (lower == upper) {
            return sorted[lower];
        }
        double fraction = rank - lower;
        return sorted[lower] + fraction * (sorted[upper] - sorted[lower]);
    }
}
