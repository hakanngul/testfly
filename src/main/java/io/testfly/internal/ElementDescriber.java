package io.testfly.internal;

import java.util.List;
import org.openqa.selenium.WebElement;

/**
 * Builds short, single-line descriptions of elements for error messages, for
 * example {@code <button#save.btn.primary "Save">}. Every Selenium call is
 * guarded: a stale or unresponsive element never turns a diagnostic into a new
 * failure.
 */
public final class ElementDescriber {

    private ElementDescriber() {
    }

    public static String describe(WebElement el) {
        if (el == null) {
            return "<null>";
        }
        try {
            String tag = el.getTagName();
            if (tag == null) {
                return "<element>";
            }
            StringBuilder sb = new StringBuilder("<").append(tag);
            String id = el.getAttribute("id");
            if (id != null && !id.isBlank()) {
                sb.append('#').append(id);
            }
            String classes = el.getAttribute("class");
            if (classes != null && !classes.isBlank()) {
                String[] parts = classes.trim().split("\\s+");
                for (int i = 0; i < Math.min(3, parts.length); i++) {
                    sb.append('.').append(parts[i]);
                }
            }
            String text = el.getText();
            if (text != null && !text.isBlank()) {
                String oneLine = text.trim().replaceAll("\\s+", " ");
                sb.append(" \"").append(oneLine.length() > 30 ? oneLine.substring(0, 30) + "…" : oneLine)
                        .append('"');
            }
            return sb.append('>').toString();
        } catch (RuntimeException e) {
            return "<element>";
        }
    }

    /** Describes up to {@code max} elements, noting how many were left out. */
    public static String describe(List<WebElement> els, int max) {
        StringBuilder sb = new StringBuilder();
        int shown = Math.min(max, els.size());
        for (int i = 0; i < shown; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(describe(els.get(i)));
        }
        if (els.size() > shown) {
            sb.append(" (+").append(els.size() - shown).append(" more)");
        }
        return sb.toString();
    }
}
