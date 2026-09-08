package io.testfly.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.introspector.PropertyUtils;

public final class ConfigurationLoader {

    private ConfigurationLoader() {
        // utility class
    }

    /**
     * Loads configuration using the following priority chain:
     * <ol>
     * <li>System property {@code -Dtestfly.config=/path/to/file.yml} (explicit
     * override)</li>
     * <li>Classpath: {@code testfly[-profile].yml} (standard Maven/Java convention
     * in src/test/resources)</li>
     * <li>Working directory: {@code ./testfly[-profile].yml} (fallback)</li>
     * </ol>
     */
    public static TestFlyConfig load() {
        String profile = System.getProperty("testfly.profile");

        String configFile = (profile == null || profile.isBlank())
                ? "testfly.yml"
                : "testfly-" + profile + ".yml";

        // Priority 1: explicit path via system property
        String explicitPath = System.getProperty("testfly.config");
        if (explicitPath != null && !explicitPath.isBlank()) {
            return loadFromFile(new File(explicitPath));
        }

        // Priority 2: classpath (standard Maven/Java convention)
        InputStream inputStream = ConfigurationLoader.class
                .getClassLoader()
                .getResourceAsStream(configFile);
        if (inputStream != null) {
            return parseAndValidate(inputStream);
        }

        // Priority 3: working directory (fallback)
        File workingDirFile = new File(configFile);
        if (workingDirFile.exists()) {
            return loadFromFile(workingDirFile);
        }

        throw new IllegalStateException(
                "Configuration file '" + configFile + "' not found. " +
                        "Checked: -Dtestfly.config, classpath, and working directory.");
    }

    private static TestFlyConfig loadFromFile(File file) {
        if (!file.exists()) {
            throw new IllegalStateException(
                    "Configuration file not found: " + file.getAbsolutePath());
        }
        try (InputStream is = new FileInputStream(file)) {
            return parseAndValidate(is);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to read configuration file: " + file.getAbsolutePath(), e);
        }
    }

    private static TestFlyConfig parseAndValidate(InputStream inputStream) {
        LoaderOptions loaderOptions = new LoaderOptions();
        Constructor constructor = new Constructor(TestFlyConfig.class, loaderOptions);
        constructor.setPropertyUtils(new LenientPropertyUtils());

        Yaml yaml = new Yaml(constructor);
        TestFlyConfig config = yaml.load(inputStream);

        // Resolve ${VAR} placeholders across ALL string fields in the config tree
        resolveEnvPlaceholders(config);

        validate(config);

        return config;
    }

    /**
     * SnakeYAML's default {@link Constructor} rejects any YAML key without a
     * matching bean property, so a single typo in {@code testfly.yml} aborts the
     * whole suite with a cryptic {@code ConstructorException}. This skips
     * unknown keys instead and reports each one with its owning bean, keeping
     * the rest of the configuration usable.
     *
     * <p>
     * Detection compares against the bean's real property set rather than
     * SnakeYAML's {@code MissingProperty} sentinel, so it does not depend on
     * that class staying reachable in future SnakeYAML releases.
     */
    private static final class LenientPropertyUtils extends PropertyUtils {

        LenientPropertyUtils() {
            setSkipMissingProperties(true);
        }

        @Override
        public Property getProperty(Class<?> type, String name) {
            for (Property candidate : super.getProperties(type)) {
                if (candidate.getName().equalsIgnoreCase(name)
                        || candidate.getName().equalsIgnoreCase(name.replace("-", ""))) {
                    return candidate;
                }
            }
            Property property = super.getProperty(type, name);
            if (property != null && !isKnownProperty(type, name)) {
                System.err.println("[TestFly] Unknown config key '" + name + "' on "
                        + type.getSimpleName() + " — ignored. Check testfly.yml for a typo.");
            }
            return property;
        }

        private boolean isKnownProperty(Class<?> type, String name) {
            for (Property candidate : super.getProperties(type)) {
                if (candidate.getName().equalsIgnoreCase(name)
                        || candidate.getName().equalsIgnoreCase(name.replace("-", ""))) {
                    return true;
                }
            }
            return false;
        }
    }

    // ── Recursive env-var resolution ────────────────────────────────────────

    /**
     * Walks every field of {@code obj} (including nested POJOs, {@link List
     * List&lt;String&gt;},
     * and {@link Map Map&lt;String, String&gt;}) and replaces {@code ${VAR}} /
     * {@code ${VAR:-default}} tokens via {@link DotEnvLoader#resolveAll(String)}.
     */
    private static void resolveEnvPlaceholders(Object obj) {
        if (obj == null) {
            return;
        }
        resolveFields(obj, new java.util.IdentityHashMap<>());
    }

