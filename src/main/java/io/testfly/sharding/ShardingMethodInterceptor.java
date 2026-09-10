package io.testfly.sharding;

import io.testfly.api.TestFlyApi;
import io.testfly.config.TestFlyConfig;
import io.testfly.internal.TestFlyContext;
import org.testng.IMethodInstance;
import org.testng.IMethodInterceptor;
import org.testng.ITestContext;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ShardingMethodInterceptor intercepts TestNG test methods prior to suite execution
 * and filters them so that only tests allocated to the current CI node/shard run.
 *
 * It uses {@link SmartTestSharder} with Longest Processing Time (LPT) Bin-Packing
 * to guarantee optimal load balancing across parallel CI jobs.
 */
@TestFlyApi(since = "1.2.0")
public final class ShardingMethodInterceptor implements IMethodInterceptor {

    @Override
    public List<IMethodInstance> intercept(List<IMethodInstance> methods, ITestContext context) {
        if (methods == null || methods.isEmpty()) {
            return methods;
        }

        ShardingParameters params = resolveParameters();
        if (!params.isEnabled() || params.getTotalShards() <= 1) {
            return methods;
        }

        int total = params.getTotalShards();
        int index = params.getShardIndex();

        if (index < 0 || index >= total) {
            System.err.printf(
                    "[TestFly] Sharding error: Shard index %d is out of range [0, %d). Running full suite without sharding.\n",
                    index, total
            );
            return methods;
        }

        // Load historical execution durations
        File metricsFile = params.getMetricsFile();
        Map<String, Long> durations = SmartTestSharder.loadHistoricalDurations(metricsFile);

        // Convert TestNG methods to ShardedItems
        List<SmartTestSharder.ShardedItem<IMethodInstance>> items = new ArrayList<>(methods.size());
        for (IMethodInstance mi : methods) {
            Class<?> testClass = mi.getMethod().getRealClass();
            Method m = mi.getMethod().getConstructorOrMethod().getMethod();
            String testId = testClass.getName() + "." + mi.getMethod().getMethodName();

            long durationMs = SmartTestSharder.resolveDuration(testId, testClass, m, durations);
            items.add(new SmartTestSharder.ShardedItem<>(testId, durationMs, mi));
        }

        // Compute LPT / Round-Robin schedule
        SmartTestSharder.ShardingPlan<IMethodInstance> plan =
                SmartTestSharder.createPlan(items, total, params.getStrategy());

        // Print distribution banner
        System.out.println(plan.formatSummary(index));

        // Return only items assigned to this node
        return plan.getPartition(index).getPayloads();
    }

    private static ShardingParameters resolveParameters() {
        // 1. Check System Properties: -Dtestfly.shard.total=4 -Dtestfly.shard.index=0
        String sysTotal = System.getProperty("testfly.shard.total");
        String sysIndex = System.getProperty("testfly.shard.index");
        String sysStrategy = System.getProperty("testfly.shard.strategy");
        String sysMetrics = System.getProperty("testfly.shard.metrics");

        // 2. Check Standard CI Environment Variables (GitLab CI, CircleCI, etc.)
        String envTotal = System.getenv("CI_NODE_TOTAL");
        String envIndex = System.getenv("CI_NODE_INDEX");

        boolean enabled = false;
        int total = 1;
        int index = 0;
        String strategy = "lpt";
        File metricsFile = new File("target/testfly-metrics.json");

        if (sysTotal != null && !sysTotal.isBlank()) {
            enabled = true;
            try { total = Integer.parseInt(sysTotal.trim()); } catch (NumberFormatException ignored) {}
            if (sysIndex != null && !sysIndex.isBlank()) {
                try { index = Integer.parseInt(sysIndex.trim()); } catch (NumberFormatException ignored) {}
            }
        } else if (envTotal != null && !envTotal.isBlank()) {
            enabled = true;
            try { total = Integer.parseInt(envTotal.trim()); } catch (NumberFormatException ignored) {}
            if (envIndex != null && !envIndex.isBlank()) {
                try { index = Integer.parseInt(envIndex.trim()); } catch (NumberFormatException ignored) {}
            }
        } else if (TestFlyContext.isInitialized()) {
            TestFlyConfig cfg = TestFlyContext.getConfig();
            if (cfg != null && cfg.getExecution() != null && cfg.getExecution().getSharding() != null) {
                TestFlyConfig.Execution.Sharding s = cfg.getExecution().getSharding();
                if (s.isEnabled()) {
                    enabled = true;
                    total = s.getTotal();
                    index = s.getIndex();
                    if (s.getStrategy() != null) {
                        strategy = s.getStrategy();
                    }
                    if (s.getMetricsFile() != null) {
                        metricsFile = new File(s.getMetricsFile());
                    }
                }
            }
        }

        if (sysStrategy != null && !sysStrategy.isBlank()) {
            strategy = sysStrategy.trim();
        }
        if (sysMetrics != null && !sysMetrics.isBlank()) {
            metricsFile = new File(sysMetrics.trim());
        }

        return new ShardingParameters(enabled, total, index, strategy, metricsFile);
    }

    private static final class ShardingParameters {
        private final boolean enabled;
        private final int totalShards;
        private final int shardIndex;
        private final String strategy;
        private final File metricsFile;

        public ShardingParameters(boolean enabled, int totalShards, int shardIndex, String strategy, File metricsFile) {
            this.enabled = enabled;
            this.totalShards = totalShards;
            this.shardIndex = shardIndex;
            this.strategy = strategy;
            this.metricsFile = metricsFile;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public int getTotalShards() {
            return totalShards;
        }

        public int getShardIndex() {
            return shardIndex;
        }

        public String getStrategy() {
            return strategy;
        }

        public File getMetricsFile() {
            return metricsFile;
        }
    }
}
