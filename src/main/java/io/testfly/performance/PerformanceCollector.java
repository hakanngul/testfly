package io.testfly.performance;

import io.testfly.driver.DriverManager;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

import java.util.Map;

/**
 * Collects Core Web Vitals and Navigation Timing metrics from the active
 * browser page
 * by executing the {@code window.performance} API via JavaScript.
 *
 * <p>
 * No external dependencies — uses browser-native APIs only:
 * <ul>
 * <li><b>Navigation Timing Level 2</b> — TTFB, DOMContentLoaded, page load</li>
 * <li><b>Paint Timing</b> — First Paint, First Contentful Paint</li>
 * <li><b>Largest Contentful Paint</b> — Chrome/Edge only (buffered
 * entries)</li>
 * <li><b>Layout Instability</b> — CLS — Chrome/Edge only (buffered
 * entries)</li>
 * </ul>
 *
 * <h3>Full page loads</h3>
 * <p>
 * Call {@link #collect()} after {@code open()} once the page has fully loaded.
 * Metrics requiring PerformanceObserver (LCP, CLS) are read from the browser's
 * buffered entry queue — no pre-injection needed.
 *
 * <h3>SPA route transitions</h3>
 * <p>
 * For single-page applications (React Router, Angular Router, Vue Router),
 * the Navigation Timing API reports metrics only for the initial full page
 * load.
 * Use the SPA-aware strategy to capture metrics scoped to a route transition:
 *
 * <pre>
 * // 1. Mark the start before triggering the route change
 * PerformanceCollector.markSpaTransitionStart(driver);
 *
 * // 2. Perform the route navigation (click a link, pushState, etc.)
 * driver.findElement(By.css("[data-route='dashboard']")).click();
 *
 * // 3. Wait for the new route to finish rendering, then collect
 * PerformanceMetrics spaMetrics = PerformanceCollector.collectSpaTransition();
 * </pre>
 *
 * <p>
 * {@link #collectSpaTransition()} measures the transition duration via
 * {@code performance.measure()} and scopes LCP/CLS to entries that occurred
 * after the start mark. Navigation Timing fields (TTFB, domLoad, pageLoad)
 * are set to {@code -1} since they are not meaningful for SPA transitions.
 */
public final class PerformanceCollector {

    /**
     * JavaScript that first registers buffered PerformanceObservers for LCP and CLS
     * (which require an active observer to appear in the timeline), then reads all
     * available performance entries and returns a plain object.
     */
    private static final String COLLECT_JS = "(function() {" +
            "  var m = { lcp:-1, fcp:-1, fp:-1, ttfb:-1, cls:-1, domLoad:-1, pageLoad:-1 };" +

            // Navigation Timing Level 2 (all modern browsers)
            "  var nav = performance.getEntriesByType('navigation');" +
            "  if (nav && nav.length > 0) {" +
            "    var n = nav[0];" +
            "    m.ttfb     = n.responseStart > 0 ? n.responseStart : -1;" +
            "    m.domLoad  = n.domContentLoadedEventEnd > 0 ? n.domContentLoadedEventEnd : -1;" +
            "    m.pageLoad = n.loadEventEnd > 0 ? n.loadEventEnd : -1;" +
            "  }" +

            // Paint Timing (Chrome, Firefox, Edge — not Safari < 14.5)
            "  var paints = performance.getEntriesByType('paint');" +
            "  for (var i = 0; i < paints.length; i++) {" +
            "    if (paints[i].name === 'first-paint') m.fp = paints[i].startTime;" +
            "    if (paints[i].name === 'first-contentful-paint') m.fcp = paints[i].startTime;" +
            "  }" +

            // Largest Contentful Paint — requires PerformanceObserver with buffered:true
            // (Chrome/Edge)
            "  if (typeof PerformanceObserver !== 'undefined') {" +
            "    try {" +
            "      var lcpEntries = [];" +
            "      var lcpDone = false;" +
            "      new PerformanceObserver(function(list) {" +
            "        var entries = list.getEntries();" +
            "        for (var i = 0; i < entries.length; i++) lcpEntries.push(entries[i]);" +
            "      }).observe({ type: 'largest-contentful-paint', buffered: true });" +
            "      var lcpList = performance.getEntriesByType('largest-contentful-paint');" +
            "      if (lcpList && lcpList.length > 0) {" +
            "        m.lcp = lcpList[lcpList.length - 1].startTime;" +
            "      } else if (lcpEntries.length > 0) {" +
            "        m.lcp = lcpEntries[lcpEntries.length - 1].startTime;" +
            "      }" +
            "    } catch(e) { /* LCP not supported */ }" +

