package io.testfly.unit;

import io.testfly.visual.VisualAssert;
import io.testfly.visual.VisualAssert.DiffResult;
import io.testfly.visual.VisualTolerance;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Unit tests for {@link VisualAssert} and {@link VisualTolerance}.
 * All tests are pure Java — no browser required.
 */
public class VisualAssertTest {

    private static final String[] UPDATE_PROPS = { "testfly.visual.updateBaselines", "updateBaselines" };
    private final String[] savedUpdateProps = new String[UPDATE_PROPS.length];

    // Comparison tests must not run in update mode, e.g. when invoked with -DupdateBaselines=true.
    @BeforeClass
    public void disableBaselineUpdates() {
        for (int i = 0; i < UPDATE_PROPS.length; i++) {
            savedUpdateProps[i] = System.getProperty(UPDATE_PROPS[i]);
            System.setProperty(UPDATE_PROPS[i], "false");
        }
    }

    @AfterClass(alwaysRun = true)
    public void restoreBaselineUpdates() {
        for (int i = 0; i < UPDATE_PROPS.length; i++) {
            if (savedUpdateProps[i] == null) {
                System.clearProperty(UPDATE_PROPS[i]);
            } else {
                System.setProperty(UPDATE_PROPS[i], savedUpdateProps[i]);
            }
        }
    }

    // ------------------------------------------------------------------
    // VisualTolerance
    // ------------------------------------------------------------------

    @Test
    public void tolerance_exact_isZero() {
        Assert.assertEquals(VisualTolerance.exact().getPercent(), 0.0, 0.0001);
    }

    @Test
    public void tolerance_of_storesPercent() {
        Assert.assertEquals(VisualTolerance.of(5.5).getPercent(), 5.5, 0.0001);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void tolerance_negative_throws() {
        VisualTolerance.of(-1);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void tolerance_over100_throws() {
        VisualTolerance.of(101);
    }

    // ------------------------------------------------------------------
    // DiffResult
    // ------------------------------------------------------------------

    @Test
    public void diffResult_zeroPixels_isZeroPercent() {
        BufferedImage img = solidImage(10, 10, Color.WHITE);
        DiffResult r = new DiffResult(0, 100, img);
        Assert.assertEquals(r.diffPercent(), 0.0, 0.0001);
    }

    @Test
    public void diffResult_halfDiff_is50Percent() {
        BufferedImage img = solidImage(10, 10, Color.RED);
        DiffResult r = new DiffResult(50, 100, img);
        Assert.assertEquals(r.diffPercent(), 50.0, 0.0001);
    }

    @Test
    public void diffResult_totalZero_returnsZero() {
        BufferedImage img = solidImage(1, 1, Color.WHITE);
        DiffResult r = new DiffResult(0, 0, img);
        Assert.assertEquals(r.diffPercent(), 0.0, 0.0001);
    }

    // ------------------------------------------------------------------
    // diff() — identical images
    // ------------------------------------------------------------------

    @Test
    public void diff_identicalImages_zeroDiff() {
        BufferedImage a = solidImage(20, 20, Color.BLUE);
        BufferedImage b = solidImage(20, 20, Color.BLUE);
        DiffResult r = VisualAssert.diff(a, b);
        Assert.assertEquals(r.diffPixels(), 0L);
        Assert.assertEquals(r.diffPercent(), 0.0, 0.0001);
    }

    @Test
    public void diff_completelyDifferentImages_100Percent() {
        BufferedImage a = solidImage(10, 10, Color.WHITE);
        BufferedImage b = solidImage(10, 10, Color.BLACK);
        DiffResult r = VisualAssert.diff(a, b);
        Assert.assertEquals(r.totalPixels(), 100L);
        Assert.assertEquals(r.diffPixels(), 100L);
        Assert.assertEquals(r.diffPercent(), 100.0, 0.0001);
    }

    @Test
    public void diff_halfDifferent_50Percent() {
        int w = 10, h = 10;
        BufferedImage a = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        BufferedImage b = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        // Paint left half white in both, right half white vs black
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                a.setRGB(x, y, Color.WHITE.getRGB());
                b.setRGB(x, y, x < w / 2 ? Color.WHITE.getRGB() : Color.BLACK.getRGB());
            }
        }
        DiffResult r = VisualAssert.diff(a, b);
        Assert.assertEquals(r.diffPercent(), 50.0, 0.0001);
    }

    @Test
    public void diff_diffImageHasRedForDifferentPixels() {
        BufferedImage a = solidImage(4, 4, Color.WHITE);
        BufferedImage b = solidImage(4, 4, Color.BLACK);
        DiffResult r = VisualAssert.diff(a, b);
        // All pixels differ → all should be red in diff image
        BufferedImage diffImg = r.diffImage();
        Assert.assertEquals(new Color(diffImg.getRGB(0, 0)), Color.RED);
        Assert.assertEquals(new Color(diffImg.getRGB(3, 3)), Color.RED);
    }

