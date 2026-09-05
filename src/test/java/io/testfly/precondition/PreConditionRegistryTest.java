package io.testfly.precondition;

import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.*;

/**
 * Unit tests for {@link PreConditionRegistry}.
 * Thread-safe for parallel=methods via singleThreaded.
 *
 * Note: The registry is a static list. We use unique condition names per test
 * to avoid interference from providers registered in previous tests.
 */
@Test(singleThreaded = true)
public class PreConditionRegistryTest {

    // ── Register a condition → retrievable ────────────────────────────────────

    @Test
    public void register_addsProvider_findableByName() {
        ProviderForRegisterTest provider = new ProviderForRegisterTest();
        PreConditionRegistry.register(provider);

        PreConditionRegistry.ProviderMethod pm = PreConditionRegistry.find("regTest_register");
        assertNotNull(pm, "Registered provider should be findable");
        assertSame(pm.instance(), provider, "Provider instance should match");
    }

    @Test
    public void register_multipleProviders_allFindable() {
        ProviderForMultiA providerA = new ProviderForMultiA();
        ProviderForMultiB providerB = new ProviderForMultiB();

        PreConditionRegistry.register(providerA);
        PreConditionRegistry.register(providerB);

        assertNotNull(PreConditionRegistry.find("regTest_multiA"), "Provider A should be findable");
        assertNotNull(PreConditionRegistry.find("regTest_multiB"), "Provider B should be findable");
    }

    @Test
    public void getProviders_returnsAllRegistered() {
        int initialCount = PreConditionRegistry.getProviders().size();

        PreConditionRegistry.register(new ProviderForCountTest1());
        PreConditionRegistry.register(new ProviderForCountTest2());

        List<BaseConditions> providers = PreConditionRegistry.getProviders();
        assertEquals(providers.size(), initialCount + 2,
                "Should have 2 more providers than initially");
    }

    // ── Unknown condition name → error (not silent skip) ─────────────────────

    @Test
    public void find_unknownCondition_returnsNull() {
        PreConditionRegistry.ProviderMethod pm = PreConditionRegistry.find("nonExistentCondition_XYZ");
        assertNull(pm, "Unknown condition should return null, not throw");
    }

    @Test
    public void find_afterRegistration_returnsCorrectMethod() {
        ProviderForMethodTest provider = new ProviderForMethodTest();
        PreConditionRegistry.register(provider);

        PreConditionRegistry.ProviderMethod pm = PreConditionRegistry.find("regTest_methodName");
        assertNotNull(pm);
        assertEquals(pm.method().getName(), "doSomething",
                "Should resolve to the correct method name");
    }

    // ── Duplicate registration → handled (override or error) ─────────────────

    @Test
    public void register_sameConditionTwice_firstWins() {
        ProviderForDuplicateTest first = new ProviderForDuplicateTest("first");
        ProviderForDuplicateTest second = new ProviderForDuplicateTest("second");

        PreConditionRegistry.register(first);
        PreConditionRegistry.register(second);

        PreConditionRegistry.ProviderMethod pm = PreConditionRegistry.find("regTest_duplicate");
        assertNotNull(pm, "Should find a provider for the condition");
        // Registry finds the first match during iteration
        assertSame(pm.instance(), first, "First registered provider should be found");
    }

    @Test
    public void providerMethod_invoke_callsCorrectMethod() throws Exception {
        ProviderForInvokeTest provider = new ProviderForInvokeTest();
        PreConditionRegistry.register(provider);

        PreConditionRegistry.ProviderMethod pm = PreConditionRegistry.find("regTest_invoke");
        assertNotNull(pm);

        pm.invoke();
        assertTrue(provider.invoked, "invoke() should call the provider method");
    }

    @Test
    public void loadAll_isIdempotent() {
        PreConditionRegistry.loadAll();
        PreConditionRegistry.loadAll();
        // No exception thrown — success
    }

    @Test
    public void getProviders_returnsUnmodifiableList() {
        List<BaseConditions> providers = PreConditionRegistry.getProviders();
        try {
            providers.add(new ProviderForRegisterTest());
            fail("Should throw UnsupportedOperationException for unmodifiable list");
        } catch (UnsupportedOperationException e) {
            // Expected
        }
    }

    // ── Test provider implementations with unique condition names ────────────

    public static class ProviderForRegisterTest extends BaseConditions {
        @ConditionProvider("regTest_register")
        public void provide() {
        }
    }

    public static class ProviderForMultiA extends BaseConditions {
        @ConditionProvider("regTest_multiA")
        public void provide() {
        }
    }

    public static class ProviderForMultiB extends BaseConditions {
        @ConditionProvider("regTest_multiB")
        public void provide() {
        }
    }

    public static class ProviderForCountTest1 extends BaseConditions {
        @ConditionProvider("regTest_count1")
        public void provide() {
        }
    }

    public static class ProviderForCountTest2 extends BaseConditions {
        @ConditionProvider("regTest_count2")
        public void provide() {
        }
    }

    public static class ProviderForMethodTest extends BaseConditions {
        @ConditionProvider("regTest_methodName")
        public void doSomething() {
        }
    }

    public static class ProviderForDuplicateTest extends BaseConditions {
        final String label;

        ProviderForDuplicateTest(String label) {
            this.label = label;
        }

        @ConditionProvider("regTest_duplicate")
        public void provide() {
        }
    }

    public static class ProviderForInvokeTest extends BaseConditions {
        boolean invoked = false;

        @ConditionProvider("regTest_invoke")
        public void provide() {
            invoked = true;
        }
    }
}