            // Cumulative Layout Shift — requires PerformanceObserver with buffered:true
            // (Chrome/Edge)
            "    try {" +
            "      var clsEntries = [];" +
            "      new PerformanceObserver(function(list) {" +
            "        var entries = list.getEntries();" +
            "        for (var i = 0; i < entries.length; i++) clsEntries.push(entries[i]);" +
            "      }).observe({ type: 'layout-shift', buffered: true });" +
            "      var shiftList = performance.getEntriesByType('layout-shift');" +
            "      var shifts = (shiftList && shiftList.length > 0) ? shiftList : clsEntries;" +
            "      if (shifts.length > 0) {" +
            "        var cls = 0;" +
            "        for (var j = 0; j < shifts.length; j++) {" +
            "          if (!shifts[j].hadRecentInput) cls += shifts[j].value;" +
            "        }" +
            "        m.cls = cls;" +
            "      }" +
            "    } catch(e) { /* CLS not supported */ }" +
            "  }" +

            "  return m;" +
            "})();";

    /**
     * JavaScript that marks the end of a SPA route transition, measures the
     * duration between the start and end marks, and collects LCP/CLS entries
     * scoped to the transition window (entries after the start mark).
     */
    private static final String COLLECT_SPA_JS = "(function() {" +
            "  var m = { lcp:-1, fcp:-1, fp:-1, ttfb:-1, cls:-1, domLoad:-1, pageLoad:-1, transitionDuration:-1 };" +

            // Verify the start mark exists
            "  var startMark = performance.getEntriesByName('testfly-route-start', 'mark');" +
            "  if (!startMark || startMark.length === 0) return m;" +
            "  var startTs = startMark[0].startTime;" +

            // Mark the end and measure duration
            "  performance.mark('testfly-route-end');" +
            "  try {" +
            "    performance.measure('testfly-route-transition', 'testfly-route-start', 'testfly-route-end');" +
            "    var measures = performance.getEntriesByName('testfly-route-transition', 'measure');" +
            "    if (measures.length > 0) m.transitionDuration = measures[measures.length - 1].duration;" +
            "  } catch(e) { /* measure failed */ }" +

            // LCP scoped to the transition (entries after the start mark)
            "  if (typeof PerformanceObserver !== 'undefined') {" +
            "    try {" +
            "      var lcpAll = [];" +
            "      new PerformanceObserver(function(list) {" +
            "        var entries = list.getEntries();" +
            "        for (var i = 0; i < entries.length; i++) lcpAll.push(entries[i]);" +
            "      }).observe({ type: 'largest-contentful-paint', buffered: true });" +
            "      var lcpList = performance.getEntriesByType('largest-contentful-paint');" +
            "      var lcpSource = (lcpList && lcpList.length > 0) ? lcpList : lcpAll;" +
            "      for (var i = lcpSource.length - 1; i >= 0; i--) {" +
            "        if (lcpSource[i].startTime >= startTs) { m.lcp = lcpSource[i].startTime - startTs; break; }" +
            "      }" +
            "    } catch(e) { /* LCP not supported */ }" +

            // CLS scoped to the transition (layout shifts after the start mark, excluding
            // input)
            "    try {" +
            "      var clsAll = [];" +
            "      new PerformanceObserver(function(list) {" +
            "        var entries = list.getEntries();" +
            "        for (var i = 0; i < entries.length; i++) clsAll.push(entries[i]);" +
            "      }).observe({ type: 'layout-shift', buffered: true });" +
            "      var shiftList = performance.getEntriesByType('layout-shift');" +
            "      var shifts = (shiftList && shiftList.length > 0) ? shiftList : clsAll;" +
            "      var cls = 0;" +
            "      for (var j = 0; j < shifts.length; j++) {" +
            "        if (shifts[j].startTime >= startTs && !shifts[j].hadRecentInput) cls += shifts[j].value;" +
            "      }" +
            "      m.cls = cls;" +
            "    } catch(e) { /* CLS not supported */ }" +
            "  }" +

            // Clean up marks and measures to avoid polluting the timeline
            "  try {" +
            "    performance.clearMarks('testfly-route-start');" +
            "    performance.clearMarks('testfly-route-end');" +
            "    performance.clearMeasures('testfly-route-transition');" +
            "  } catch(e) { /* cleanup is best-effort */ }" +

            "  return m;" +
            "})();";

    /**
     * JavaScript that places a performance mark at the start of a SPA route
     * transition.
     */
    private static final String MARK_SPA_START_JS = "(function() {" +
            "  if (typeof performance.mark === 'function') {" +
            "    performance.clearMarks('testfly-route-start');" +
            "    performance.mark('testfly-route-start');" +
            "    return true;" +
            "  }" +
            "  return false;" +
            "})();";

    private PerformanceCollector() {
    }

    /**
     * Collects Core Web Vitals from the currently loaded page.
     *
     * <p>
     * Call this after {@code open()} and after any SPA transitions have settled.
     * For single-page applications, navigate to the target route first, then
     * collect.
     *
     * @throws IllegalStateException         if no active WebDriver session exists
     * @throws UnsupportedOperationException if the browser does not support
     *                                       JavaScript execution
     */
    public static PerformanceMetrics collect() {
        WebDriver driver = DriverManager.getDriver();
        if (driver == null) {
            throw new IllegalStateException(
                    "[Performance] No active WebDriver. Call collect() after open().");
        }
        if (!(driver instanceof JavascriptExecutor)) {
            throw new UnsupportedOperationException(
                    "[Performance] Browser does not support JavaScript execution.");
        }

        Object result = ((JavascriptExecutor) driver).executeScript(COLLECT_JS);
        return parse(result);
    }

