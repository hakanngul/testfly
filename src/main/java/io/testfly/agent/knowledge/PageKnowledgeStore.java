package io.testfly.agent.knowledge;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.testfly.api.TestFlyApi;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Persistent store for Learned Page Models (Auto-POM).
 * <p>
 * Saves discovered elements, locators, and interaction patterns to
 * {@code .testfly/page-knowledge.json} across test runs.
 */
@TestFlyApi(since = "1.0.5")
public final class PageKnowledgeStore {

    private static final Logger LOG = Logger.getLogger(PageKnowledgeStore.class.getName());
    private static final Map<String, LearnedPageModel> MODELS = new ConcurrentHashMap<>();
    private static volatile boolean loaded = false;
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private PageKnowledgeStore() {}

    /**
     * Retrieves the learned page model for the given URL, or empty if not learned yet.
     */
    public static Optional<LearnedPageModel> getPageModel(String url) {
        load();
        String key = normalizeUrl(url);
        return Optional.ofNullable(MODELS.get(key));
    }

    /**
     * Gets or creates a new page model for the given URL.
     */
    public static LearnedPageModel getOrCreate(String url, String pageTitle) {
        load();
        String key = normalizeUrl(url);
        return MODELS.computeIfAbsent(key, k -> new LearnedPageModel(k, pageTitle != null ? pageTitle : k));
    }

    /**
     * Records or updates a learned element for the given URL and flushes to disk.
     */
    public static void recordElement(String url, LearnedElement element) {
        if (element == null || element.name().isBlank()) {
            return;
        }
        load();
        LearnedPageModel model = getOrCreate(url, null);
        model.putElement(element);
        save();
    }

    /**
     * Finds a learned element matching the query on the given page.
     */
    public static Optional<LearnedElement> findElement(String url, String query) {
        return getPageModel(url).flatMap(model -> model.findElement(query));
    }

    /**
     * Normalizes a URL to its path component for consistent cross-test lookup.
     */
    public static String normalizeUrl(String url) {
        if (url == null || url.isBlank()) return "/";
        try {
            URI uri = URI.create(url);
            String path = uri.getPath();
            return (path != null && !path.isBlank()) ? path : "/";
        } catch (Exception e) {
            return url;
        }
    }

    /**
     * Loads the knowledge store from {@code .testfly/page-knowledge.json}.
     */
    public static void load() {
        if (loaded) return;
        synchronized (PageKnowledgeStore.class) {
            if (loaded) return;
            File file = knowledgeFile();
            if (file.exists()) {
                try {
                    Map<String, LearnedPageModel> entries = MAPPER.readValue(file,
                            new TypeReference<Map<String, LearnedPageModel>>() {});
                    if (entries != null) {
                        for (Map.Entry<String, LearnedPageModel> entry : entries.entrySet()) {
                            LearnedPageModel m = entry.getValue();
                            if (m != null) {
                                String url = (m.urlPattern() != null && !m.urlPattern().isBlank()) ? m.urlPattern() : entry.getKey();
                                String page = (m.pageName() != null && !m.pageName().isBlank()) ? m.pageName() : entry.getKey();
                                MODELS.put(entry.getKey(), new LearnedPageModel(url, page, m.elements(), m.updatedAt()));
                            }
                        }
                    }
                } catch (IOException e) {
                    LOG.warning("[PageKnowledgeStore] Failed to load page knowledge: " + e.getMessage());
                }
            }
            loaded = true;

            // If store is empty or models have no elements, bootstrap from action cache
            boolean hasElements = MODELS.values().stream().anyMatch(m -> !m.elements().isEmpty());
            if (!hasElements) {
                try {
                    KnowledgeLearner.learnFromActionCache();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /**
     * Persists the knowledge store to disk.
     */
    public static void save() {
        if (MODELS.isEmpty()) return;
        synchronized (PageKnowledgeStore.class) {
            File file = knowledgeFile();
            try {
                File parent = file.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                MAPPER.writeValue(file, MODELS);
            } catch (IOException e) {
                LOG.warning("[PageKnowledgeStore] Failed to save page knowledge: " + e.getMessage());
            }
        }
    }

    /**
     * Clears in-memory knowledge store (for testing).
     */
    public static void clear() {
        MODELS.clear();
        loaded = true;
    }

    /**
     * Returns the count of registered page models.
     */
    public static int size() {
        load();
        return MODELS.size();
    }

    private static File knowledgeFile() {
        return new File(".testfly", "page-knowledge.json");
    }
}
