package io.testfly.sharding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.testfly.api.TestFlyApi;

import java.io.File;
import java.lang.reflect.Method;
import java.util.*;

/**
 * SmartTestSharder implements optimal test partitioning across CI/CD worker nodes
 * using the Longest Processing Time (LPT) first Bin-Packing algorithm.
 *
 * It reads historical execution durations from {@code target/testfly-metrics.json}
 * or annotations, sorts tests descending by weight, and greedily assigns each test
 * to the shard with the lowest accumulated runtime.
 */
@TestFlyApi(since = "1.2.0")
public final class SmartTestSharder {

    public static final long DEFAULT_TEST_DURATION_MS = 5000L;

    private SmartTestSharder() {
    }

    /**
     * Represents an individual schedulable test unit with its estimated duration.
     */
    public static final class ShardedItem<T> {
        private final String id;
        private final long estimatedDurationMs;
        private final T payload;

        public ShardedItem(String id, long estimatedDurationMs, T payload) {
            this.id = Objects.requireNonNull(id, "id must not be null");
            this.estimatedDurationMs = Math.max(1L, estimatedDurationMs);
            this.payload = payload;
        }

        public String getId() {
            return id;
        }

        public long getEstimatedDurationMs() {
            return estimatedDurationMs;
        }

        public T getPayload() {
            return payload;
        }

        @Override
        public String toString() {
            return id + " (" + estimatedDurationMs + "ms)";
        }
    }

    /**
     * A single worker shard bucket containing assigned test items.
     */
    public static final class ShardPartition<T> {
        private final int index;
        private final List<ShardedItem<T>> items = new ArrayList<>();
        private long totalEstimatedDurationMs = 0L;

        public ShardPartition(int index) {
            this.index = index;
        }

        public int getIndex() {
            return index;
        }

        public List<ShardedItem<T>> getItems() {
            return Collections.unmodifiableList(items);
        }

        public List<T> getPayloads() {
            List<T> list = new ArrayList<>(items.size());
            for (ShardedItem<T> it : items) {
                list.add(it.getPayload());
            }
            return list;
        }

        public long getTotalEstimatedDurationMs() {
            return totalEstimatedDurationMs;
        }

        public void addItem(ShardedItem<T> item) {
            items.add(item);
            totalEstimatedDurationMs += item.getEstimatedDurationMs();
        }

        public int getItemCount() {
            return items.size();
        }
    }

    /**
     * The overall distribution plan across all worker nodes.
     */
    public static final class ShardingPlan<T> {
        private final int totalShards;
        private final String strategy;
        private final List<ShardPartition<T>> partitions;
        private final long totalDurationMs;
        private final long makespanMs;
        private final double balanceEfficiency;

        public ShardingPlan(int totalShards, String strategy, List<ShardPartition<T>> partitions) {
            this.totalShards = totalShards;
            this.strategy = strategy;
            this.partitions = partitions;

            long total = 0L;
            long max = 0L;
            for (ShardPartition<T> p : partitions) {
                total += p.getTotalEstimatedDurationMs();
                if (p.getTotalEstimatedDurationMs() > max) {
                    max = p.getTotalEstimatedDurationMs();
                }
            }
            this.totalDurationMs = total;
            this.makespanMs = max;

            double idealAverage = totalShards > 0 ? (double) total / totalShards : 0.0;
            this.balanceEfficiency = (max > 0) ? Math.min(100.0, (idealAverage / max) * 100.0) : 100.0;
        }

        public int getTotalShards() {
            return totalShards;
        }

        public String getStrategy() {
            return strategy;
        }

        public List<ShardPartition<T>> getPartitions() {
            return Collections.unmodifiableList(partitions);
        }

        public ShardPartition<T> getPartition(int index) {
            if (index < 0 || index >= partitions.size()) {
                throw new IndexOutOfBoundsException("Shard index " + index + " out of bounds for total " + totalShards);
            }
            return partitions.get(index);
        }

        public long getTotalDurationMs() {
            return totalDurationMs;
        }

        public long getMakespanMs() {
            return makespanMs;
        }

        public double getBalanceEfficiency() {
            return balanceEfficiency;
        }

        public String formatSummary(int currentShardIndex) {
            StringBuilder sb = new StringBuilder();
            sb.append("\n================================================================================\n");
            sb.append("✈  TestFly Smart Test Sharder — ").append(strategy.toUpperCase(Locale.ROOT)).append(" Bin-Packing\n");
            sb.append("================================================================================\n");
            int totalItems = partitions.stream().mapToInt(ShardPartition::getItemCount).sum();
            sb.append(String.format("Total Tests: %d | Total Shards: %d | Active Shard: %d of %d (Index: %d)\n",
                    totalItems, totalShards, currentShardIndex + 1, totalShards, currentShardIndex));
            sb.append(String.format("Suite Total Time: %s | Makespan (Bottleneck): %s | Efficiency: %.1f%%\n",
                    formatDuration(totalDurationMs), formatDuration(makespanMs), balanceEfficiency));
            sb.append("--------------------------------------------------------------------------------\n");

            for (ShardPartition<T> p : partitions) {
                double pct = totalDurationMs > 0 ? (p.getTotalEstimatedDurationMs() * 100.0) / totalDurationMs : 0.0;
                String mark = (p.getIndex() == currentShardIndex) ? " [CURRENT NODE]" : "";
                sb.append(String.format("Shard %-2d%-15s: %3d tests ~ %-9s (%.1f%% load)\n",
                        p.getIndex(), mark, p.getItemCount(), formatDuration(p.getTotalEstimatedDurationMs()), pct));
            }
            sb.append("================================================================================\n");
            return sb.toString();
        }
    }

