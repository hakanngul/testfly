package io.testfly.agent.knowledge;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.testfly.api.TestFlyApi;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Represents a learned page model (Auto-POM) for a specific URL pattern.
 */
@TestFlyApi(since = "1.0.5")
public final class LearnedPageModel {

    private final String urlPattern;
    private final String pageName;
    private final Map<String, LearnedElement> elements;
    private final long updatedAt;

    @JsonCreator
    public LearnedPageModel(
            @JsonProperty("urlPattern") String urlPattern,
            @JsonProperty("pageName") String pageName,
            @JsonProperty("elements") Map<String, LearnedElement> elements,
            @JsonProperty("updatedAt") long updatedAt) {
        this.urlPattern = urlPattern != null ? urlPattern.trim() : "";
        this.pageName = pageName != null ? pageName.trim() : "";
        this.elements = elements != null ? new LinkedHashMap<>(elements) : new LinkedHashMap<>();
        this.updatedAt = updatedAt > 0 ? updatedAt : System.currentTimeMillis();
    }

    public LearnedPageModel(String urlPattern, String pageName) {
        this(urlPattern, pageName, new LinkedHashMap<>(), System.currentTimeMillis());
    }

    public String urlPattern() {
        return urlPattern;
    }

    public String pageName() {
        return pageName;
    }

    public Map<String, LearnedElement> elements() {
        return Collections.unmodifiableMap(elements);
    }

    public long updatedAt() {
        return updatedAt;
    }

    /**
     * Registers or updates a learned element in this page model.
     */
    public synchronized void putElement(LearnedElement element) {
        if (element != null && !element.name().isBlank()) {
            elements.put(element.name(), element);
        }
    }

    /**
     * Finds a learned element matching the given query by name or synonyms.
     */
    public Optional<LearnedElement> findElement(String query) {
        if (query == null || query.isBlank()) {
            return Optional.empty();
        }
        String clean = query.trim().toLowerCase();
        if (elements.containsKey(clean)) {
            return Optional.of(elements.get(clean));
        }
        for (LearnedElement el : elements.values()) {
            if (el.matches(clean)) {
                return Optional.of(el);
            }
        }
        return Optional.empty();
    }

    @Override
    public String toString() {
        return "LearnedPageModel{" +
                "urlPattern='" + urlPattern + '\'' +
                ", pageName='" + pageName + '\'' +
                ", elementCount=" + elements.size() +
                '}';
    }
}
