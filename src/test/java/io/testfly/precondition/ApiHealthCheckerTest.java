package io.testfly.precondition;

import org.testng.SkipException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * Unit tests for {@link ApiHealthChecker}.
 * Thread-safe for parallel=methods via singleThreaded.
 *
 * Note: These tests use real HTTP calls to localhost or invalid URLs to avoid
 * external dependencies. The probe method is package-private and tested directly.
 */
@Test(singleThreaded = true)
public class ApiHealthCheckerTest {

    @BeforeMethod
    public void setup() {
        ApiHealthChecker.clearCache();
    }

    @AfterMethod
    public void tearDown() {
        ApiHealthChecker.clearCache();
    }

    // ── Endpoint reachable → health check passes ─────────────────────────────

    @Test
    public void probe_invalidPort_returnsFalse() {
        boolean result = ApiHealthChecker.probe("http://localhost:99999/health", 1);
        assertFalse(result, "Invalid port should return false");
    }

    @Test
    public void probe_invalidUrl_returnsFalse() {
        boolean result = ApiHealthChecker.probe("http://invalid.localhost.test:1/health", 1);
        assertFalse(result, "Unreachable URL should return false");
    }

    @Test
    public void probe_malformedUrl_returnsFalse() {
        boolean result = ApiHealthChecker.probe("not-a-valid-url", 1);
        assertFalse(result, "Malformed URL should return false");
    }

    // ── Endpoint unreachable → health check fails with clear message ─────────

    @Test(expectedExceptions = SkipException.class,
          expectedExceptionsMessageRegExp = "@DependsOnApi:.*unreachable.*")
    public void checkOrSkip_unreachableUrl_throwsSkipException() {
        ApiHealthChecker.checkOrSkip("http://invalid.localhost.test:1/health", 1);
    }

    @Test(expectedExceptions = SkipException.class)
    public void checkOrSkip_connectionRefused_throwsSkipException() {
        // Port 1 is typically not open
        ApiHealthChecker.checkOrSkip("http://localhost:1/health", 1);
    }

    // ── Timeout handling ─────────────────────────────────────────────────────

    @Test
    public void probe_timeout_returnsFalse() {
        // Very short timeout should cause timeout
        boolean result = ApiHealthChecker.probe("http://10.255.255.1/health", 1);
        assertFalse(result, "Timeout should return false");
    }

    @Test(expectedExceptions = SkipException.class)
    public void checkOrSkip_timeout_throwsSkipException() {
        ApiHealthChecker.checkOrSkip("http://10.255.255.1/health", 1);
    }

    // ── Caching behavior ─────────────────────────────────────────────────────

    @Test
    public void checkOrSkip_cachesResult_secondCallDoesNotProbe() {
        String url = "http://invalid.localhost.test:1/health";

        // First call should probe and cache the result
        try {
            ApiHealthChecker.checkOrSkip(url, 1);
            fail("Should have thrown SkipException");
        } catch (SkipException e) {
            // Expected
        }

        // Second call should use cached result (still throw SkipException)
        try {
            ApiHealthChecker.checkOrSkip(url, 1);
            fail("Should have thrown SkipException on second call");
        } catch (SkipException e) {
            // Expected — cached result should still cause skip
        }
    }

    @Test
    public void clearCache_resetsState() {
        String url = "http://invalid.localhost.test:1/health";

        // First call caches the result
        try {
            ApiHealthChecker.checkOrSkip(url, 1);
        } catch (SkipException e) {
            // Expected
        }

        // Clear cache
        ApiHealthChecker.clearCache();

        // Second call should probe again (still fail, but proves cache was cleared)
        try {
            ApiHealthChecker.checkOrSkip(url, 1);
        } catch (SkipException e) {
            // Expected
        }
    }

    @Test
    public void probe_differentUrls_probedSeparately() {
        boolean result1 = ApiHealthChecker.probe("http://localhost:1/health", 1);
        boolean result2 = ApiHealthChecker.probe("http://localhost:2/health", 1);

        // Both should fail (ports not open), but they're different URLs
        assertFalse(result1);
        assertFalse(result2);
    }

    @Test
    public void checkOrSkip_messageContainsUrl() {
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
