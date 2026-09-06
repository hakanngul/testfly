package io.testfly.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Persistent cache of compiled action plans ("Compile &amp; Freeze") — survives multiple test runs.
 *
 * <p>When a natural language goal is compiled by the agent, it is saved into
 * {@code .testfly/action-cache.json}. On subsequent test runs, the frozen plan is executed
 * directly with 0 ms AI latency, achieving deterministic replay.
 */
public final class ActionCache {

    private static final Logger LOG = Logger.getLogger(ActionCache.class.getName());
    private static final Map<String, ActionPlan> CACHE = new ConcurrentHashMap<>();
    private static volatile boolean loaded = false;
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private ActionCache() {}

    /**
     * Returns the cached ActionPlan for the specified URL and goal, or {@code null} if not cached.
     */
    public static ActionPlan get(String url, String goal) {
        load();
        return CACHE.get(buildKey(url, goal));
    }

    /**
     * Stores a compiled ActionPlan and flushes to disk.
     */
    public static void put(String url, String goal, ActionPlan plan) {
        load();
        CACHE.put(buildKey(url, goal), plan);
        save();
    }

    /**
     * Removes an entry from cache (e.g. on UI mismatch or execution failure).
     */
    public static void invalidate(String url, String goal) {
        load();
        String key = buildKey(url, goal);
        CACHE.remove(key);
        synchronized (ActionCache.class) {
            File file = cacheFile();
            if (file.exists()) {
                try {
                    Map<String, ActionPlan> existing = MAPPER.readValue(file,
                            new TypeReference<Map<String, ActionPlan>>() {});
                    if (existing != null && existing.containsKey(key)) {
                        existing.remove(key);
                        MAPPER.writeValue(file, existing);
                    }
                } catch (IOException e) {
                    LOG.warning("[ActionCache] Failed to invalidate cache on disk: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Clears all in-memory cache entries.
     */
    public static void clear() {
        CACHE.clear();
        loaded = true;
    }

    /**
     * Returns the number of cached plans.
     */
    public static int size() {
        load();
        return CACHE.size();
    }

    /**
     * Generates a cache key combining normalized URL path and goal.
     */
    public static String buildKey(String url, String goal) {
        String path = normalizeUrl(url);
        String cleanGoal = goal != null ? goal.trim().toLowerCase() : "";
        return path + "::" + cleanGoal;
    }

    private static String normalizeUrl(String url) {
        if (url == null || url.isBlank()) return "";
        try {
            URI uri = URI.create(url);
            String path = uri.getPath();
            return (path != null && !path.isBlank()) ? path : url;
        } catch (Exception e) {
            return url;
        }
    }

    /**
     * Loads the cache from {@code .testfly/action-cache.json}. Safe to call multiple times.
     */
    public static void load() {
        if (loaded) return;
        synchronized (ActionCache.class) {
            if (loaded) return;
            File file = cacheFile();
            if (file.exists()) {
                try {
                    Map<String, ActionPlan> entries = MAPPER.readValue(file,
                            new TypeReference<Map<String, ActionPlan>>() {});
                    if (entries != null) {
                        CACHE.putAll(entries);
                    }
                } catch (IOException e) {
                    LOG.warning("[ActionCache] Failed to load action cache: " + e.getMessage());
                }
            }
            loaded = true;
        }
    }

    /**
     * Persists the cache to disk, merging with existing entries.
     */
    public static void save() {
        if (CACHE.isEmpty()) return;
        synchronized (ActionCache.class) {
            File file = cacheFile();
            try {
                if (file.getParentFile() != null) {
                    file.getParentFile().mkdirs();
                }
                Map<String, ActionPlan> merged = new LinkedHashMap<>();
                if (file.exists()) {
                    try {
                        Map<String, ActionPlan> existing = MAPPER.readValue(file,
                                new TypeReference<Map<String, ActionPlan>>() {});
                        if (existing != null) merged.putAll(existing);
                    } catch (IOException ignored) {}
                }
                merged.putAll(CACHE);
                MAPPER.writeValue(file, merged);
            } catch (IOException e) {
                LOG.warning("[ActionCache] Failed to save action cache: " + e.getMessage());
            }
        }
    }

    private static File cacheFile() {
        return new File(".testfly/action-cache.json");
    }
}
