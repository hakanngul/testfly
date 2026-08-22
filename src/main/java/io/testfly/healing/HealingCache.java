package io.testfly.healing;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Persistent cache of healed locators — survives {@code mvn clean}.
 *
 * <p>When a locator is healed during a test run, the mapping is stored here.
 * On subsequent runs, the cache is consulted <b>before</b> running the full
 * fallback strategy chain — so a locator healed yesterday is resolved
 * instantly today.
 *
 * <p>Storage: {@code .testfly/healed-locators.json} in the project root.
 * This file should be committed to VCS so CI benefits from local heals.
 */
public final class HealingCache {

    private static final Logger LOG = Logger.getLogger(HealingCache.class.getName());
    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();
    private static volatile boolean loaded = false;

    private HealingCache() {}

    /**
     * Returns the previously healed locator for {@code originalLocator},
     * or {@code null} if no cache entry exists.
     */
    public static String get(String originalLocator) {
        return CACHE.get(originalLocator);
    }

    /**
     * Stores a heal mapping. Called after a successful heal so the next run
     * can skip the fallback chain entirely.
     */
    public static void put(String originalLocator, String healedLocator) {
        CACHE.put(originalLocator, healedLocator);
    }

    /** Returns the number of cached entries. */
    public static int size() {
        return CACHE.size();
    }

    /**
     * Loads the cache from {@code .testfly/healed-locators.json}.
     * Safe to call multiple times — subsequent calls are no-ops.
     */
    public static void load() {
        if (loaded) return;
        synchronized (HealingCache.class) {
            if (loaded) return;
            File cacheFile = cacheFile();
            if (cacheFile.exists()) {
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    Map<String, Object> data = mapper.readValue(cacheFile,
                            new TypeReference<Map<String, Object>>() {});

                    @SuppressWarnings("unchecked")
                    Map<String, String> entries = (Map<String, String>) data.get("entries");
                    if (entries != null) {
                        CACHE.putAll(entries);
                        System.out.println("[TestFly] Healing cache loaded: "
                                + entries.size() + " locator(s) from "
                                + cacheFile.getPath());
                    }
                } catch (IOException e) {
                    LOG.warning("[HealingCache] Failed to load cache: " + e.getMessage());
                }
            }
            loaded = true;
        }
    }

    /**
     * Saves the cache to {@code .testfly/healed-locators.json}.
     * Merges with any existing entries on disk (never loses data).
     */
    public static void save() {
        if (CACHE.isEmpty()) return;

        File cacheFile = cacheFile();
        try {
            cacheFile.getParentFile().mkdirs();

            // Merge with existing entries on disk
            Map<String, String> merged = new LinkedHashMap<>();
            if (cacheFile.exists()) {
                ObjectMapper reader = new ObjectMapper();
                Map<String, Object> existing = reader.readValue(cacheFile,
                        new TypeReference<Map<String, Object>>() {});
                @SuppressWarnings("unchecked")
                Map<String, String> oldEntries = (Map<String, String>) existing.get("entries");
                if (oldEntries != null) {
                    merged.putAll(oldEntries);
                }
            }
            merged.putAll(CACHE);

            Map<String, Object> root = new LinkedHashMap<>();
            root.put("totalCached", merged.size());
            root.put("entries", merged);

            ObjectMapper writer = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
            writer.writeValue(cacheFile, root);
            System.out.println("[TestFly] Healing cache saved: "
                    + merged.size() + " locator(s) → " + cacheFile.getPath());
        } catch (IOException e) {
            LOG.warning("[HealingCache] Failed to save cache: " + e.getMessage());
        }
    }

    private static File cacheFile() {
        return new File(System.getProperty("user.dir"),
                ".testfly" + File.separator + "healed-locators.json");
    }
}
