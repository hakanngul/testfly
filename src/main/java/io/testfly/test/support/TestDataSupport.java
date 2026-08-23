package io.testfly.test.support;

import io.testfly.api.TestFlyApi;
import io.testfly.testdata.TestDataStore;

import java.util.Map;

/**
 * Shared test-data helpers — single source of truth for {@code getTestData()}.
 *
 * <p>Implemented by {@code BaseTest} and {@code BaseApiTest} so the delegation
 * to {@link TestDataStore} lives in one place.
 */
@TestFlyApi(since = "1.10.0")
public interface TestDataSupport {

    /** Returns the test data loaded by {@code @TestData} for the current test. */
    default Map<String, Object> getTestData() {
        return TestDataStore.get();
    }

    /** Typed test data retrieval. */
    @SuppressWarnings("unchecked")
    default <T> T getTestData(String key, Class<T> type) {
        Object value = TestDataStore.get().get(key);
        if (value == null) throw new IllegalStateException(
            "[TestData] Key not found: '" + key + "'");
        return (T) value;
    }
}
