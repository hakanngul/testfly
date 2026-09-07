package io.testfly.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads environment variables from a {@code .env} file in the project root so
 * that
 * {@code ${VAR}} placeholders in {@code testfly.yml} are resolved even when the
 * shell
 * environment has not been sourced.
 *
 * <p>
 * Resolution priority (highest → lowest):
 * <ol>
 * <li>{@code .env} file in the working directory</li>
 * <li>Shell environment variable ({@code System.getenv})</li>
 * <li>System property ({@code -D})</li>
 * <li>The {@code ${VAR:-default}} fallback, when present</li>
 * </ol>
 *
 * <p>
 * {@code .env} deliberately wins over the shell so that a stale exported
 * credential
 * cannot silently override the project's checked-out configuration. Values read
 * from
 * {@code .env} are also published as system properties when the shell has not
 * already
 * set them, which keeps {@code -DVAR} style access working for downstream
 * consumers.
 *
 * <p>
 * Supported syntax:
 * 
 * <pre>
 *   # comment
 *   KEY=value
 *   KEY="quoted value"
 *   KEY='single quoted'
 *   KEY=value  # inline comment
 * </pre>
 *
 * <p>
 * The loader is idempotent — calling it more than once is a no-op.
 */
public final class DotEnvLoader {

    private static volatile boolean loaded;

    /**
     * Values read from {@code .env}, kept separate from system properties so they
     * can win over the shell.
     */
    private static final Map<String, String> DOTENV_VARS = new ConcurrentHashMap<>();

    private DotEnvLoader() {
        // utility class
    }

    /**
     * Loads {@code .env} from the working directory if it exists.
     * Safe to call multiple times — subsequent calls are no-ops.
     */
    public static void load() {
        if (loaded)
            return;
        synchronized (DotEnvLoader.class) {
            if (loaded)
                return;
            Path envFile = Paths.get(System.getProperty("user.dir"), ".env");
            if (Files.exists(envFile) && Files.isRegularFile(envFile)) {
                int count = parse(envFile);
                if (count > 0) {
                    System.out.println("[TestFly] Loaded " + count
                            + " variable(s) from .env");
                }
            }
            loaded = true;
        }
    }

    /**
     * Returns the value declared in {@code .env} for {@code key}, or {@code null}
     * when the
     * key is absent. Never consults the shell environment or system properties,
     * which makes
     * this the way to ask "what does the project's .env say?" explicitly.
     *
     * @param key variable name, e.g. {@code AI_API_KEY}
     * @return the {@code .env} value, or {@code null}
     */
    public static String fromDotEnv(String key) {
        if (key == null)
            return null;
        load();
        return DOTENV_VARS.get(key);
    }

    /**
     * Resolves a {@code ${VAR}} or {@code ${VAR:-default}} placeholder against
     * {@code .env}, the shell environment, and system properties, in that order.
     *
     * <p>
     * This method only resolves when the <em>entire</em> string is a single
     * {@code ${...}} placeholder. For strings that contain one or more embedded
     * placeholders (e.g. {@code "https://${HOST}:${PORT}/api"}), use
     * {@link #resolveAll(String)} instead.
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

        load();

        // Priority: .env > shell environment > system property > default
        String resolved = DOTENV_VARS.get(varName);
        if (isBlank(resolved)) {
            resolved = System.getenv(varName);
        }
        if (isBlank(resolved)) {
            resolved = System.getProperty(varName);
        }
        if (isBlank(resolved)) {
            resolved = defaultValue;
        }
        return resolved;
    }

    /** Pattern that matches {@code ${VAR}} or {@code ${VAR:-default}} tokens. */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]+)}");

    /**
     * Resolves <em>all</em> {@code ${VAR}} and {@code ${VAR:-default}} placeholders
     * embedded anywhere within {@code value}. Unlike {@link #resolve(String)},
     * which only handles a string that is entirely a single placeholder, this
     * method handles mixed content such as
     * {@code "https://${HOST}:${PORT:-8080}/api"}.
     *
     * <p>
     * If a variable cannot be resolved and no default is provided, the
     * original {@code ${...}} token is left in place so the caller can detect
     * unresolved references.
     *
     * @param value the raw string; may be {@code null}
     * @return the string with all resolvable placeholders replaced, or {@code null}
     */
    public static String resolveAll(String value) {
        if (value == null || !value.contains("${")) {
            return value;
        }

        load();

        Matcher matcher = PLACEHOLDER.matcher(value);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String inner = matcher.group(1);
            String varName;
            String defaultValue = null;
            int defaultIdx = inner.indexOf(":-");
            if (defaultIdx >= 0) {
                varName = inner.substring(0, defaultIdx).trim();
                defaultValue = inner.substring(defaultIdx + 2).trim();
            } else {
                varName = inner.trim();
            }

            // Priority: .env > shell environment > system property > default
            String resolved = DOTENV_VARS.get(varName);
            if (isBlank(resolved)) {
                resolved = System.getenv(varName);
            }
            if (isBlank(resolved)) {
                resolved = System.getProperty(varName);
            }
            if (isBlank(resolved)) {
                resolved = defaultValue;
            }

            if (resolved != null) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(resolved));
            }
            // If still null, leave the original ${...} token untouched
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static int parse(Path envFile) {
        int count = 0;
        try (BufferedReader reader = Files.newBufferedReader(envFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#"))
                    continue;

                int eq = line.indexOf('=');
                if (eq <= 0)
                    continue;

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

                DOTENV_VARS.put(key, value);
                count++;

                // Publish as a system property too, unless the shell already defines it,
                // so that System.getProperty / -D style access keeps working.
                if (System.getenv(key) == null) {
                    System.setProperty(key, value);
                }
            }
        } catch (IOException e) {
            System.err.println("[TestFly] Warning: failed to read .env file: " + e.getMessage());
        }
        return count;
    }
}