    /**
     * Core recursive walker. Uses an {@link java.util.IdentityHashMap} guard to
     * avoid infinite loops on cyclic object graphs (defensive — the config POJOs
     * are acyclic, but this costs almost nothing).
     */
    @SuppressWarnings("unchecked")
    private static void resolveFields(Object obj, java.util.Map<Object, Boolean> visited) {
        if (obj == null || visited.containsKey(obj)) {
            return;
        }
        visited.put(obj, Boolean.TRUE);

        Class<?> clazz = obj.getClass();

        // Handle List<String> — resolve each element in place
        if (obj instanceof List) {
            List<Object> list = (List<Object>) obj;
            for (int i = 0; i < list.size(); i++) {
                Object item = list.get(i);
                if (item instanceof String) {
                    String resolved = DotEnvLoader.resolveAll((String) item);
                    if (resolved != null && !resolved.equals(item)) {
                        list.set(i, resolved);
                    }
                } else if (item != null && !isJdkType(item.getClass())) {
                    resolveFields(item, visited);
                }
            }
            return;
        }

        // Handle Map<String, String> and Map<String, Object>
        if (obj instanceof Map) {
            Map<Object, Object> map = (Map<Object, Object>) obj;
            // Collect keys first to avoid ConcurrentModificationException
            for (Object key : new java.util.ArrayList<>(map.keySet())) {
                Object value = map.get(key);
                if (value instanceof String) {
                    String resolved = DotEnvLoader.resolveAll((String) value);
                    if (resolved != null && !resolved.equals(value)) {
                        map.put(key, resolved);
                    }
                } else if (value != null && !isJdkType(value.getClass())) {
                    resolveFields(value, visited);
                }
            }
            return;
        }

        // Walk declared fields of the object's class and its superclasses
        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                int mods = field.getModifiers();
                if (Modifier.isStatic(mods) || Modifier.isFinal(mods)) {
                    continue;
                }

                Class<?> type = field.getType();

                // Resolve String fields
                if (type == String.class) {
                    try {
                        field.setAccessible(true);
                        String value = (String) field.get(obj);
                        if (value != null && value.contains("${")) {
                            String resolved = DotEnvLoader.resolveAll(value);
                            if (resolved != null && !resolved.equals(value)) {
                                field.set(obj, resolved);
                            }
                        }
                    } catch (IllegalAccessException e) {
                        // skip inaccessible fields silently
                    }
                    continue;
                }

                // Recurse into List fields
                if (List.class.isAssignableFrom(type)) {
                    try {
                        field.setAccessible(true);
                        Object listObj = field.get(obj);
                        if (listObj != null) {
                            resolveFields(listObj, visited);
                        }
                    } catch (IllegalAccessException e) {
                        // skip
                    }
                    continue;
                }

                // Recurse into Map fields
                if (Map.class.isAssignableFrom(type)) {
                    try {
                        field.setAccessible(true);
                        Object mapObj = field.get(obj);
                        if (mapObj != null) {
                            resolveFields(mapObj, visited);
                        }
                    } catch (IllegalAccessException e) {
                        // skip
                    }
                    continue;
                }

                // Recurse into nested POJO fields (non-primitive, non-JDK types)
                if (!type.isPrimitive() && !isJdkType(type) && !type.isEnum()) {
                    try {
                        field.setAccessible(true);
                        Object nested = field.get(obj);
                        if (nested != null) {
                            resolveFields(nested, visited);
                        }
                    } catch (IllegalAccessException e) {
                        // skip
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
    }

    /**
     * Returns {@code true} for types from {@code java.*} / {@code javax.*} that
     * should be treated as opaque leaves rather than recursed into.
     */
    private static boolean isJdkType(Class<?> type) {
        if (type.isArray()) {
            return true;
        }
        String name = type.getName();
        return name.startsWith("java.") || name.startsWith("javax.");
    }

    private static void validate(TestFlyConfig config) {
        Objects.requireNonNull(config, "Configuration must not be null");

        if (config.getBrowser() == null) {
            throw new IllegalStateException("Browser configuration must be specified");
        }
        // browser.name is required unless browser.matrix provides the list of browsers
        // to run
        boolean matrixConfigured = config.getBrowser().getMatrix() != null
                && !config.getBrowser().getMatrix().isEmpty();
        if (!matrixConfigured && config.getBrowser().getName() == null) {
            throw new IllegalStateException(
                    "browser.name must be specified (or use browser.matrix for multi-browser runs)");
        }

        if (config.getExecution() == null || config.getExecution().getMode() == null) {
            throw new IllegalStateException("Execution mode must be specified");
        }

        String mode = config.getExecution().getMode();
        if (!"local".equalsIgnoreCase(mode) && !"remote".equalsIgnoreCase(mode)
                && !"browserstack".equalsIgnoreCase(mode) && !"saucelabs".equalsIgnoreCase(mode)) {
            throw new IllegalStateException(
                    "execution.mode must be 'local', 'remote', 'browserstack', or 'saucelabs', got: '" + mode + "'");
        }

        if (config.getTimeouts() == null
                || config.getTimeouts().getExplicit() <= 0
                || config.getTimeouts().getPageLoad() <= 0) {
            throw new IllegalStateException(
                    "timeouts.explicit and timeouts.pageLoad must be configured with positive values");
        }
    }
}
