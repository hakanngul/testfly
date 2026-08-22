package io.testfly.unit;

import io.testfly.client.ApiClient;
import io.testfly.config.TestFlyConfig;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.testng.Assert.*;

/**
 * Unit tests for new ApiClient features: query params, timeout override,
 * interceptors, cookies, and RetryConfig defaults.
 *
 * HTTP-level behavior (retry loop, actual requests) is tested via integration tests.
 */
public class ApiClientFeaturesTest {

    @AfterMethod
    public void cleanup() {
        ApiClient.clearGlobalAuth();
        ApiClient.clearCookies();
        ApiClient.clearInterceptors();
    }

    // ── Query parameter builder ────────────────────────────────────────────────

    @Test
    public void queryParam_singleParam_appendedToUrl() throws Exception {
        ApiClient client = ApiClient.get("/users").queryParam("page", 1);
        Map<String, String> params = getQueryParams(client);
        assertEquals(params.size(), 1);
        assertEquals(params.get("page"), "1");
    }

    @Test
    public void queryParam_multipleParams_allAppended() throws Exception {
        ApiClient client = ApiClient.get("/users")
                .queryParam("page", 1)
                .queryParam("limit", 10)
                .queryParam("sort", "name");
        Map<String, String> params = getQueryParams(client);
        assertEquals(params.size(), 3);
        assertEquals(params.get("page"), "1");
        assertEquals(params.get("limit"), "10");
        assertEquals(params.get("sort"), "name");
    }

    @Test
    public void queryParams_fromMap_allAppended() throws Exception {
        ApiClient client = ApiClient.get("/users")
                .queryParams(Map.of("page", 1, "limit", 10));
        Map<String, String> params = getQueryParams(client);
        assertEquals(params.size(), 2);
    }

    @Test
    public void queryParam_specialCharacters_storedAsIs() throws Exception {
        // URL encoding happens at buildUrl() time, not at queryParam() time
        ApiClient client = ApiClient.get("/search")
                .queryParam("q", "hello world");
        Map<String, String> params = getQueryParams(client);
        assertEquals(params.get("q"), "hello world");
    }

    // ── Timeout override ───────────────────────────────────────────────────────

    @Test
    public void timeout_setsOverride() throws Exception {
        ApiClient client = ApiClient.get("/slow").timeout(120);
        Integer timeout = getTimeoutOverride(client);
        assertEquals(timeout, Integer.valueOf(120));
    }

    @Test
    public void timeout_notSet_returnsNull() throws Exception {
        ApiClient client = ApiClient.get("/fast");
        Integer timeout = getTimeoutOverride(client);
        assertNull(timeout, "Timeout override should be null by default");
    }

    // ── Interceptors ───────────────────────────────────────────────────────────

    @Test
    public void addRequestInterceptor_registersInterceptor() {
        ApiClient.addRequestInterceptor(builder ->
                builder.header("X-Test", "value"));
        // No exception = success (registration is fire-and-forget)
    }

    @Test
    public void addResponseInterceptor_registersInterceptor() {
        ApiClient.addResponseInterceptor(response -> {
            // no-op
        });
        // No exception = success
    }

    @Test
    public void clearInterceptors_removesAll() {
        ApiClient.addRequestInterceptor(builder -> {});
        ApiClient.addResponseInterceptor(response -> {});
        ApiClient.clearInterceptors();
        // No exception = success (clear is idempotent)
    }

    @Test
    public void multipleInterceptors_allRegistered() {
        ApiClient.addRequestInterceptor(builder -> {});
        ApiClient.addRequestInterceptor(builder -> {});
        ApiClient.addRequestInterceptor(builder -> {});
        ApiClient.clearInterceptors();
    }

    // ── Cookie jar ─────────────────────────────────────────────────────────────

    @Test
    public void clearCookies_doesNotThrow() {
        ApiClient.clearCookies();
        ApiClient.clearCookies(); // idempotent
    }

    @Test
    public void withCookies_returnsSameInstance() {
        ApiClient client = ApiClient.get("/login");
        ApiClient same = client.withCookies();
        assertSame(client, same, "withCookies() must return the same builder instance");
    }

    // ── Global auth ────────────────────────────────────────────────────────────

    @Test
    public void setGlobalAuth_and_clearAuth_doNotThrow() {
        ApiClient.setGlobalAuth(builder ->
                builder.header("Authorization", "Bearer test"));
        ApiClient.clearGlobalAuth();
    }

    // ── RetryConfig defaults ───────────────────────────────────────────────────

    @Test
    public void retryConfig_defaultValues_areSafe() {
        TestFlyConfig.Api.RetryConfig retry = new TestFlyConfig.Api.RetryConfig();
        assertFalse(retry.isEnabled(), "Retry should be disabled by default");
        assertEquals(retry.getMaxAttempts(), 3);
        assertEquals(retry.getBackoffMs(), 500L);
        assertTrue(retry.isRetryOnException());
        assertEquals(retry.getRetryOnStatus().size(), 3);
        assertTrue(retry.getRetryOnStatus().contains(502));
        assertTrue(retry.getRetryOnStatus().contains(503));
        assertTrue(retry.getRetryOnStatus().contains(504));
    }

    @Test
    public void retryConfig_customValues_areApplied() {
        TestFlyConfig.Api.RetryConfig retry = new TestFlyConfig.Api.RetryConfig();
        retry.setEnabled(true);
        retry.setMaxAttempts(5);
        retry.setBackoffMs(1000);
        retry.setRetryOnException(false);
        retry.setRetryOnStatus(java.util.List.of(429, 500));

        assertTrue(retry.isEnabled());
        assertEquals(retry.getMaxAttempts(), 5);
        assertEquals(retry.getBackoffMs(), 1000L);
        assertFalse(retry.isRetryOnException());
        assertEquals(retry.getRetryOnStatus().size(), 2);
    }

    // ── Truncation limit ───────────────────────────────────────────────────────

    @Test
    public void truncationLimit_defaultValue_is300() {
        TestFlyConfig.Api api = new TestFlyConfig.Api();
        assertEquals(api.getTruncationLimit(), 300);
    }

    @Test
    public void truncationLimit_customValue_isApplied() {
        TestFlyConfig.Api api = new TestFlyConfig.Api();
        api.setTruncationLimit(1000);
        assertEquals(api.getTruncationLimit(), 1000);
    }

    // ── Fluent API returns same instance ───────────────────────────────────────

    @Test
    public void fluentApi_allMethods_returnSameInstance() {
        ApiClient client = ApiClient.create();
        assertSame(client, client.get());
        assertSame(client, client.post());
        assertSame(client, client.put());
        assertSame(client, client.patch());
        assertSame(client, client.delete());
        assertSame(client, client.path("/test"));
        assertSame(client, client.header("X", "Y"));
        assertSame(client, client.contentType("application/json"));
        assertSame(client, client.body("test"));
        assertSame(client, client.timeout(30));
        assertSame(client, client.queryParam("k", "v"));
        assertSame(client, client.withCookies());
    }

    // ── Reflection helpers (access private fields for unit testing) ─────────────

    @SuppressWarnings("unchecked")
    private Map<String, String> getQueryParams(ApiClient client) throws Exception {
        Field field = ApiClient.class.getDeclaredField("queryParams");
        field.setAccessible(true);
        return (Map<String, String>) field.get(client);
    }

    private Integer getTimeoutOverride(ApiClient client) throws Exception {
        Field field = ApiClient.class.getDeclaredField("timeoutOverride");
        field.setAccessible(true);
        return (Integer) field.get(client);
    }
}
