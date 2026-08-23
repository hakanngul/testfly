package io.testfly.test.support;

import io.testfly.api.TestFlyApi;
import io.testfly.context.ScenarioContext;
import io.testfly.context.SuiteContext;

/**
 * Shared context helpers — single source of truth for {@code ctx()} / {@code suiteCtx()}.
 *
 * <p>Implemented by {@code BaseTest} and {@code BaseApiTest}. Uses holder singletons
 * so the ThreadLocal semantics of {@link ScenarioContext} are preserved — the same
 * pattern already used in {@code BaseTest}.
 */
@TestFlyApi(since = "1.10.0")
public interface ContextSupport {

    /** In-test thread-local context store. Cleared after each test. */
    default ScenarioContext ctx() {
        return ScenarioContextHolder.INSTANCE;
    }

    /** Suite-scoped global context store. Survives between tests. */
    default SuiteContext suiteCtx() {
        return SuiteContextHolder.INSTANCE;
    }

    final class ScenarioContextHolder {
        static final ScenarioContext INSTANCE = new ScenarioContext();
        private ScenarioContextHolder() {}
    }

    final class SuiteContextHolder {
        static final SuiteContext INSTANCE = new SuiteContext();
        private SuiteContextHolder() {}
    }
}
