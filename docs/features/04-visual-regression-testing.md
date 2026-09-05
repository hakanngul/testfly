# Feature Plan: Built-in Visual Regression & Pixel Diffing Engine

- **Feature Name**: `testfly-visual-regression`
- **Target Version**: `v1.2.0`
- **Status**: Proposed / Architecture Design
- **Author**: TestFly Core Team

---

## 1. Executive Summary

Functional assertions verify data correctness (e.g. `assertThat(element).hasText("Submit")`), but they cannot detect visual regressions—such as broken CSS layouts, overlapping buttons, misaligned headers, or unintended font shifts.

While Playwright provides native `expect(page).toHaveScreenshot()`, the Java/Selenium ecosystem has historically required either expensive SaaS subscriptions (e.g., Applitools, Percy) or unmaintained legacy libraries (e.g., AShot).

The **TestFly Visual Regression Engine** is a zero-dependency, framework-native visual comparison engine built into `TestFlyAssertions`. It enables pixel-accurate visual snapshot testing for full pages and individual elements, featuring automated baseline management, dynamic element masking, antialiasing tolerance, and an interactive side-by-side diff slider in TestFly HTML reports.

---

## 2. Motivation & Problem Statement

### The Problem Today
1. **Broken UI Escapes to Production**: A CSS refactoring can inadvertently break mobile layouts, shift buttons off-screen, or distort brand styling while all traditional functional tests still pass.
2. **Expensive SaaS Vendor Lock-In**: Cloud visual testing providers charge steep monthly fees per snapshot, which is cost-prohibitive for large open-source and enterprise test suites.
3. **Flaky Pixel Diffing Across Operating Systems**: Minor font antialiasing differences between macOS, Windows, and Linux CI runners frequently trigger false positive visual failures unless antialiasing smoothing is built-in.

### The Solution: Native Java Visual Assertions
A clean, expressive assertion integrated directly into TestFly's assertion engine:
```java
// Assert visual state of a specific component
assertThat(find(".header-nav")).matchesVisualSnapshot("header-navigation");

// Full-page snapshot with dynamic masking
assertThatPage().matchesVisualSnapshot("checkout-summary", VisualDiffOptions.builder()
    .mask(find(".live-timestamp"))
    .mask(find(".user-avatar"))
    .maxDiffRatio(0.01) // Allow up to 1% pixel difference
    .build());
```

---

## 3. Architecture & Technical Design

```
+-----------------------------------------------------------------------------------+
|                        assertThat(element).matchesVisualSnapshot()                |
+-----------------------------------------------------------------------------------+
                                          |
                                          v
+-----------------------------------------------------------------------------------+
|               io.testfly.visual.VisualSnapshotEngine                              |
|  1. Captures Target Element / Viewport Screenshot via Selenium WebDriver          |
|  2. Masks Dynamic Elements (fills masked bounding boxes with neutral color)        |
|  3. Loads Baseline Image from `src/test/resources/visual-baselines/{name}.png`   |
+-----------------------------------------------------------------------------------+
                                          |
                        +-----------------+-----------------+
                        |                                   |
                        v (Baseline Exists)                 v (Baseline Missing or Update Mode)
+-----------------------------------------------+ +---------------------------------+
|          Fast Java Pixel Diff Engine          | |      Baseline Generator         |
| - Fast YIQ color-difference calculation       | | - Saves image as new golden     |
| - Antialiasing filter (3x3 neighborhood check)| |   baseline                      |
| - Calculates mismatch ratio %                 | | - Flags test as Passed / Created|
+-----------------------------------------------+ +---------------------------------+
                        |
                        +-----------------+
                                          |
                        +-----------------+-----------------+
                        |                                   |
                        v (Diff <= Threshold)               v (Diff > Threshold)
                 [Test Passes]                       [VisualMismatchException]
                                                     - Generates:
                                                       * {name}-actual.png
                                                       * {name}-expected.png
                                                       * {name}-diff.png (magenta highlight)
                                                     - Embeds interactive slider in HTML Report
```

### Core Components

1. **`io.testfly.visual.VisualDiffEngine`**:
   - High-performance pure-Java pixel-by-pixel comparison using standard `java.awt.image.BufferedImage`.
   - Utilizes the **YIQ color metric** (human perceptual color difference) to avoid false positives on subtle color banding.
   - Built-in **antialiasing detection**: scans 3x3 surrounding pixel matrix to ignore single-pixel font rasterization discrepancies across macOS / Linux CI.

