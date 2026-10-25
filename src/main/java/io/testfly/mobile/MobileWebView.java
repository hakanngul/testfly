package io.testfly.mobile;

import io.testfly.mobile.internal.MobileSession;

import java.util.List;
import java.util.Map;
import java.util.Objects;

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
     * Runs {@code body} as the body of a function called with {@code args} ({@code arguments[0]}, ...),
     * e.g. {@code evaluate("return document.querySelector(arguments[0]).value", "#email")}.
     */
    public Object evaluate(String body, Object... args) {
        return call("evaluate", Map.of("expression", body, "args", java.util.Arrays.asList(args)));
    }

    // ── locators (the page DOM) ─────────────────────────────────────────

    /** Elements matching a CSS selector. */
    public MobileWebViewLocator locator(String css) {
        return new MobileWebViewLocator(this, session, Objects.requireNonNull(css, "css"), null, "locator(\"" + css + "\")");
    }

    /** Elements whose {@code data-testid} equals {@code testId}. */
    public MobileWebViewLocator getByTestId(String testId) {
        return new MobileWebViewLocator(this, session, "[data-testid=\"" + cssString(testId) + "\"]", null, "getByTestId(\"" + testId + "\")");
    }

    /** The innermost elements whose text (or {@code aria-label}) equals {@code text}, whitespace normalized. */
    public MobileWebViewLocator getByText(String text) {
        return new MobileWebViewLocator(this, session, "body *", Objects.requireNonNull(text, "text"), "getByText(\"" + text + "\")");
    }

    /** Elements of an ARIA role ({@code button}, {@code link}, {@code textbox}, {@code checkbox}, {@code heading}, ...). */
    public MobileWebViewLocator getByRole(String role) {
        return new MobileWebViewLocator(this, session, roleCss(role), null, "getByRole(" + role + ")");
    }

    /** Elements of an ARIA role whose name (text, {@code aria-label} or placeholder) equals {@code name}. */
    public MobileWebViewLocator getByRole(String role, String name) {
        return new MobileWebViewLocator(this, session, roleCss(role), Objects.requireNonNull(name, "name"),
                "getByRole(" + role + ", name=\"" + name + "\")");
    }

    private static String roleCss(String role) {
        return switch (Objects.requireNonNull(role, "role")) {
            case "button" -> "button,[role=button],input[type=button],input[type=submit],input[type=reset]";
            case "link" -> "a[href],[role=link]";
            case "textbox" -> "input:not([type]),input[type=text],input[type=email],input[type=password],input[type=search],"
                    + "input[type=tel],input[type=url],input[type=number],textarea,[role=textbox],[contenteditable=true]";
            case "checkbox" -> "input[type=checkbox],[role=checkbox]";
            case "radio" -> "input[type=radio],[role=radio]";
            case "heading" -> "h1,h2,h3,h4,h5,h6,[role=heading]";
            case "img", "image" -> "img,[role=img]";
            case "combobox" -> "select,[role=combobox]";
            default -> "[role=\"" + cssString(role) + "\"]";
        };
    }

    private static String cssString(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
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
