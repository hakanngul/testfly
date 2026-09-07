package io.testfly.loadtest;

import io.testfly.api.TestFlyApi;

/**
 * A response check condition for {@link LoadStep}.
 *
 * <pre>
 * .check(status().is(200))
 * .check(status().in(200, 201))
 * .check(jsonPath("$.id").exists())
 * </pre>
 */
@TestFlyApi(since = "1.1.0")
public final class CheckCondition {

    /** Check type: status code, JSONPath existence, header presence, body regex. */
    public enum Type {
        STATUS_IS, STATUS_IN, JSON_EXISTS, HEADER_PRESENT, BODY_MATCHES
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
     * Fluent builder for check conditions.
     */
    public static final class Builder {
        private final Type defaultType;
        private final String target;

        Builder(Type defaultType, String target) {
            this.defaultType = defaultType;
            this.target = target;
        }

        /** Checks that the value equals the expected result. */
        public CheckCondition is(int value) {
            return new CheckCondition(Type.STATUS_IS, value, target);
        }

        /** Checks that the value is one of the given results. */
        public CheckCondition in(int... values) {
            return new CheckCondition(Type.STATUS_IN, values, target);
        }

        /** Checks that the JSONPath exists in the response body. */
        public CheckCondition exists() {
            return new CheckCondition(Type.JSON_EXISTS, true, target);
        }

        /** Checks that the response body matches the given regex. */
        public CheckCondition matches(String regex) {
            return new CheckCondition(Type.BODY_MATCHES, regex, target);
        }
    }
}
