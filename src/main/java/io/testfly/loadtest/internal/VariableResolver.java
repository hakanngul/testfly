package io.testfly.loadtest.internal;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves {@code ${variable}} placeholders in strings using a variable map.
 *
 * <p>
 * Used by {@link JdkLoadEngine} to substitute feeder values into request
 * paths, headers, query parameters, and bodies before sending.
 *
 * <pre>
 * Map&lt;String, Object&gt; vars = Map.of("userId", 42, "token", "abc123");
 * VariableResolver.resolve("/api/users/${userId}", vars); // → "/api/users/42"
 * VariableResolver.resolve("Bearer ${token}", vars); // → "Bearer abc123"
 * </pre>
 *
 * <p>
 * Unresolved variables are left as-is (e.g. {@code ${missing}} stays
 * {@code ${missing}}) so the request still executes and the failure is
 * visible in the response rather than silently swallowed.
 */
public final class VariableResolver {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    private VariableResolver() {
    }

    /**
     * Replaces all {@code ${key}} occurrences in {@code template} with values
     * from {@code variables}. Unresolved keys are left as-is.
     *
     * @param template  the string containing placeholders (may be {@code null})
     * @param variables the variable map (may be {@code null} or empty)
     * @return the resolved string, or the original template if no variables
     */
    public static String resolve(String template, Map<String, Object> variables) {
        if (template == null || variables == null || variables.isEmpty()) {
            return template;
        }
        Matcher matcher = VAR_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            Object value = variables.get(key);
            String replacement = value != null
                    ? Matcher.quoteReplacement(value.toString())
                    : Matcher.quoteReplacement(matcher.group(0));
            matcher.appendReplacement(sb, replacement);
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Resolves variables in a request body object. Handles {@code String} bodies
     * directly; for {@code Map} bodies, resolves each string value recursively.
     *
     * @param body      the request body (String, Map, or other object)
     * @param variables the variable map
     * @return the resolved body
     */
    @SuppressWarnings("unchecked")
    public static Object resolveBody(Object body, Map<String, Object> variables) {
        if (body == null || variables == null || variables.isEmpty()) {
            return body;
        }
        if (body instanceof String s) {
            return resolve(s, variables);
        }
        if (body instanceof Map<?, ?> map) {
            Map<String, Object> resolved = new java.util.LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = entry.getKey() != null ? entry.getKey().toString() : null;
                Object value = entry.getValue();
                if (value instanceof String s) {
                    resolved.put(key, resolve(s, variables));
                } else if (value instanceof Map) {
                    resolved.put(key, resolveBody(value, variables));
                } else {
                    resolved.put(key, value);
                }
            }
            return resolved;
        }
        return body;
    }
}
