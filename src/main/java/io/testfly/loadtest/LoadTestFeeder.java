package io.testfly.loadtest;

import io.testfly.api.TestFlyApi;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Data feeder for load-test scenarios — supplies variable values per virtual
 * user iteration.
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

    /**
     * Returns the next set of variable bindings, or {@code null} when exhausted.
     */
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
     * <p>
     * Full implementation in Sprint 2.
     */
    public static LoadTestFeeder csv(String path) {
        return new CsvFeeder(path);
    }

    /**
     * Creates a JSON array file feeder. Each element becomes a variable map.
     *
     * <p>
     * Full implementation in Sprint 2.
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

        @Override
        public void reset() {
        }

        @Override
        public boolean hasNext() {
            return true;
        }
    }

    private static final class UuidFeeder extends LoadTestFeeder {
        private final String variable;

        UuidFeeder(String variable) {
            this.variable = variable;
        }

        @Override
        public Map<String, Object> next() {
            return Map.of(variable, UUID.randomUUID().toString());
        }

        @Override
        public void reset() {
        }

        @Override
        public boolean hasNext() {
            return true;
        }
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

        @Override
        public void reset() {
            current.set(start);
        }

        @Override
        public boolean hasNext() {
            return true;
        }
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

        @Override
        public void reset() {
        }

        @Override
        public boolean hasNext() {
            return true;
        }
    }

    /**
     * CSV file feeder — reads headers from the first row, cycles rows round-robin.
     * Thread-safe via synchronized {@code next()}.
     */
    private static final class CsvFeeder extends LoadTestFeeder {
        private final String path;
        private final List<Map<String, Object>> rows;
        private final AtomicInteger index = new AtomicInteger(0);

        CsvFeeder(String path) {
            this.path = path;
            this.rows = parseCsv(path);
        }

        @Override
        public synchronized Map<String, Object> next() {
            if (rows.isEmpty())
                return Map.of();
            int i = index.getAndIncrement() % rows.size();
            return rows.get(i);
        }

        @Override
        public void reset() {
            index.set(0);
        }

        @Override
        public boolean hasNext() {
            return !rows.isEmpty();
        }

        private static List<Map<String, Object>> parseCsv(String path) {
            List<Map<String, Object>> result = new ArrayList<>();
            java.io.File file = resolveFile(path);
            if (file == null || !file.exists()) {
                System.err.println("[LoadTest] CSV feeder file not found: " + path);
                return result;
            }
            try (var reader = new java.io.BufferedReader(new java.io.FileReader(file))) {
                String headerLine = reader.readLine();
                if (headerLine == null)
                    return result;

                String[] headers = headerLine.split(",", -1);
                for (int i = 0; i < headers.length; i++) {
                    headers[i] = headers[i].trim().replaceAll("^\"|\"$", "");
                }

                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank())
                        continue;
                    String[] values = line.split(",", -1);
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 0; i < headers.length && i < values.length; i++) {
                        row.put(headers[i], values[i].trim().replaceAll("^\"|\"$", ""));
                    }
                    result.add(row);
                }
            } catch (java.io.IOException e) {
                System.err.println("[LoadTest] Failed to read CSV feeder: " + path + " — " + e.getMessage());
            }
            return result;
        }
    }

    /**
     * JSON array file feeder — each element becomes a variable map.
     * Uses Jackson (already a TestFly dependency) for parsing.
     */
    private static final class JsonFeeder extends LoadTestFeeder {
        private final String path;
        private final List<Map<String, Object>> rows;
        private final AtomicInteger index = new AtomicInteger(0);

        JsonFeeder(String path) {
            this.path = path;
            this.rows = parseJson(path);
        }

        @Override
        public synchronized Map<String, Object> next() {
            if (rows.isEmpty())
                return Map.of();
            int i = index.getAndIncrement() % rows.size();
            return rows.get(i);
        }

        @Override
        public void reset() {
            index.set(0);
        }

        @Override
        public boolean hasNext() {
            return !rows.isEmpty();
        }

        @SuppressWarnings("unchecked")
        private static List<Map<String, Object>> parseJson(String path) {
            List<Map<String, Object>> result = new ArrayList<>();
            java.io.File file = resolveFile(path);
            if (file == null || !file.exists()) {
                System.err.println("[LoadTest] JSON feeder file not found: " + path);
                return result;
            }
            try {
                var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                Object root = mapper.readValue(file, Object.class);
                if (root instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> map) {
                            result.add((Map<String, Object>) map);
                        }
                    }
                } else if (root instanceof Map<?, ?> map) {
                    result.add((Map<String, Object>) map);
                }
            } catch (java.io.IOException e) {
                System.err.println("[LoadTest] Failed to read JSON feeder: " + path + " — " + e.getMessage());
            }
            return result;
        }
    }

    // ── File resolution helper ───────────────────────────────────────────

    private static java.io.File resolveFile(String path) {
        // Try classpath first
        var url = LoadTestFeeder.class.getClassLoader().getResource(path);
        if (url != null) {
            try {
                return new java.io.File(url.toURI());
            } catch (Exception e) {
                // fall through
            }
        }
        // Try filesystem
        java.io.File file = new java.io.File(path);
        if (file.exists())
            return file;
        // Try src/test/resources
        file = new java.io.File("src/test/resources/" + path);
        if (file.exists())
            return file;
        return null;
    }
}
