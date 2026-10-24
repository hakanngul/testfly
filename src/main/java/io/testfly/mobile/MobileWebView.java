package io.testfly.mobile;

import io.testfly.mobile.internal.MobileSession;

import java.util.List;
import java.util.Map;

/**
 * An embedded webview ({@code WKWebView}, {@code android.webkit.WebView}) of the app in front, or a
 * visible Chrome/Safari tab, driven through mobilecli's webview commands. {@link #id()}, {@link #url()}
 * and {@link #title()} as listed by {@link MobileDevice#webViews()}; the other methods ask the device.
 */
public final class MobileWebView {

    private final MobileSession session;
    private final String id;
    private final String listedUrl;
    private final String listedTitle;
    private final boolean visible;

    MobileWebView(MobileSession session, String id, String url, String title, boolean visible) {
        this.session = session;
        this.id = id;
        this.listedUrl = url;
        this.listedTitle = title;
        this.visible = visible;
    }

    public String id() {
        return id;
    }

    /** Whether the webview is on screen; only a visible Chrome/Safari tab answers scripts. */
    public boolean isVisible() {
        return visible;
    }

    /** The current URL (asked from the page). */
    public String url() {
        return text(call("url", null));
    }

    /** The current page title (asked from the page). */
    public String title() {
        return text(call("title", null));
    }

    /** The page HTML. */
    public String content() {
        return text(call("content", null));
    }

    /**
     * Runs JavaScript in the page and returns its result as plain Java values ({@code Map}, {@code List},
     * {@code String}, numbers, booleans) or {@code null}.
     */
    public Object evaluate(String expression) {
        return call("evaluate", Map.of("expression", expression));
    }

    /**
     * Elements matching a CSS selector, each as a map with {@code tag}, {@code text}, {@code id},
     * {@code class}, {@code value} and {@code href}.
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> query(String cssSelector) {
        Object result = call("query", Map.of("selector", cssSelector));
        return result instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }

    public void navigate(String url) {
        call("goto", Map.of("url", url));
    }

    public void reload() {
        call("reload", null);
    }

    public void goBack() {
        call("goBack", null);
    }

    public void goForward() {
        call("goForward", null);
    }

    /** Waits until the page has loaded ({@code load} event), within the action timeout. */
    public void waitForLoad() {
        call("waitForLoadState", Map.of("state", "load", "timeout", session.actionTimeout().toMillis()));
    }

    private Object call(String operation, Map<String, Object> params) {
        return session.driver().webView(operation, id, params, session.rpcTimeout());
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    @Override
    public String toString() {
        return "MobileWebView(" + id + ", " + listedUrl + ", \"" + listedTitle + "\")";
    }
}
