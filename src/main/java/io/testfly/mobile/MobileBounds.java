package io.testfly.mobile;

/** Element bounds in device pixels, as reported by the view tree. */
public record MobileBounds(double x, double y, double width, double height) {

    public static final MobileBounds EMPTY = new MobileBounds(0, 0, 0, 0);

    public double centerX() {
        return x + width / 2;
    }

    public double centerY() {
        return y + height / 2;
    }

    public boolean isEmpty() {
        return width <= 0 || height <= 0;
    }

    /** True when {@code inner} lies fully inside these bounds. */
    public boolean contains(MobileBounds inner) {
        return inner.x >= x && inner.y >= y && inner.x + inner.width <= x + width && inner.y + inner.height <= y + height;
    }

    @Override
    public String toString() {
        return "[" + fmt(x) + "," + fmt(y) + " " + fmt(width) + "x" + fmt(height) + "]";
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? Long.toString((long) v) : Double.toString(v);
    }
}