2. **`io.testfly.visual.VisualDiffOptions`**:
   - Fluent configuration for fine-tuning visual sensitivity:
     - `mask(ElementLocator... locators)`: Redacts dynamic content (clocks, avatars, random test IDs).
     - `maxDiffRatio(double ratio)`: Allowed difference percentage (e.g. `0.005` = 0.5%).
     - `pixelTolerance(int threshold)`: Color sensitivity threshold (0-255).
     - `hideScrollbars(boolean hide)`: Automatically hides browser scrollbars via injected CSS during capture.

3. **`io.testfly.visual.BaselineManager`**:
   - Resolves golden images under `src/test/resources/visual-baselines/{browser}-{platform}/{name}.png`.
   - Supports `-DupdateBaselines=true` CLI flag to mass-update all visual snapshots when redesigns are intentional.

4. **`io.testfly.reporter.VisualDiffHtmlComponent`**:
   - Embeds an interactive before/after split slider in the TestFly HTML report so reviewers can visually inspect the exact pixel diff.

---

## 4. User-Facing Configuration & API

### Configuration (`testfly.yml`)
```yaml
visual:
  baselineDir: "src/test/resources/visual-baselines"
  diffOutputDir: "target/testfly-visual-diffs"
  defaultMaxDiffRatio: 0.005      # 0.5% default tolerance
  autoCreateBaselines: true       # Creates baseline if not found on first run
  antialiasingTolerance: true     # Filters sub-pixel OS font differences
  hideScrollbars: true
```

### Code Examples

#### 1. Component-Level Visual Regression
```java
public class HeaderVisualTest extends BaseTest {

    @Test
    public void verifyHeaderNavigationLayout() {
        open("/dashboard");
        
        // Verifies only the header bar pixel layout
        assertThat(find("#navbar-main"))
            .matchesVisualSnapshot("navbar-desktop");
    }
}
```

#### 2. Masking Dynamic Advertisements & Clocks
```java
@Test
public void verifyLandingPageWithMasking() {
    open("/");

    assertThatPage().matchesVisualSnapshot("landing-page", VisualDiffOptions.builder()
        .mask(find(".live-ticker"))
        .mask(find("#ad-slot-top"))
        .maxDiffRatio(0.01)
        .build());
}
```

#### 3. Updating Baselines via CLI
When an intentional design change occurs, developers run:
```bash
mvn test -Dtest=*VisualTest -DupdateBaselines=true
```
This updates all `.png` baselines in `src/test/resources/visual-baselines/` ready to be committed to git.

---

## 5. HTML Report Visual Diff Viewer

When a visual test fails, the TestFly HTML report displays an interactive component:
- **Side-by-Side Mode**: Expected baseline image on left, actual captured image on right.
- **Split Slider Mode**: A draggable vertical divider allowing users to slide between before & after.
- **Diff Highlight Mode**: Mismatched pixels highlighted in bright magenta/red overlay with mismatch stats (`142 pixels (0.12%) changed`).

---

## 6. Phased Implementation Plan

### Phase 1: Pure Java Pixel Diff & Baseline Manager (Sprint 1)
- [ ] Create `io.testfly.visual` package.
- [ ] Implement `PixelDiffUtil` using `BufferedImage` with YIQ perceptual diff algorithm.
- [ ] Implement `BaselineManager` with auto-save and `-DupdateBaselines=true` support.
- [ ] Add `matchesVisualSnapshot(name)` to `ElementAssert` and `PageAssert`.

### Phase 2: Dynamic Element Masking & Antialiasing Filter (Sprint 2)
- [ ] Implement `VisualDiffOptions` builder with `mask()` support.
- [ ] Apply canvas masking by painting neutral grey bounding rectangles over masked elements before diffing.
- [ ] Implement 3x3 kernel antialiasing filter to prevent cross-OS CI font false positives.

### Phase 3: HTML Report Split Slider & Allure Attachment (Sprint 3)
- [ ] Embed interactive image slider component into `HtmlReportGenerator`.
- [ ] Attach `diff.png`, `actual.png`, and `expected.png` to Allure / TestNG reporter outputs.
- [ ] Add `visual_diff` diagnostic tool to `testfly-mcp`.

---

## 7. Performance & Optimization

- **Memory Efficiency**: Diff calculations are performed on raw byte arrays / int arrays without allocating redundant full-sized bitmap copies in memory.
- **Fast Execution**: A 1920x1080 pixel full-page comparison completes in <45ms on modern JVMs.
- **Storage Strategy**: Baseline images are saved as optimized PNG files with zero lossy compression.
