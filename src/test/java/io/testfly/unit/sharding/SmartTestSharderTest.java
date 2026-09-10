package io.testfly.unit.sharding;

import io.testfly.sharding.ShardingMethodInterceptor;
import io.testfly.sharding.SmartTestSharder;
import io.testfly.sharding.SmartTestSharder.ShardedItem;
import io.testfly.sharding.SmartTestSharder.ShardingPlan;
import io.testfly.sharding.TestWeight;
import org.testng.IMethodInstance;
import org.testng.ITestNGMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

public class SmartTestSharderTest {

    @Test
    public void testLptBinPackingBalancesOptimalLoad() {
        // Test items: 60s, 50s, 40s, 30s, 20s, 10s -> Total: 210s
        List<ShardedItem<String>> items = new ArrayList<>();
        items.add(new ShardedItem<>("Test1", 60_000L, "Payload1"));
        items.add(new ShardedItem<>("Test2", 50_000L, "Payload2"));
        items.add(new ShardedItem<>("Test3", 40_000L, "Payload3"));
        items.add(new ShardedItem<>("Test4", 30_000L, "Payload4"));
        items.add(new ShardedItem<>("Test5", 20_000L, "Payload5"));
        items.add(new ShardedItem<>("Test6", 10_000L, "Payload6"));

        ShardingPlan<String> plan = SmartTestSharder.createPlan(items, 3, "lpt");

        assertEquals(plan.getTotalShards(), 3);
        assertEquals(plan.getTotalDurationMs(), 210_000L);

        // LPT should achieve 100% balance efficiency (70s per shard)
        assertEquals(plan.getPartition(0).getTotalEstimatedDurationMs(), 70_000L);
        assertEquals(plan.getPartition(1).getTotalEstimatedDurationMs(), 70_000L);
        assertEquals(plan.getPartition(2).getTotalEstimatedDurationMs(), 70_000L);
        assertEquals(plan.getMakespanMs(), 70_000L);
        assertEquals(plan.getBalanceEfficiency(), 100.0, 0.01);

        String summary = plan.formatSummary(0);
        assertTrue(summary.contains("Active Shard: 1 of 3 (Index: 0)"));
        assertTrue(summary.contains("[CURRENT NODE]"));
    }

    @Test
    public void testRoundRobinStrategy() {
        List<ShardedItem<String>> items = new ArrayList<>();
        items.add(new ShardedItem<>("A", 10_000L, "A"));
        items.add(new ShardedItem<>("B", 10_000L, "B"));
        items.add(new ShardedItem<>("C", 10_000L, "C"));
        items.add(new ShardedItem<>("D", 10_000L, "D"));

        ShardingPlan<String> plan = SmartTestSharder.createPlan(items, 2, "round-robin");
        assertEquals(plan.getPartition(0).getItems().size(), 2);
        assertEquals(plan.getPartition(1).getItems().size(), 2);
        assertEquals(plan.getPartition(0).getItems().get(0).getId(), "A");
        assertEquals(plan.getPartition(1).getItems().get(0).getId(), "B");
    }

    @Test
    public void testSingleShardReturnsAllItems() {
        List<ShardedItem<String>> items = List.of(
                new ShardedItem<>("X", 5_000L, "X"),
                new ShardedItem<>("Y", 3_000L, "Y")
        );

        ShardingPlan<String> plan = SmartTestSharder.createPlan(items, 1, "lpt");
        assertEquals(plan.getTotalShards(), 1);
        assertEquals(plan.getPartition(0).getItems().size(), 2);
    }

    @Test
    public void testEmptyItemsHandling() {
        ShardingPlan<String> plan = SmartTestSharder.createPlan(new ArrayList<>(), 4, "lpt");
        assertEquals(plan.getTotalShards(), 4);
        assertEquals(plan.getTotalDurationMs(), 0L);
        assertEquals(plan.getPartition(0).getItems().size(), 0);
    }

    @Test
    public void testHistoricalDurationsResolution() throws Exception {
        File tempFile = File.createTempFile("testfly-metrics-test", ".json");
        tempFile.deleteOnExit();

        String json = "{\n" +
                "  \"tests\": [\n" +
                "    { \"testId\": \"com.example.DemoTest.testA\", \"totalMs\": 4500 },\n" +
                "    { \"testId\": \"com.example.DemoTest.testB\", \"totalMs\": 12000 }\n" +
                "  ]\n" +
                "}";

        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write(json);
        }

        Map<String, Long> durations = SmartTestSharder.loadHistoricalDurations(tempFile);
        assertEquals(durations.get("com.example.DemoTest.testA"), Long.valueOf(4500L));
        assertEquals(durations.get("com.example.DemoTest.testB"), Long.valueOf(12000L));

        long resolvedA = SmartTestSharder.resolveDuration("com.example.DemoTest.testA", null, null, durations);
        assertEquals(resolvedA, 4500L);

        long fallback = SmartTestSharder.resolveDuration("com.example.Unknown.testZ", null, null, durations);
        assertEquals(fallback, SmartTestSharder.DEFAULT_TEST_DURATION_MS);
    }

    @Test
    public void testTestWeightAnnotationResolution() throws Exception {
        Method annotatedMethod = SampleAnnotatedTest.class.getMethod("heavyTest");
        long duration = SmartTestSharder.resolveDuration("some.test", SampleAnnotatedTest.class, annotatedMethod, Map.of());
        assertEquals(duration, 45_000L); // 30s * 1.5 multiplier = 45s = 45000ms
    }

    @Test
    public void testShardingMethodInterceptorWithSystemProperties() {
        System.setProperty("testfly.shard.total", "2");
        System.setProperty("testfly.shard.index", "0");
        try {
            ShardingMethodInterceptor interceptor = new ShardingMethodInterceptor();

            IMethodInstance m1 = mockMethodInstance("com.example.SuiteTest", "testFast");
            IMethodInstance m2 = mockMethodInstance("com.example.SuiteTest", "testSlow");

            List<IMethodInstance> input = List.of(m1, m2);
            List<IMethodInstance> result = interceptor.intercept(input, null);

            assertNotNull(result);
            assertEquals(result.size(), 1); // 2 tests across 2 shards = 1 per shard
        } finally {
            System.clearProperty("testfly.shard.total");
            System.clearProperty("testfly.shard.index");
        }
    }

    private static IMethodInstance mockMethodInstance(String className, String methodName) {
        IMethodInstance mi = mock(IMethodInstance.class);
        ITestNGMethod tm = mock(ITestNGMethod.class);
        when(mi.getMethod()).thenReturn(tm);
        when(tm.getMethodName()).thenReturn(methodName);
        doReturn(SampleAnnotatedTest.class).when(tm).getRealClass();

        org.testng.internal.ConstructorOrMethod com = mock(org.testng.internal.ConstructorOrMethod.class);
        when(tm.getConstructorOrMethod()).thenReturn(com);
        try {
            when(com.getMethod()).thenReturn(SampleAnnotatedTest.class.getMethod("heavyTest"));
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        return mi;
    }

    public static class SampleAnnotatedTest {
        @TestWeight(seconds = 30, multiplier = 1.5)
        public void heavyTest() {
        }
    }
}
