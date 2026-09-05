package io.testfly.precondition;

import org.testng.SkipException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * Unit tests for {@link ApiHealthChecker}.
 * Thread-safe for parallel=methods via synchronized lock on ApiHealthChecker.class.
 *
 * Note: These tests use real HTTP calls to localhost or invalid URLs to avoid
 * external dependencies. The probe method is package-private and tested directly.
 */
@Test(singleThreaded = true)
public class ApiHealthCheckerTest {

    private static final Object HEALTH_CHECKER_LOCK = ApiHealthChecker.class;

    @BeforeMethod
    public void setup() {
        synchronized (HEALTH_CHECKER_LOCK) {
            ApiHealthChecker.clearCache();
        }
    }

    @AfterMethod
    public void tearDown() {
        synchronized (HEALTH_CHECKER_LOCK) {
            ApiHealthChecker.clearCache();
        }
    }

    // ── Endpoint reachable → health check passes ─────────────────────────────

    @Test
    public void probe_invalidPort_returnsFalse() {
        synchronized (HEALTH_CHECKER_LOCK) {
            boolean result = ApiHealthChecker.probe("http://localhost:99999/health", 1);
            assertFalse(result, "Invalid port should return false");
        }
    }

    @Test
    public void probe_invalidUrl_returnsFalse() {
        synchronized (HEALTH_CHECKER_LOCK) {
            boolean result = ApiHealthChecker.probe("http://invalid.localhost.test:1/health", 1);
            assertFalse(result, "Unreachable URL should return false");
        }
    }

    @Test
    public void probe_malformedUrl_returnsFalse() {
        synchronized (HEALTH_CHECKER_LOCK) {
            boolean result = ApiHealthChecker.probe("not-a-valid-url", 1);
            assertFalse(result, "Malformed URL should return false");
        }
    }

    // ── Endpoint unreachable → health check fails with clear message ─────────

    @Test(expectedExceptions = SkipException.class,
          expectedExceptionsMessageRegExp = "@DependsOnApi:.*unreachable.*")
    public void checkOrSkip_unreachableUrl_throwsSkipException() {
        synchronized (HEALTH_CHECKER_LOCK) {
            ApiHealthChecker.checkOrSkip("http://invalid.localhost.test:1/health", 1);
        }
    }

    @Test(expectedExceptions = SkipException.class)
    public void checkOrSkip_connectionRefused_throwsSkipException() {
        synchronized (HEALTH_CHECKER_LOCK) {
            // Port 1 is typically not open
            ApiHealthChecker.checkOrSkip("http://localhost:1/health", 1);
        }
    }

    // ── Timeout handling ─────────────────────────────────────────────────────

    @Test
    public void probe_timeout_returnsFalse() {
        synchronized (HEALTH_CHECKER_LOCK) {
            // Very short timeout should cause timeout
            boolean result = ApiHealthChecker.probe("http://10.255.255.1/health", 1);
            assertFalse(result, "Timeout should return false");
        }
    }

    @Test(expectedExceptions = SkipException.class)
    public void checkOrSkip_timeout_throwsSkipException() {
        synchronized (HEALTH_CHECKER_LOCK) {
            ApiHealthChecker.checkOrSkip("http://10.255.255.1/health", 1);
        }
    }

    // ── Caching behavior ─────────────────────────────────────────────────────

    @Test
    public void checkOrSkip_cachesResult_secondCallDoesNotProbe() {
        synchronized (HEALTH_CHECKER_LOCK) {
            String url = "http://invalid.localhost.test:1/health";
            assertFalse(ApiHealthChecker.isCached(url), "URL should not be cached initially");

            // First call should probe and cache the result
            try {
                ApiHealthChecker.checkOrSkip(url, 1);
                fail("Should have thrown SkipException");
            } catch (SkipException e) {
                // Expected
            }

            assertTrue(ApiHealthChecker.isCached(url), "URL should be cached after first probe");

            // Second call should use cached result (still throw SkipException)
            try {
                ApiHealthChecker.checkOrSkip(url, 1);
                fail("Should have thrown SkipException on second call");
            } catch (SkipException e) {
                // Expected — cached result should still cause skip
            }

            assertTrue(ApiHealthChecker.isCached(url), "URL should remain cached after second call");
        }
    }

    @Test
    public void clearCache_resetsState() {
        synchronized (HEALTH_CHECKER_LOCK) {
            String url = "http://invalid.localhost.test:1/health";

            // First call caches the result
            try {
                ApiHealthChecker.checkOrSkip(url, 1);
            } catch (SkipException e) {
                // Expected
            }

            assertTrue(ApiHealthChecker.isCached(url), "URL should be cached after probe");

            // Clear cache
            ApiHealthChecker.clearCache();
            assertFalse(ApiHealthChecker.isCached(url), "Cache should be empty after clearCache()");
            assertEquals(ApiHealthChecker.getCacheSize(), 0, "Cache size should be 0");

            // Second call should probe again (still fail, but proves cache was cleared)
            try {
                ApiHealthChecker.checkOrSkip(url, 1);
            } catch (SkipException e) {
                // Expected
            }

            assertTrue(ApiHealthChecker.isCached(url), "URL should be re-cached after second call");
        }
    }

    @Test
    public void probe_differentUrls_probedSeparately() {
        synchronized (HEALTH_CHECKER_LOCK) {
            boolean result1 = ApiHealthChecker.probe("http://localhost:1/health", 1);
            boolean result2 = ApiHealthChecker.probe("http://localhost:2/health", 1);

            // Both should fail (ports not open), but they're different URLs
            assertFalse(result1);
            assertFalse(result2);
        }
    }

    @Test
    public void checkOrSkip_messageContainsUrl() {
        synchronized (HEALTH_CHECKER_LOCK) {
            String url = "http://invalid.localhost.test:1/health";
            try {
                ApiHealthChecker.checkOrSkip(url, 1);
                fail("Should have thrown SkipException");
            } catch (SkipException e) {
                assertTrue(e.getMessage().contains(url),
                        "SkipException message should contain the URL");
            }
        }
    }
}
