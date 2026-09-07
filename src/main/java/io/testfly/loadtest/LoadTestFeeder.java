package io.testfly.loadtest;

import io.testfly.api.TestFlyApi;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Data feeder for load-test scenarios — supplies variable values per virtual user iteration.
 *
 * <pre>
 * .feed(LoadTestFeeder.csv("users.csv"))
 * .feed(LoadTestFeeder.json("products.json"))
 * .feed(LoadTestFeeder.random("userId", 1, 10000))
 * .feed(LoadTestFeeder.uuid("requestId"))
 * .feed(LoadTestFeeder.sequence("orderId", 1000, 1))
 * </pre>
 */
@TestFlyApi(since = "1.1.0")
public abstract class LoadTestFeeder {

    /** Returns the next set of variable bindings, or {@code null} when exhausted. */
    public abstract Map<String, Object> next();

    /** Resets the feeder to its initial state. */
    public abstract void reset();

    /** Returns {@code true} if this feeder can supply more values. */
    public abstract boolean hasNext();

    // ── Factory methods ──────────────────────────────────────────────────

    /**
     * Creates a CSV file feeder. Each row becomes a variable map
     * (column headers → values). Rows are cycled round-robin.
     *
     * <p>Full implementation in Sprint 2.
     */
    public static LoadTestFeeder csv(String path) {
        return new CsvFeeder(path);
    }

    /**
     * Creates a JSON array file feeder. Each element becomes a variable map.
     *
     * <p>Full implementation in Sprint 2.
     */
    public static LoadTestFeeder json(String path) {
        return new JsonFeeder(path);
    }

    /** Creates a random-integer feeder for the given variable name. */
    public static LoadTestFeeder random(String variable, int min, int max) {
        return new RandomFeeder(variable, min, max);
    }

    /** Creates a UUID feeder for the given variable name. */
    public static LoadTestFeeder uuid(String variable) {
        return new UuidFeeder(variable);
    }

    /** Creates a sequential-integer feeder for the given variable name. */
    public static LoadTestFeeder sequence(String variable, long start, long step) {
        return new SequenceFeeder(variable, start, step);
    }

    /** Creates a constant-value feeder for the given variable name. */
    public static LoadTestFeeder constant(String variable, String value) {
        return new ConstantFeeder(variable, value);
    }

    // ── Built-in implementations ─────────────────────────────────────────

    private static final class RandomFeeder extends LoadTestFeeder {
        private final String variable;
        private final int min;
        private final int max;
        private final java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current();

        RandomFeeder(String variable, int min, int max) {
            this.variable = variable;
            this.min = min;
            this.max = max;
        }

        @Override
        public Map<String, Object> next() {
            return Map.of(variable, java.util.concurrent.ThreadLocalRandom.current().nextInt(min, max + 1));
        }

        @Override public void reset() {}
        @Override public boolean hasNext() { return true; }
    }

    private static final class UuidFeeder extends LoadTestFeeder {
        private final String variable;

        UuidFeeder(String variable) { this.variable = variable; }

        @Override
        public Map<String, Object> next() {
            return Map.of(variable, UUID.randomUUID().toString());
        }

        @Override public void reset() {}
        @Override public boolean hasNext() { return true; }
    }

    private static final class SequenceFeeder extends LoadTestFeeder {
        private final String variable;
        private final long start;
        private final long step;
        private final AtomicLong current;

        SequenceFeeder(String variable, long start, long step) {
            this.variable = variable;
            this.start = start;
            this.step = step;
            this.current = new AtomicLong(start);
        }

        @Override
        public Map<String, Object> next() {
            return Map.of(variable, current.getAndAdd(step));
        }

        @Override public void reset() { current.set(start); }
        @Override public boolean hasNext() { return true; }
    }

    private static final class ConstantFeeder extends LoadTestFeeder {
        private final String variable;
        private final String value;

        ConstantFeeder(String variable, String value) {
            this.variable = variable;
            this.value = value;
        }

        @Override
        public Map<String, Object> next() {
            return Map.of(variable, value);
        }

        @Override public void reset() {}
        @Override public boolean hasNext() { return true; }
    }

    /** Placeholder — full CSV parsing in Sprint 2. */
    private static final class CsvFeeder extends LoadTestFeeder {
        private final String path;

        CsvFeeder(String path) { this.path = path; }

        @Override
        public Map<String, Object> next() {
            throw new UnsupportedOperationException(
                "[LoadTest] CSV feeder not yet implemented. Path: " + path +
                ". Will be available in Sprint 2.");
        }

        @Override public void reset() {}
        @Override public boolean hasNext() { return false; }
    }

    /** Placeholder — full JSON parsing in Sprint 2. */
    private static final class JsonFeeder extends LoadTestFeeder {
        private final String path;

        JsonFeeder(String path) { this.path = path; }

        @Override
        public Map<String, Object> next() {
            throw new UnsupportedOperationException(
                "[LoadTest] JSON feeder not yet implemented. Path: " + path +
                ". Will be available in Sprint 2.");
        }

        @Override public void reset() {}
        @Override public boolean hasNext() { return false; }
    }
}