    // ------------------------------------------------------------------
    // compare() — baseline creation / update / pass / fail
    // ------------------------------------------------------------------

    @Test
    public void compare_noBaseline_createsBaselineAndPasses() throws Exception {
        // Use the internal compare() method via a dedicated temp dir
        // Since baselineDir is config-driven we test the logic through a known path
        String name = "test_no_baseline_" + System.nanoTime();
        File baseline = new File("src/test/resources/baselines", name + ".png");
        try {
            BufferedImage img = solidImage(5, 5, Color.GREEN);
            // First call — no baseline; should save and not throw
            VisualAssert.compare(name, img, VisualTolerance.exact());
            Assert.assertTrue(baseline.exists(), "Baseline should have been saved");
        } finally {
            baseline.delete();
        }
    }

    @Test
    public void compare_sameImage_passes() throws Exception {
        String name = "test_same_" + System.nanoTime();
        File baseline = new File("src/test/resources/baselines", name + ".png");
        try {
            BufferedImage img = solidImage(8, 8, Color.CYAN);
            // Create baseline
            VisualAssert.compare(name, img, VisualTolerance.exact());
            // Second call with same image — should pass
            VisualAssert.compare(name, solidImage(8, 8, Color.CYAN), VisualTolerance.exact());
        } finally {
            baseline.delete();
        }
    }

    @Test
    public void compare_withinTolerance_passes() throws Exception {
        String name = "test_tolerance_" + System.nanoTime();
        File baseline = new File("src/test/resources/baselines", name + ".png");
        try {
            // 8x8 = 64 pixels; change 1 pixel → 1.5625% diff
            BufferedImage base = solidImage(8, 8, Color.CYAN);
            VisualAssert.compare(name, base, VisualTolerance.exact());
            BufferedImage current = solidImage(8, 8, Color.CYAN);
            current.setRGB(0, 0, Color.RED.getRGB()); // 1 pixel different
            // 2% tolerance — should pass
            VisualAssert.compare(name, current, VisualTolerance.of(2));
        } finally {
            baseline.delete();
        }
    }

    @Test(expectedExceptions = AssertionError.class)
    public void compare_exceedsTolerance_fails() throws Exception {
        String name = "test_fail_" + System.nanoTime();
        File baseline = new File("src/test/resources/baselines", name + ".png");
        File diffFile = new File("target/visual-diffs", name + "-diff.png");
        try {
            BufferedImage base = solidImage(10, 10, Color.WHITE);
            VisualAssert.compare(name, base, VisualTolerance.exact());
            // All pixels different — should fail
            VisualAssert.compare(name, solidImage(10, 10, Color.BLACK), VisualTolerance.exact());
        } finally {
            baseline.delete();
            diffFile.delete();
        }
    }

    @Test
    public void compare_differentAspectRatio_failsWithSizeMismatch() {
        String name = "test_aspect_" + System.nanoTime();
        File baseline = new File("src/test/resources/baselines", name + ".png");
        File actualFile = new File("target/visual-diffs", name + "-actual.png");
        try {
            VisualAssert.compare(name, solidImage(20, 10, Color.WHITE), VisualTolerance.exact());
            try {
                // Same content, different viewport shape — must not be stretched and diffed
                VisualAssert.compare(name, solidImage(30, 10, Color.WHITE), VisualTolerance.of(100));
                Assert.fail("Expected AssertionError for mismatched screenshot size");
            } catch (AssertionError e) {
                Assert.assertTrue(e.getMessage().contains("30x10"), e.getMessage());
                Assert.assertTrue(e.getMessage().contains("20x10"), e.getMessage());
                Assert.assertTrue(actualFile.exists(), "Current screenshot should be saved");
            }
        } finally {
            baseline.delete();
            actualFile.delete();
        }
    }

    @Test
    public void compare_sameAspectRatioScaled_passes() {
        String name = "test_scaled_" + System.nanoTime();
        File baseline = new File("src/test/resources/baselines", name + ".png");
        try {
            VisualAssert.compare(name, solidImage(20, 10, Color.CYAN), VisualTolerance.exact());
            // 2x device pixel ratio — same aspect ratio, scaled down before diffing
            VisualAssert.compare(name, solidImage(40, 20, Color.CYAN), VisualTolerance.exact());
        } finally {
            baseline.delete();
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static BufferedImage solidImage(int w, int h, Color color) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                img.setRGB(x, y, color.getRGB());
            }
        }
        return img;
    }
}