    /**
     * Partitions items using LPT (Longest Processing Time) or Round-Robin.
     */
    public static <T> ShardingPlan<T> createPlan(List<ShardedItem<T>> items, int totalShards, String strategy) {
        int safeTotal = Math.max(1, totalShards);
        List<ShardPartition<T>> partitions = new ArrayList<>(safeTotal);
        for (int i = 0; i < safeTotal; i++) {
            partitions.add(new ShardPartition<>(i));
        }

        if (items == null || items.isEmpty()) {
            return new ShardingPlan<>(safeTotal, strategy != null ? strategy : "lpt", partitions);
        }

        String strat = strategy != null ? strategy.trim().toLowerCase(Locale.ROOT) : "lpt";

        if ("round-robin".equals(strat)) {
            for (int i = 0; i < items.size(); i++) {
                partitions.get(i % safeTotal).addItem(items.get(i));
            }
        } else {
            // Default: LPT (Longest Processing Time first)
            List<ShardedItem<T>> sorted = new ArrayList<>(items);
            sorted.sort((a, b) -> Long.compare(b.getEstimatedDurationMs(), a.getEstimatedDurationMs()));

            // Min-Heap / greedy bin packing
            PriorityQueue<ShardPartition<T>> minHeap = new PriorityQueue<>(
                    safeTotal,
                    (p1, p2) -> {
                        int cmp = Long.compare(p1.getTotalEstimatedDurationMs(), p2.getTotalEstimatedDurationMs());
                        if (cmp != 0) return cmp;
                        return Integer.compare(p1.getIndex(), p2.getIndex());
                    }
            );
            minHeap.addAll(partitions);

            for (ShardedItem<T> item : sorted) {
                ShardPartition<T> leastLoaded = minHeap.poll();
                leastLoaded.addItem(item);
                minHeap.add(leastLoaded);
            }
        }

        // Sort partitions by index before returning
        partitions.sort(Comparator.comparingInt(ShardPartition::getIndex));
        return new ShardingPlan<>(safeTotal, strat, partitions);
    }

    /**
     * Resolves historical execution durations from metrics JSON files or annotations.
     */
    public static Map<String, Long> loadHistoricalDurations(File metricsFile) {
        Map<String, Long> durations = new HashMap<>();
        if (metricsFile == null || !metricsFile.exists() || metricsFile.length() == 0) {
            return durations;
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(metricsFile);
            if (root.has("tests") && root.get("tests").isArray()) {
                for (JsonNode testNode : root.get("tests")) {
                    String testId = testNode.has("testId") ? testNode.get("testId").asText() : null;
                    String testClass = testNode.has("testClassName") ? testNode.get("testClassName").asText() : null;
                    long totalMs = testNode.has("totalMs") ? testNode.get("totalMs").asLong() : 0L;

                    if (testId != null && totalMs > 0) {
                        durations.put(testId, totalMs);
                    }
                    if (testClass != null && totalMs > 0) {
                        durations.merge(testClass, totalMs, Long::sum);
                    }
                }
            }
        } catch (Exception ignored) {
            // Graceful fallback to default durations
        }
        return durations;
    }

    /**
     * Resolves estimated duration for a test method or class.
     */
    public static long resolveDuration(String testId, Class<?> testClass, Method method, Map<String, Long> historicalDurations) {
        // 1. Check exact test ID match (e.g. com.example.LoginTest.testValidLogin)
        if (historicalDurations != null && testId != null && historicalDurations.containsKey(testId)) {
            return historicalDurations.get(testId);
        }

        // 2. Check method annotation @TestWeight
        if (method != null && method.isAnnotationPresent(TestWeight.class)) {
            TestWeight tw = method.getAnnotation(TestWeight.class);
            return (long) (tw.seconds() * 1000L * tw.multiplier());
        }

        // 3. Check class annotation @TestWeight
        if (testClass != null && testClass.isAnnotationPresent(TestWeight.class)) {
            TestWeight tw = testClass.getAnnotation(TestWeight.class);
            return (long) (tw.seconds() * 1000L * tw.multiplier());
        }

        // 4. Check class-level historical duration average
        if (historicalDurations != null && testClass != null && historicalDurations.containsKey(testClass.getName())) {
            return Math.max(1000L, historicalDurations.get(testClass.getName()) / 2L);
        }

        // 5. Default fallback
        return DEFAULT_TEST_DURATION_MS;
    }

    public static String formatDuration(long ms) {
        if (ms < 1000L) {
            return ms + "ms";
        }
        long seconds = ms / 1000L;
        if (seconds < 60L) {
            return seconds + "s";
        }
        long mins = seconds / 60L;
        long remSec = seconds % 60L;
        return mins + "m " + remSec + "s";
    }
}
