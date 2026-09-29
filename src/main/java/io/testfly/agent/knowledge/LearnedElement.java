package io.testfly.agent.knowledge;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.testfly.agent.ActionType;
import io.testfly.api.TestFlyApi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents a semantically learned element on a web page stored in the Learned Page Model (Auto-POM).
 */
@TestFlyApi(since = "1.0.5")
public final class LearnedElement {

    private final String name;
    private final String locator;
    private final ActionType actionType;
    private final String parentTrigger;
    private final List<String> synonyms;
    private final long lastSeenAt;

    @JsonCreator
    public LearnedElement(
            @JsonProperty("name") String name,
            @JsonProperty("locator") String locator,
            @JsonProperty("actionType") ActionType actionType,
            @JsonProperty("parentTrigger") String parentTrigger,
            @JsonProperty("synonyms") List<String> synonyms,
            @JsonProperty("lastSeenAt") long lastSeenAt) {
        this.name = name != null ? name.trim().toLowerCase() : "";
        this.locator = locator != null ? locator.trim() : "";
        this.actionType = actionType != null ? actionType : ActionType.CLICK;
        this.parentTrigger = parentTrigger != null ? parentTrigger.trim() : null;
        this.synonyms = synonyms != null ? new ArrayList<>(synonyms) : new ArrayList<>();
        this.lastSeenAt = lastSeenAt > 0 ? lastSeenAt : System.currentTimeMillis();
    }

    public LearnedElement(String name, String locator, ActionType actionType, String parentTrigger, List<String> synonyms) {
        this(name, locator, actionType, parentTrigger, synonyms, System.currentTimeMillis());
    }

    @JsonProperty("name")
    public String name() {
        return name;
    }

    @JsonProperty("locator")
    public String locator() {
        return locator;
    }

    @JsonProperty("actionType")
    public ActionType actionType() {
        return actionType;
    }

    @JsonProperty("parentTrigger")
    public String parentTrigger() {
        return parentTrigger;
    }

    @JsonProperty("synonyms")
    public List<String> synonyms() {
        return Collections.unmodifiableList(synonyms);
    }

    @JsonProperty("lastSeenAt")
    public long lastSeenAt() {
        return lastSeenAt;
    }

    public String getName() {
        return name();
    }

    public String getLocator() {
        return locator();
    }

    public ActionType getActionType() {
        return actionType();
    }

    public String getParentTrigger() {
        return parentTrigger();
    }

    public List<String> getSynonyms() {
        return synonyms();
    }

    public long getLastSeenAt() {
        return lastSeenAt();
    }

    /**
     * Checks if this element matches the given query term or any of its synonyms.
     */
    public boolean matches(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        String clean = query.trim().toLowerCase();
        if (clean.equals(name) || name.contains(clean) || clean.contains(name)) {
            return true;
        }
        for (String syn : synonyms) {
            String cleanSyn = syn.trim().toLowerCase();
            if (clean.equals(cleanSyn) || clean.contains(cleanSyn) || cleanSyn.contains(clean)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LearnedElement that = (LearnedElement) o;
        return Objects.equals(name, that.name) && Objects.equals(locator, that.locator);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, locator);
    }

    @Override
    public String toString() {
        return "LearnedElement{" +
                "name='" + name + '\'' +
                ", locator='" + locator + '\'' +
                ", actionType=" + actionType +
                ", parentTrigger='" + parentTrigger + '\'' +
                ", synonyms=" + synonyms +
                '}';
    }
}
