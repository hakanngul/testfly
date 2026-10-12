package io.testfly.locator;

import io.testfly.api.TestFlyApi;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.openqa.selenium.By;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Loads named {@link Locator} declarations from a YAML file so page objects and
 * tests can share them without hard-coding selectors.
 *
 * <pre>
 * page: register
 * elements:
 *   register_form:
 *     css: "#register-form"
 *     elements:
 *       email:
 *         css: "input[type=email]"
 *       submit_button:
 *         role: button
 *         accessibleName: "Register"
 *         exact: true
 *   login_link:
 *     text: "Already have an account?"
 * </pre>
 *
 * <pre>
 * LocatorRepository repo = LocatorRepository.load("locators/register.yaml");
 * repo.get("register_form.email").type("a@b.com");
 * </pre>
 *
 * <h2>Element rules</h2>
 * <ul>
 * <li>Exactly one <em>strategy</em> key: {@code css}, {@code xpath},
 * {@code id}, {@code byName}, {@code className}, {@code text}, {@code label},
 * {@code placeholder}, {@code testId}, {@code altText}, {@code title} or
 * {@code role}.</li>
 * <li>Optional modifiers: {@code accessibleName} and {@code level}
 * ({@code role} only; {@code level} needs {@code role: heading}),
 * {@code exact}, and at most one of {@code nth}, {@code first},
 * {@code last}.</li>
 * <li>An element with {@code elements:} is a container. Its own strategy must be
 * one of {@code css}, {@code xpath}, {@code id}, {@code byName} or
 * {@code className}, it takes no modifiers, and each child is scoped to it with
 * {@link Locator#within(By)}. Containers nest one level only and children are
 * addressed as {@code container.child}.</li>
 * </ul>
 *
 * <p>
 * Any violation (unknown key, conflicting strategies, malformed value, missing
 * file) fails fast with a {@link LocatorException} naming the file and element.
 *
 * <p>
 * Instances are immutable and hold no driver state, so a repository can be
 * shared across threads. {@code byTestId} resolves the attribute configured by
 * {@code locators.testIdAttribute} at lookup time, not at load time.
 */
@TestFlyApi(since = "1.0.7")
public final class LocatorRepository {

    private static final Set<String> BY_STRATEGIES = Set.of(
            "css", "xpath", "id", "byName", "className");

    private static final Set<String> STRATEGIES = Set.of(
            "css", "xpath", "id", "byName", "className", "text", "label",
            "placeholder", "testId", "altText", "title", "role");

    private static final Set<String> EXACT_STRATEGIES = Set.of(
            "text", "label", "placeholder", "altText", "title", "role");

    private static final Set<String> MODIFIERS = Set.of(
            "accessibleName", "level", "exact", "nth", "first", "last");

    private final String source;
    private final String page;
    private final Map<String, Locator> locators;

    private LocatorRepository(String source, String page, Map<String, Locator> locators) {
        this.source = source;
        this.page = page;
        this.locators = Collections.unmodifiableMap(locators);
    }

    // ------------------------------------------------------------------
    // Loading
    // ------------------------------------------------------------------

    /**
     * Loads a locator file, looking on the classpath first and then on the file
     * system (relative to the working directory, or absolute).
     *
     * @param resource classpath resource name or file path
     * @throws LocatorException if the file cannot be found, read, or is invalid
     */
    public static LocatorRepository load(String resource) {
        if (resource == null || resource.isBlank()) {
            throw new LocatorException("Locator file path must not be blank");
        }

        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        for (ClassLoader loader : new ClassLoader[] { contextLoader, LocatorRepository.class.getClassLoader() }) {
            if (loader == null) {
                continue;
            }
            InputStream stream = loader.getResourceAsStream(resource);
            if (stream != null) {
                try (stream) {
                    return load(stream, "classpath:" + resource);
                } catch (IOException e) {
                    throw new LocatorException("Failed to read locator file 'classpath:" + resource + "'", e);
                }
            }
        }

        Path path = Paths.get(resource);
        if (Files.isRegularFile(path)) {
            return load(path);
        }

        throw new LocatorException("Locator file not found: '" + resource + "'. Searched classpath ("
                + resource + ") and file system (" + path.toAbsolutePath() + ")");
    }

    /**
     * Loads a locator file from the file system.
     *
     * @throws LocatorException if the file cannot be read or is invalid
     */
    public static LocatorRepository load(Path path) {
        if (path == null) {
            throw new LocatorException("Locator file path must not be null");
        }
        if (!Files.isRegularFile(path)) {
            throw new LocatorException("Locator file not found: " + path.toAbsolutePath());
        }
        try (InputStream stream = Files.newInputStream(path)) {
            return load(stream, path.toAbsolutePath().toString());
        } catch (IOException e) {
            throw new LocatorException("Failed to read locator file '" + path.toAbsolutePath() + "'", e);
        }
    }

    /**
     * Loads locators from a stream. The stream is not closed.
     *
     * @param sourceName name used in error messages (file name, URL, ...)
     * @throws LocatorException if the content is invalid
     */
    public static LocatorRepository load(InputStream stream, String sourceName) {
        if (stream == null) {
            throw new LocatorException("Locator stream must not be null");
        }
        String source = sourceName == null || sourceName.isBlank() ? "<stream>" : sourceName;

        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        Object root;
        try {
            root = new Yaml(new SafeConstructor(options)).load(stream);
        } catch (RuntimeException e) {
            throw new LocatorException("Invalid YAML in locator file '" + source + "': " + e.getMessage(), e);
        }
        return parse(root, source);
    }

    // ------------------------------------------------------------------
    // Access
    // ------------------------------------------------------------------

    /** The optional {@code page} name declared in the file, or {@code null}. */
    public String page() {
        return page;
    }

    /**
     * Returns the locator for {@code key}. Children of a container are addressed
     * as {@code container.child}.
     *
     * @throws LocatorException if the key is unknown
     */
    public Locator get(String key) {
        Locator locator = key == null ? null : locators.get(key);
        if (locator == null) {
            throw new LocatorException("No locator '" + key + "' in '" + source
                    + "'. Available: " + locators.keySet());
        }
        return locator;
    }

    /** True when a locator with this key exists. */
    public boolean contains(String key) {
        return key != null && locators.containsKey(key);
    }

    /** All locator keys in declaration order, including {@code container.child} keys. */
    public Set<String> keys() {
        return locators.keySet();
    }

    // ------------------------------------------------------------------
    // Parsing
    // ------------------------------------------------------------------

    private static LocatorRepository parse(Object root, String source) {
        if (!(root instanceof Map<?, ?> rootMap)) {
            throw new LocatorException("Locator file '" + source
                    + "' must be a YAML mapping with an 'elements' section");
        }

        Map<String, Object> top = stringKeyed(rootMap, source, "<root>");
        for (String key : top.keySet()) {
            if (!key.equals("page") && !key.equals("elements")) {
                throw new LocatorException("Locator file '" + source + "': unknown top-level key '" + key
                        + "'. Allowed: page, elements");
            }
        }

        String page = null;
        if (top.containsKey("page")) {
            Object value = top.get("page");
            if (!(value instanceof String text) || text.isBlank()) {
                throw new LocatorException("Locator file '" + source + "': 'page' must be a non-blank string");
            }
            page = text;
        }

        Object elements = top.get("elements");
        if (!(elements instanceof Map<?, ?> elementsMap) || elementsMap.isEmpty()) {
            throw new LocatorException("Locator file '" + source + "': 'elements' must be a non-empty mapping");
        }

        Map<String, Locator> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : stringKeyed(elementsMap, source, "elements").entrySet()) {
            parseElement(entry.getKey(), entry.getValue(), source, result);
        }
        return new LocatorRepository(source, page, result);
    }

    private static void parseElement(String name, Object value, String source, Map<String, Locator> out) {
        validateName(name, source);
        Map<String, Object> def = elementMap(name, value, source);

        if (!def.containsKey("elements")) {
            out.put(name, buildLocator(name, def, source, null));
            return;
        }

        // Container: strategy + elements only, By-based strategy only.
        Map<String, Object> containerDef = new LinkedHashMap<>(def);
        Object children = containerDef.remove("elements");
        String strategy = strategyOf(name, containerDef, source);
        if (!BY_STRATEGIES.contains(strategy)) {
            throw fail(source, name, "container strategy must be one of " + sorted(BY_STRATEGIES)
                    + " (found '" + strategy + "')");
        }
        if (containerDef.size() > 1) {
            Set<String> extra = new LinkedHashSet<>(containerDef.keySet());
            extra.remove(strategy);
            throw fail(source, name, "container takes no modifiers, found " + extra);
        }
        if (!(children instanceof Map<?, ?> childMap) || childMap.isEmpty()) {
            throw fail(source, name, "'elements' must be a non-empty mapping");
        }

        Locator container = buildLocator(name, containerDef, source, null);
        out.put(name, container);
        By scope = container.toBy();

        for (Map.Entry<String, Object> child : stringKeyed(childMap, source, name + ".elements").entrySet()) {
            String childName = child.getKey();
            String path = name + "." + childName;
            validateName(childName, source);
            Map<String, Object> childDef = elementMap(path, child.getValue(), source);
            if (childDef.containsKey("elements")) {
                throw fail(source, path, "containers nest one level only; 'elements' is not allowed here");
            }
            out.put(path, buildLocator(path, childDef, source, scope));
        }
    }

    private static Locator buildLocator(String path, Map<String, Object> def, String source, By scope) {
        for (String key : def.keySet()) {
            if (!STRATEGIES.contains(key) && !MODIFIERS.contains(key)) {
                throw fail(source, path, "unknown key '" + key + "'. Allowed strategies: " + sorted(STRATEGIES)
                        + "; modifiers: " + sorted(MODIFIERS));
            }
        }

        String strategy = strategyOf(path, def, source);
        Object raw = def.get(strategy);
        Locator locator = switch (strategy) {
            case "role" -> Locator.byRole(parseRole(path, raw, source));
            case "css" -> Locator.cssSelector(text(path, strategy, raw, source));
            case "xpath" -> Locator.xpath(text(path, strategy, raw, source));
            case "id" -> Locator.id(text(path, strategy, raw, source));
            case "byName" -> Locator.name(text(path, strategy, raw, source));
            case "className" -> Locator.className(text(path, strategy, raw, source));
            case "text" -> Locator.byText(text(path, strategy, raw, source));
            case "label" -> Locator.byLabel(text(path, strategy, raw, source));
            case "placeholder" -> Locator.byPlaceholder(text(path, strategy, raw, source));
            case "testId" -> Locator.byTestId(text(path, strategy, raw, source));
            case "altText" -> Locator.byAltText(text(path, strategy, raw, source));
            default -> Locator.byTitle(text(path, strategy, raw, source));
        };

        if (def.containsKey("accessibleName")) {
            requireRole(path, "accessibleName", strategy, source);
            locator = locator.withName(text(path, "accessibleName", def.get("accessibleName"), source));
        }
        if (def.containsKey("level")) {
            requireRole(path, "level", strategy, source);
            if (!(raw instanceof String roleName) || parseRole(path, roleName, source) != Role.HEADING) {
                throw fail(source, path, "'level' requires role: heading");
            }
            Object level = def.get("level");
            if (!(level instanceof Integer value) || value < 1 || value > 6) {
                throw fail(source, path, "'level' must be an integer between 1 and 6");
            }
            locator = locator.withLevel(value);
        }
        if (def.containsKey("exact")) {
            if (!EXACT_STRATEGIES.contains(strategy)) {
                throw fail(source, path, "'exact' is not supported with '" + strategy
                        + "'. Supported with: " + sorted(EXACT_STRATEGIES));
            }
            if (bool(path, "exact", def.get("exact"), source)) {
                locator = locator.exact();
            }
        }

        locator = applyPosition(path, def, source, locator);
        return scope == null ? locator : locator.within(scope);
    }

    private static Locator applyPosition(String path, Map<String, Object> def, String source, Locator locator) {
        List<String> chosen = new ArrayList<>();
        boolean first = def.containsKey("first") && bool(path, "first", def.get("first"), source);
        boolean last = def.containsKey("last") && bool(path, "last", def.get("last"), source);
        if (first) {
            chosen.add("first");
        }
        if (last) {
            chosen.add("last");
        }
        if (def.containsKey("nth")) {
            chosen.add("nth");
        }
        if (chosen.size() > 1) {
            throw fail(source, path, "only one of nth, first, last may be used, found " + chosen);
        }
        if (def.containsKey("nth")) {
            Object nth = def.get("nth");
            if (!(nth instanceof Integer index) || index < 0) {
                throw fail(source, path, "'nth' must be a zero-based non-negative integer");
            }
            return locator.nth(index);
        }
        if (first) {
            return locator.first();
        }
        return last ? locator.last() : locator;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static String strategyOf(String path, Map<String, Object> def, String source) {
        List<String> found = new ArrayList<>();
        for (String key : def.keySet()) {
            if (STRATEGIES.contains(key)) {
                found.add(key);
            }
        }
        if (found.isEmpty()) {
            throw fail(source, path, "no locator strategy. Use exactly one of " + sorted(STRATEGIES));
        }
        if (found.size() > 1) {
            throw fail(source, path, "conflicting strategies " + found + "; use exactly one");
        }
        return found.get(0);
    }

    private static void requireRole(String path, String modifier, String strategy, String source) {
        if (!strategy.equals("role")) {
            throw fail(source, path, "'" + modifier + "' requires the 'role' strategy (found '" + strategy + "')");
        }
    }

    private static Role parseRole(String path, Object raw, String source) {
        String name = text(path, "role", raw, source);
        for (Role role : Role.values()) {
            if (role.ariaName().equalsIgnoreCase(name)) {
                return role;
            }
        }
        List<String> valid = new ArrayList<>();
        for (Role role : Role.values()) {
            valid.add(role.ariaName());
        }
        throw fail(source, path, "unknown role '" + name + "'. Valid roles: " + valid);
    }

    private static String text(String path, String key, Object value, String source) {
        if (value instanceof Number || value instanceof String) {
            String text = value.toString();
            if (!text.isBlank()) {
                return text;
            }
        }
        String hint = value instanceof Boolean || value == null
                ? ". YAML reads unquoted yes/no/on/off/true/false/null as non-strings; quote the value, e.g. "
                        + key + ": \"Yes\""
                : "";
        throw fail(source, path, "'" + key + "' must be a non-blank string" + hint);
    }

    private static boolean bool(String path, String key, Object value, String source) {
        if (value instanceof Boolean flag) {
            return flag;
        }
        throw fail(source, path, "'" + key + "' must be true or false");
    }

    private static Map<String, Object> elementMap(String path, Object value, String source) {
        if (!(value instanceof Map<?, ?> map) || map.isEmpty()) {
            throw fail(source, path, "must be a non-empty mapping with one locator strategy");
        }
        return stringKeyed(map, source, path);
    }

    private static Map<String, Object> stringKeyed(Map<?, ?> map, String source, String where) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new LocatorException("Locator file '" + source + "', '" + where
                        + "': keys must be strings, found '" + entry.getKey() + "' ("
                        + (entry.getKey() == null ? "null" : entry.getKey().getClass().getSimpleName())
                        + "). YAML reads names such as on/off/yes/no/null or 123 as non-strings; quote the key");
            }
            result.put(key, entry.getValue());
        }
        return result;
    }

    private static void validateName(String name, String source) {
        if (name.isBlank() || name.contains(".")) {
            throw new LocatorException("Locator file '" + source + "': invalid element name '" + name
                    + "' (must be non-blank and contain no '.')");
        }
    }

    private static List<String> sorted(Set<String> values) {
        List<String> list = new ArrayList<>(values);
        Collections.sort(list);
        return list;
    }

    private static LocatorException fail(String source, String path, String message) {
        return new LocatorException("Locator file '" + source + "', element '" + path + "': " + message);
    }
}
