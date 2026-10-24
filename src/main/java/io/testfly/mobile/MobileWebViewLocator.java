package io.testfly.mobile;

import io.testfly.mobile.internal.MobileSession;

import java.util.Objects;

/**
 * Elements of a webview's DOM (incubating); create with {@link MobileWebView#locator(String)},
 * {@code getByTestId}, {@code getByText} or {@code getByRole}. Each call runs a short script in the
 * page through {@link MobileWebView#evaluate(String, Object...)}. Actions and reads wait (within
 * {@code mobile.timeouts.actionMs}) until exactly one visible element matches.
 */
public final class MobileWebViewLocator {

    /**
     * arguments: css, exact text/name or null, operation, operation argument. Text matching keeps the
     * innermost elements, so {@code getByText} does not also match their ancestors.
     */
    private static final String SCRIPT = """
            const [css, name, op, arg] = arguments;
            const norm = s => (s || '').replace(/\\s+/g, ' ').trim();
            let els = [...document.querySelectorAll(css)];
            if (name != null) {
              const want = norm(name);
              els = els.filter(e => [e.getAttribute('aria-label'), e.getAttribute('placeholder'), e.innerText ?? e.textContent,
                  e.tagName === 'INPUT' ? e.value : null].some(v => norm(v) === want));
              els = els.filter(e => !els.some(o => o !== e && e.contains(o)));
            }
            const visible = e => { const r = e.getBoundingClientRect(), s = getComputedStyle(e);
              return r.width > 0 && r.height > 0 && s.visibility !== 'hidden' && s.display !== 'none'; };
            if (op === 'count') return els.length;
            if (op === 'state') return els.length !== 1 ? 'matches:' + els.length : visible(els[0]) ? 'visible' : 'hidden';
            const e = els[0];
            switch (op) {
              case 'click': e.scrollIntoView({ block: 'center' }); e.click(); return true;
              case 'fill': {
                e.focus();
                const setter = Object.getOwnPropertyDescriptor(Object.getPrototypeOf(e), 'value');
                if (e.isContentEditable) e.textContent = arg;
                else if (setter && setter.set) setter.set.call(e, arg);
                else e.value = arg;
                e.dispatchEvent(new Event('input', { bubbles: true }));
                e.dispatchEvent(new Event('change', { bubbles: true }));
                return true;
              }
              case 'text': return norm(e.innerText ?? e.textContent);
              case 'value': return e.value ?? null;
              case 'attr': return e.getAttribute(arg);
            }
            return null;
            """;

    private final MobileWebView view;
    private final MobileSession session;
    private final String css;
    private final String name;
    private final String description;

    MobileWebViewLocator(MobileWebView view, MobileSession session, String css, String name, String description) {
        this.view = view;
        this.session = session;
        this.css = css;
        this.name = name;
        this.description = description;
    }

    /** Clicks the element (scrolled into view first). */
    public void click() {
        run("click", null);
    }

    /** Sets the field's value and fires {@code input} and {@code change} (works with React/Vue controlled inputs). */
    public void fill(String text) {
        run("fill", Objects.requireNonNull(text, "text"));
    }

    /** Waits for the element and returns its visible text, whitespace normalized. */
    public String getText() {
        Object v = run("text", null);
        return v == null ? "" : v.toString();
    }

    /** Waits for the element and returns its {@code value} ({@code null} when it has none). */
    public String getValue() {
        Object v = run("value", null);
        return v == null ? null : v.toString();
    }

    /** Waits for the element and returns an attribute ({@code null} when missing). */
    public String getAttribute(String attribute) {
        Object v = run("attr", Objects.requireNonNull(attribute, "attribute"));
        return v == null ? null : v.toString();
    }

    /** Number of matches right now (no waiting). */
    public int count() {
        Object v = eval("count", null);
        return v instanceof Number n ? n.intValue() : 0;
    }

    /** Whether exactly one match exists and it is visible (no waiting). */
    public boolean isVisible() {
        return "visible".equals(eval("state", null));
    }

    /** Waits until exactly one visible element matches. */
    public void waitFor() {
        long deadline = System.nanoTime() + session.actionTimeout().toNanos();
        Object state;
        while (!"visible".equals(state = eval("state", null))) {
            if (System.nanoTime() >= deadline) {
                String found = String.valueOf(state);
                throw new MobileTimeoutException("webview " + describe() + " timed out after " + session.actionTimeout().toMillis()
                        + " ms: " + (found.startsWith("matches:") ? found.substring(8) + " element(s) match, expected exactly one"
                                : "the element is not visible") + ".");
            }
            try {
                Thread.sleep(Math.max(50, session.pollInterval().toMillis()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new MobileException("Interrupted while waiting for webview " + describe(), e);
            }
        }
    }

    /** Human-readable query, e.g. {@code getByRole(button, name="Sign in")}. */
    public String describe() {
        return description;
    }

    @Override
    public String toString() {
        return "MobileWebViewLocator(" + description + ")";
    }

    private Object run(String op, String arg) {
        waitFor();
        return eval(op, arg);
    }

    private Object eval(String op, String arg) {
        return view.evaluate(SCRIPT, css, name, op, arg);
    }
}
