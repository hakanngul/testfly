package io.testfly.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads environment variables from a {@code .env} file in the project root
 * into {@link System#getProperties()} so that {@code ${VAR}} placeholders
 * in {@code testfly.yml} are resolved even when the shell environment
 * has not been sourced.
 *
 * <p>Resolution priority (highest → lowest):
 * <ol>
 *   <li>Shell environment variable ({@code System.getenv})</li>
 *   <li>System property (set here from {@code .env}, or via {@code -D})</li>
 * </ol>
 *
 * <p>Supported syntax:
 * <pre>
 *   # comment
 *   KEY=value
 *   KEY="quoted value"
 *   KEY='single quoted'
 *   KEY=value  # inline comment
 * </pre>
 *
 * <p>The loader is idempotent — calling it more than once is a no-op.
 */
public final class DotEnvLoader {

    private static volatile boolean loaded;

    private DotEnvLoader() {
        // utility class
    }

    /**
     * Loads {@code .env} from the working directory if it exists.
     * Safe to call multiple times — subsequent calls are no-ops.
     */
    public static void load() {
        if (loaded) return;
        synchronized (DotEnvLoader.class) {
            if (loaded) return;
            Path envFile = Paths.get(System.getProperty("user.dir"), ".env");
            if (Files.exists(envFile) && Files.isRegularFile(envFile)) {
                List<String> vars = parse(envFile);
                if (!vars.isEmpty()) {
                    System.out.println("[TestFly] Loaded " + vars.size()
                            + " variable(s) from .env");
                }
            }
            loaded = true;
        }
    }

    /**
     * Resolves a {@code ${VAR}} or {@code ${VAR:-default}} placeholder
     * against the environment + system properties.
     *
     * @param value the raw string from YAML config; may be {@code null}
     * @return the resolved value, or the original string if no placeholder is found
     */
    public static String resolve(String value) {
        if (value == null || !value.startsWith("${") || !value.endsWith("}")) {
            return value;
        }
        String inner = value.substring(2, value.length() - 1);

        // Handle ${VAR:-default} syntax
        String varName;
        String defaultValue = null;
        int defaultIdx = inner.indexOf(":-");
        if (defaultIdx >= 0) {
            varName = inner.substring(0, defaultIdx).trim();
            defaultValue = inner.substring(defaultIdx + 2).trim();
        } else {
            varName = inner.trim();
        }

        // Priority: env var > system property > default
        String resolved = System.getenv(varName);
        if (resolved == null) {
            resolved = System.getProperty(varName);
        }
        if (resolved == null) {
            resolved = defaultValue;
        }
        return resolved;
    }

    private static List<String> parse(Path envFile) {
        List<String> loadedVars = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(envFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                int eq = line.indexOf('=');
                if (eq <= 0) continue;

                String key = line.substring(0, eq).trim();
                String value = line.substring(eq + 1).trim();

                // Strip quotes
                if (value.length() >= 2
                        && ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'")))) {
                    value = value.substring(1, value.length() - 1);
                }

                // Strip inline comments (only for unquoted values)
                int hashIdx = value.indexOf(" #");
                if (hashIdx >= 0) {
                    value = value.substring(0, hashIdx).trim();
                }

                // Only set if the env var is not already set in the shell
                if (System.getenv(key) == null) {
                    System.setProperty(key, value);
                    loadedVars.add(key);
                }
            }
        } catch (IOException e) {
            System.err.println("[TestFly] Warning: failed to read .env file: " + e.getMessage());
        }
        return loadedVars;
    }
}
