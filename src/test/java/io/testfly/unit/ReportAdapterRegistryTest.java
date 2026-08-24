package io.testfly.unit;

import io.testfly.reporting.ReportAdapter;
import io.testfly.reporting.ReportAdapterRegistry;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.util.List;

import static org.testng.Assert.*;

/**
 * Unit tests for {@link ReportAdapterRegistry}.
 * Thread-safe for parallel=methods via singleThreaded.
 */
@Test(singleThreaded = true)
public class ReportAdapterRegistryTest {

    @BeforeMethod
    public void setup() throws Exception {
        resetRegistry();
    }

    @AfterMethod
    public void resetRegistry() throws Exception {
        synchronized (ReportAdapterRegistry.class) {
            Field adapters = ReportAdapterRegistry.class.getDeclaredField("adapters");
            adapters.setAccessible(true);
            ((List<?>) adapters.get(null)).clear();

            Field loaded = ReportAdapterRegistry.class.getDeclaredField("loaded");
            loaded.setAccessible(true);
            loaded.set(null, false);
        }
    }

    @Test
    public void loadAll_alwaysIncludesBuiltInHtmlAdapter() {
        synchronized (ReportAdapterRegistry.class) {
            ReportAdapterRegistry.loadAll();
            List<ReportAdapter> list = getAdapters();
            assertTrue(list.stream().anyMatch(a -> "html".equals(a.getName())),
                "Built-in HTML adapter should always be present");
        }
    }

    @Test
    public void loadAll_isIdempotent() {
        synchronized (ReportAdapterRegistry.class) {
            ReportAdapterRegistry.loadAll();
            int countAfterFirst = getAdapters().size();
            ReportAdapterRegistry.loadAll();
            assertEquals(getAdapters().size(), countAfterFirst, "loadAll should be idempotent");
        }
    }

    @Test
    public void register_addsCustomAdapter() {
        synchronized (ReportAdapterRegistry.class) {
            ReportAdapterRegistry.loadAll();
            TrackingAdapter adapter = new TrackingAdapter("custom");
            ReportAdapterRegistry.register(adapter);
            assertTrue(getAdapters().stream().anyMatch(a -> "custom".equals(a.getName())));
        }
    }

    @Test
    public void generateAll_callsGenerateOnEachAdapter() {
        TrackingAdapter a1 = new TrackingAdapter("a1");
        TrackingAdapter a2 = new TrackingAdapter("a2");
        synchronized (ReportAdapterRegistry.class) {
            ReportAdapterRegistry.register(a1);
            ReportAdapterRegistry.register(a2);
            ReportAdapterRegistry.generateAll();
        }
        assertTrue(a1.generated, "a1.generate should have been called");
        assertTrue(a2.generated, "a2.generate should have been called");
    }

    @Test
    public void generateAll_adapterException_doesNotAbortOthers() {
        TrackingAdapter good = new TrackingAdapter("good");
        synchronized (ReportAdapterRegistry.class) {
            ReportAdapterRegistry.register(new BrokenAdapter());
            ReportAdapterRegistry.register(good);
            ReportAdapterRegistry.generateAll();
        }
        assertTrue(good.generated, "Adapter after broken one should still run");
    }

    // ----------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static List<ReportAdapter> getAdapters() {
        try {
            Field f = ReportAdapterRegistry.class.getDeclaredField("adapters");
            f.setAccessible(true);
            return (List<ReportAdapter>) f.get(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static class TrackingAdapter implements ReportAdapter {
        private final String name;
        boolean generated;

        TrackingAdapter(String name) { this.name = name; }

        @Override public String getName() { return name; }
        @Override public void generate(File metricsJson) { generated = true; }
    }

    static class BrokenAdapter implements ReportAdapter {
        @Override public String getName() { return "broken"; }
        @Override public void generate(File metricsJson) { throw new RuntimeException("simulated failure"); }
    }
}
