package io.testfly.mobile;

import io.testfly.mobile.internal.LocatorEngine;
import io.testfly.mobile.internal.MobileSessions;
import io.testfly.mobile.internal.SelectorParser;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Mobile locators kept in a YAML file instead of code (incubating, ADR 0002). One file serves iOS
 * and Android; values are the selector strings of {@link MobileScreen#locate(String)}.
 *
 * <pre>
 * login:
 *   username:
 *     ios: LoginScreen.nameTextField
 *     android: com.example:id/etUsername
 *   signIn: role=button;name=Sign in     # a plain value: both platforms
 *   welcome:
 *     mobile: text=Welcome back           # mobile: both platforms
 * </pre>
 *
 * {@code MobileLocators.load("mobile-locators/login.yml").get("login.username")} returns the locator
 * of the running platform. Groups nest with dots; a key without a selector for the platform yields a
 * locator that fails when used. Selectors are checked when the file is loaded.
 */
public final class MobileLocators {

    private static final Set<String> BLOCKS = Set.of("ios", "android", "mobile");

    private record Entry(String ios, String android) {
    }

    private final String source;
    private final Map<String, Entry> entries;

    private MobileLocators(String source, Map<String, Entry> entries) {
        this.source = source;
        this.entries = entries;
    }

    /** Loads a file from the classpath, else from the file system (relative or absolute). */
    public static MobileLocators load(String resource) {
        if (resource == null || resource.isBlank()) {
            throw new MobileLocatorException("Mobile locator file path must not be blank");
        }
        for (ClassLoader loader : new ClassLoader[] {Thread.currentThread().getContextClassLoader(), MobileLocators.class.getClassLoader()}) {
            if (loader == null) {
                continue;
            }
            InputStream stream = loader.getResourceAsStream(resource);
            if (stream != null) {
                try (stream) {
                    return load(stream, "classpath:" + resource);
                } catch (IOException e) {
                    throw new MobileLocatorException("Cannot read mobile locator file 'classpath:" + resource + "': " + e.getMessage());
                }
            }
        }
        Path path = Paths.get(resource);
        if (Files.isRegularFile(path)) {
            return load(path);
        }
        throw new MobileLocatorException("Mobile locator file not found: '" + resource + "'. Searched the classpath and "
                + path.toAbsolutePath());
    }

    public static MobileLocators load(Path path) {
        try (InputStream stream = Files.newInputStream(path)) {
            return load(stream, path.toAbsolutePath().toString());
        } catch (IOException e) {
            throw new MobileLocatorException("Cannot read mobile locator file '" + path.toAbsolutePath() + "': " + e.getMessage());
        }
    }

    /** Loads from {@code stream} (not closed); {@code sourceName} appears in error messages. */
    public static MobileLocators load(InputStream stream, String sourceName) {
        String source = sourceName == null || sourceName.isBlank() ? "<stream>" : sourceName;
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        Object root;
        try {
            root = new Yaml(new SafeConstructor(options)).load(stream);
        } catch (RuntimeException e) {
            throw new MobileLocatorException("Invalid YAML in mobile locator file '" + source + "': " + e.getMessage());
        }
        Map<String, Entry> entries = new LinkedHashMap<>();
        if (root != null) {
            if (!(root instanceof Map<?, ?> map)) {
                throw new MobileLocatorException("Mobile locator file '" + source + "' must be a map of keys to selectors");
            }
            collect(map, "", source, entries);
        }
        return new MobileLocators(source, Collections.unmodifiableMap(entries));
    }

    private static void collect(Map<?, ?> map, String prefix, String source, Map<String, Entry> out) {
        for (Map.Entry<?, ?> e : map.entrySet()) {
            String key = prefix + e.getKey();
            Object value = e.getValue();
            if (value instanceof String selector) {
                out.put(key, entry(key, source, selector, selector));
            } else if (value instanceof Map<?, ?> child && !child.isEmpty() && BLOCKS.containsAll(stringKeys(child))) {
                String both = text(child.get("mobile"), key, source);
                String ios = text(child.get("ios"), key, source);
                String android = text(child.get("android"), key, source);
                out.put(key, entry(key, source, ios != null ? ios : both, android != null ? android : both));
            } else if (value instanceof Map<?, ?> child) {
                collect(child, key + ".", source, out);
            } else {
                throw new MobileLocatorException("'" + key + "' in '" + source + "' must be a selector string or a map");
            }
        }
    }

    private static Set<String> stringKeys(Map<?, ?> map) {
        Set<String> keys = new TreeSet<>();
        map.keySet().forEach(k -> keys.add(String.valueOf(k)));
        return keys;
    }

    private static String text(Object value, String key, String source) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof String s)) {
            throw new MobileLocatorException("'" + key + "' in '" + source + "': selectors must be strings, got " + value);
        }
        return s;
    }

    private static Entry entry(String key, String source, String ios, String android) {
        for (String selector : new String[] {ios, android}) {
            if (selector != null) {
                try {
                    SelectorParser.parse(selector);
                } catch (MobileLocatorException e) {
                    throw new MobileLocatorException("'" + key + "' in '" + source + "': " + e.getMessage());
                }
            }
        }
        return new Entry(ios, android);
    }

    /** The locator for {@code key} on the current test's device. */
    public MobileLocator get(String key) {
        return get(key, MobileSessions.current().screen());
    }

    /** The locator for {@code key} on {@code screen}'s platform. */
    public MobileLocator get(String key, MobileScreen screen) {
        Entry entry = entries.get(key);
        if (entry == null) {
            throw new MobileLocatorException("No mobile locator '" + key + "' in '" + source + "'. Keys: " + entries.keySet());
        }
        MobilePlatform platform = screen.platform();
        String selector = platform == MobilePlatform.IOS ? entry.ios() : entry.android();
        return selector == null
                ? screen.locator(LocatorEngine.missing(key + ": no " + platform.wireName() + " selector in " + source))
                : screen.locate(selector);
    }

    /** Every key in the file, groups joined with dots. */
    public Set<String> keys() {
        return entries.keySet();
    }
}
