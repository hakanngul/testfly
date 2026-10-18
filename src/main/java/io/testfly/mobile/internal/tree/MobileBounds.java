package io.testfly.mobile.internal.tree;

/**
 * Adapter-normalized bounds in an adapter's coordinate space; no screen scaling is implied.
 * Negative origins and zero sizes are valid, but dimensions cannot be negative.
 */
public record MobileBounds(double x, double y, double width, double height) {

    public MobileBounds {
        if (!Double.isFinite(x) || !Double.isFinite(y)
                || !Double.isFinite(width) || !Double.isFinite(height)
                || !Double.isFinite(x + width) || !Double.isFinite(y + height)
                || width < 0 || height < 0) {
            throw new IllegalArgumentException("Bounds require finite coordinates and non-negative dimensions");
        }
    }
}
