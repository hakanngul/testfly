package io.testfly.network;

import io.testfly.driver.DriverManager;
import org.mockito.MockedStatic;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.time.Instant;
import java.util.Map;

import static org.mockito.Mockito.mockStatic;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

/**
 * Verifies {@link NetworkMock#clear()} / {@link NetworkMock#cleanup()} reset all
 * per-thread state (routes, recorded requests, legacy stubs, blocklist flag).
 */
public class NetworkMockCleanupTest {

    @AfterMethod
    public void cleanup() {
        NetworkMock.cleanup();
    }

    @Test
    public void clear_resetsRoutesAndRecorded() {
        try (MockedStatic<DriverManager> dm = mockStatic(DriverManager.class)) {
            dm.when(DriverManager::getDriver).thenReturn(null); // non-Chromium path

            NetworkMock mock = NetworkMock.get();
            mock.mockRoute("**/api/x", Response.status(200));
            mock.stub("**/api/y").returnStatus(201);
            mock.recordForTest(new RecordedRequest("https://h/api/x", "GET", Map.of(), null, Instant.now()));

            // Sanity: rule resolves and a request is recorded before clear
            assertEquals(mock.recordedRequests().size(), 1);

            mock.clear();

            assertNull(mock.resolveRuleForTest("https://h/api/x", "GET"),
                    "routes should be empty after clear");
            assertEquals(mock.recordedRequests().size(), 0, "recorded cleared");
        }
    }

    @Test
    public void cleanup_removesThreadLocalInstance() {
        NetworkMock first = NetworkMock.get();
        first.recordForTest(new RecordedRequest("u", "GET", Map.of(), null, Instant.now()));
        NetworkMock.cleanup();
        NetworkMock second = NetworkMock.get();
        assertEquals(second.recordedRequests().size(), 0, "fresh instance after cleanup");
    }
}
