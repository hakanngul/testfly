package io.testfly.loadtest;

import io.testfly.api.TestFlyApi;

/**
 * A response check condition for {@link LoadStep}.
 *
 * <pre>
 * .check(status().is(200))
 * .check(status().in(200, 201))
 * .check(jsonPath("$.id").exists())
 * .check(jsonPath("$.id").is(5))
 * </pre>
 *
 * <p>Semantics are identical in the JDK and Gatling engines:
 * <ul>
 * <li>If a step has no {@code status().is(..)}/{@code status().in(..)} check, the default status check
 * applies: a response is OK only if its status is 2xx (200..299) or 304.</li>
 * <li>{@code matches(regex)} — on both builders — is a whole-body regex search (the regex must be
 * found somewhere in the response body; no implicit DOTALL).</li>
 * <li>JSON checks compare the string form of the value found at the JSONPath (JSON number {@code 5} is
 * {@code "5"}) with {@code String.valueOf(expected)}.</li>
 * </ul>
 */
@TestFlyApi(since = "1.1.0")
public final class CheckCondition {

    /**
     * Check type. New constants are only ever appended.
     *
     * <ul>
     * <li>{@code STATUS_IS} / {@code STATUS_IN} — HTTP status equals / is one of the expected codes</li>
     * <li>{@code JSON_EXISTS} — a value exists at the JSONPath {@link #target()}</li>
     * <li>{@code HEADER_PRESENT} — the response header {@link #target()} is present</li>
     * <li>{@code BODY_MATCHES} — the regex {@link #expected()} is found in the response body</li>
     * <li>{@code JSON_EQUALS} — the value at the JSONPath {@link #target()} equals {@link #expected()}</li>
     * <li>{@code JSON_IN} — the value at the JSONPath {@link #target()} is one of {@link #expected()}</li>
     * </ul>
     */
    public enum Type {
        STATUS_IS, STATUS_IN, JSON_EXISTS, HEADER_PRESENT, BODY_MATCHES,
        /** @since 1.1.0 */
        JSON_EQUALS,
        /** @since 1.1.0 */
        JSON_IN
    }

    private final Type type;
    private final Object expected;
    private final String target;

    private CheckCondition(Type type, Object expected, String target) {
        this.type = type;
        this.expected = expected;
        this.target = target;
    }

    public Type type() { return type; }
    public Object expected() { return expected; }
    public String target() { return target; }

    // ── Builders ─────────────────────────────────────────────────────────

    static Builder statusBuilder() {
        return new Builder(Type.STATUS_IS, null);
    }

    static Builder jsonPathBuilder(String path) {
        return new Builder(Type.JSON_EXISTS, path);
    }

    /**
     * Fluent builder for check conditions. The builder remembers its kind: a
     * {@code status()} builder produces status checks, a {@code jsonPath(..)} builder
     * produces JSON value checks on its path.
     */
    public static final class Builder {
        private final Type defaultType;
        private final String target;

        Builder(Type defaultType, String target) {
            this.defaultType = defaultType;
            this.target = target;
        }

        private boolean isJsonPath() {
            return defaultType == Type.JSON_EXISTS;
        }

        /**
         * {@code status().is(code)}: the HTTP status equals {@code value}.
         * {@code jsonPath(p).is(value)}: the value at {@code p} equals {@code value}
         * (string comparison, JSON number {@code 5} equals {@code 5}).
         */
        public CheckCondition is(int value) {
            return isJsonPath()
                    ? new CheckCondition(Type.JSON_EQUALS, value, target)
                    : new CheckCondition(Type.STATUS_IS, value, target);
        }

        /**
         * {@code status().in(codes)}: the HTTP status is one of {@code values}.
         * {@code jsonPath(p).in(values)}: the value at {@code p} is one of {@code values}.
         */
        public CheckCondition in(int... values) {
            return isJsonPath()
                    ? new CheckCondition(Type.JSON_IN, values, target)
                    : new CheckCondition(Type.STATUS_IN, values, target);
        }

        /**
         * {@code jsonPath(p).exists()}: a value exists at the JSONPath.
         *
         * @throws IllegalStateException on a {@code status()} builder
         */
        public CheckCondition exists() {
            if (!isJsonPath()) {
                throw new IllegalStateException("exists() is only valid for jsonPath(...)");
            }
            return new CheckCondition(Type.JSON_EXISTS, true, target);
        }

        /**
         * Checks that the regex is found in the <em>whole response body</em>
         * ({@code Matcher.find()}, no implicit DOTALL). On a {@code jsonPath(..)} builder the
         * path is not used for this check.
         */
        public CheckCondition matches(String regex) {
            return new CheckCondition(Type.BODY_MATCHES, regex, target);
        }
    }
}