    /**
     * Marks the start of a SPA route transition.
     *
     * <p>
     * Call this <b>before</b> triggering a client-side navigation (e.g., clicking
     * a router link or calling {@code history.pushState}). Then call
     * {@link #collectSpaTransition()} after the new route has finished rendering.
     *
     * <p>
     * If the browser does not support {@code performance.mark()}, this method
     * returns {@code false} silently — no exception is thrown.
     *
     * @param driver the active WebDriver instance
     * @return {@code true} if the mark was placed successfully, {@code false} if
     *         the browser does not support the Performance Mark API
     * @throws UnsupportedOperationException if the browser does not support
     *                                       JavaScript execution
     */
    public static boolean markSpaTransitionStart(WebDriver driver) {
        if (!(driver instanceof JavascriptExecutor)) {
            throw new UnsupportedOperationException(
                    "[Performance] Browser does not support JavaScript execution.");
        }
        Object result = ((JavascriptExecutor) driver).executeScript(MARK_SPA_START_JS);
        return Boolean.TRUE.equals(result);
    }

    /**
     * Collects performance metrics scoped to a SPA route transition.
     *
     * <p>
     * This method:
     * <ol>
     * <li>Marks the end of the transition ({@code testfly-route-end})</li>
     * <li>Measures the duration between the start and end marks via
     * {@code performance.measure()}</li>
     * <li>Collects LCP and CLS entries that occurred <b>after</b> the start mark,
     * giving values scoped to the transition rather than cumulative from page
     * load</li>
     * <li>Cleans up the marks and measures from the performance timeline</li>
     * </ol>
     *
     * <p>
     * Navigation Timing fields (TTFB, domLoad, pageLoad) are set to {@code -1}
     * because they only reflect the initial full page load and are not meaningful
     * for SPA transitions.
     *
     * <p>
     * If {@link #markSpaTransitionStart(WebDriver)} was not called before the
     * transition, the returned metrics will have all values set to {@code -1}.
     *
     * @return a {@link PerformanceMetrics} with SPA-scoped LCP, CLS, and
     *         {@code transitionDuration}; Navigation Timing fields are {@code -1}
     * @throws IllegalStateException         if no active WebDriver session exists
     * @throws UnsupportedOperationException if the browser does not support
     *                                       JavaScript execution
     */
    public static PerformanceMetrics collectSpaTransition() {
        WebDriver driver = DriverManager.getDriver();
        if (driver == null) {
            throw new IllegalStateException(
                    "[Performance] No active WebDriver. Call collectSpaTransition() after open().");
        }
        if (!(driver instanceof JavascriptExecutor)) {
            throw new UnsupportedOperationException(
                    "[Performance] Browser does not support JavaScript execution.");
        }

        Object result = ((JavascriptExecutor) driver).executeScript(COLLECT_SPA_JS);
        return parseSpa(result);
    }

    @SuppressWarnings("unchecked")
    private static PerformanceMetrics parse(Object raw) {
        if (!(raw instanceof Map)) {
            return unavailable();
        }
        Map<String, Object> m = (Map<String, Object>) raw;
        return new PerformanceMetrics(
                toDouble(m.get("lcp")),
                toDouble(m.get("fcp")),
                toDouble(m.get("fp")),
                toDouble(m.get("ttfb")),
                toDouble(m.get("cls")),
                toDouble(m.get("domLoad")),
                toDouble(m.get("pageLoad")));
    }

    @SuppressWarnings("unchecked")
    private static PerformanceMetrics parseSpa(Object raw) {
        if (!(raw instanceof Map)) {
            return unavailable();
        }
        Map<String, Object> m = (Map<String, Object>) raw;
        return new PerformanceMetrics(
                toDouble(m.get("lcp")),
                toDouble(m.get("fcp")),
                toDouble(m.get("fp")),
                toDouble(m.get("ttfb")),
                toDouble(m.get("cls")),
                toDouble(m.get("domLoad")),
                toDouble(m.get("pageLoad")),
                toDouble(m.get("transitionDuration")));
    }

    private static double toDouble(Object value) {
        if (value == null)
            return PerformanceMetrics.NOT_AVAILABLE;
        if (value instanceof Number)
            return ((Number) value).doubleValue();
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return PerformanceMetrics.NOT_AVAILABLE;
        }
    }

    private static PerformanceMetrics unavailable() {
        double na = PerformanceMetrics.NOT_AVAILABLE;
        return new PerformanceMetrics(na, na, na, na, na, na, na);
    }
}
