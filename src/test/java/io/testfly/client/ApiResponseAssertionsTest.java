package io.testfly.client;

import org.mockito.Mockito;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.when;
import static org.testng.Assert.*;

/**
 * Unit tests for the new ApiResponse assertion methods.
 * Covers: assertDurationLessThan, assertHeader, assertHeaderPresent, assertBodyMatches,
 * assertJsonArraySize, assertJsonExists, assertJsonNull.
 */
public class ApiResponseAssertionsTest {

    private HttpResponse<String> mockResponse;

    @BeforeMethod
    public void setUp() {
        mockResponse = Mockito.mock(HttpResponse.class);
    }

    private ApiResponse response(int status, String body, long durationMs) {
        when(mockResponse.statusCode()).thenReturn(status);
        when(mockResponse.body()).thenReturn(body);
        HttpHeaders headers = HttpHeaders.of(Map.of(), (a, b) -> true);
        when(mockResponse.headers()).thenReturn(headers);
        return new ApiResponse(mockResponse, durationMs);
    }

    private ApiResponse responseWithHeaders(int status, String body, Map<String, List<String>> headerMap) {
        when(mockResponse.statusCode()).thenReturn(status);
        when(mockResponse.body()).thenReturn(body);
        HttpHeaders headers = HttpHeaders.of(headerMap, (a, b) -> true);
        when(mockResponse.headers()).thenReturn(headers);
        return new ApiResponse(mockResponse, 100);
    }

    // ── assertDurationLessThan ─────────────────────────────────────────────────

    @Test
    public void assertDurationLessThan_withinLimit_passes() {
        response(200, "{}", 150).assertDurationLessThan(200);
    }

    @Test
    public void assertDurationLessThan_exactlyAtLimit_passes() {
        response(200, "{}", 200).assertDurationLessThan(200);
    }

    @Test(expectedExceptions = AssertionError.class)
    public void assertDurationLessThan_exceedsLimit_fails() {
        response(200, "{}", 500).assertDurationLessThan(200);
    }

    @Test
    public void assertDurationLessThan_withTimeUnit_passes() {
        response(200, "{}", 800).assertDurationLessThan(1, TimeUnit.SECONDS);
    }

    @Test(expectedExceptions = AssertionError.class)
    public void assertDurationLessThan_withTimeUnit_fails() {
        response(200, "{}", 2000).assertDurationLessThan(1, TimeUnit.SECONDS);
    }

    // ── assertHeader ───────────────────────────────────────────────────────────

    @Test
    public void assertHeader_matchingValue_passes() {
        ApiResponse res = responseWithHeaders(200, "{}",
                Map.of("content-type", List.of("application/json")));
        res.assertHeader("content-type", "application/json");
    }

    @Test(expectedExceptions = AssertionError.class)
    public void assertHeader_mismatchedValue_fails() {
        ApiResponse res = responseWithHeaders(200, "{}",
                Map.of("content-type", List.of("text/html")));
        res.assertHeader("content-type", "application/json");
    }

    @Test(expectedExceptions = AssertionError.class)
    public void assertHeader_missingHeader_fails() {
        ApiResponse res = responseWithHeaders(200, "{}", Map.of());
        res.assertHeader("x-missing", "value");
    }

    // ── assertHeaderPresent ────────────────────────────────────────────────────

    @Test
    public void assertHeaderPresent_existingHeader_passes() {
        ApiResponse res = responseWithHeaders(200, "{}",
                Map.of("x-request-id", List.of("abc-123")));
        res.assertHeaderPresent("x-request-id");
    }

    @Test(expectedExceptions = AssertionError.class)
    public void assertHeaderPresent_missingHeader_fails() {
        ApiResponse res = responseWithHeaders(200, "{}", Map.of());
        res.assertHeaderPresent("x-missing");
    }

    // ── assertBodyMatches ──────────────────────────────────────────────────────

    @Test
    public void assertBodyMatches_matchingRegex_passes() {
        response(200, "Order #12345 confirmed", 50)
                .assertBodyMatches("Order #\\d+ confirmed");
    }

    @Test(expectedExceptions = AssertionError.class)
    public void assertBodyMatches_nonMatchingRegex_fails() {
        response(200, "Order pending", 50)
                .assertBodyMatches("Order #\\d+ confirmed");
    }

    @Test
    public void assertBodyMatches_multilineBody_passes() {
        response(200, "line1\nline2\nline3", 50)
                .assertBodyMatches("line1.*line3");
    }

    // ── assertJsonArraySize ────────────────────────────────────────────────────

    @Test
    public void assertJsonArraySize_correctSize_passes() {
        response(200, "{\"items\": [1, 2, 3]}", 50)
                .assertJsonArraySize("$.items", 3);
    }

    @Test(expectedExceptions = AssertionError.class)
    public void assertJsonArraySize_wrongSize_fails() {
        response(200, "{\"items\": [1, 2, 3]}", 50)
                .assertJsonArraySize("$.items", 5);
    }

    @Test(expectedExceptions = AssertionError.class)
    public void assertJsonArraySize_notAnArray_fails() {
        response(200, "{\"name\": \"John\"}", 50)
                .assertJsonArraySize("$.name", 1);
    }

    @Test(expectedExceptions = AssertionError.class)
    public void assertJsonArraySize_missingPath_fails() {
        response(200, "{\"items\": [1]}", 50)
                .assertJsonArraySize("$.nonexistent", 1);
    }

    // ── assertJsonExists ───────────────────────────────────────────────────────

    @Test
    public void assertJsonExists_existingPath_passes() {
        response(200, "{\"user\": {\"name\": \"John\"}}", 50)
                .assertJsonExists("$.user.name");
    }

    @Test
    public void assertJsonExists_nullValue_stillPasses() {
        response(200, "{\"user\": null}", 50)
                .assertJsonExists("$.user");
    }

    @Test(expectedExceptions = AssertionError.class)
    public void assertJsonExists_missingPath_fails() {
        response(200, "{\"user\": {\"name\": \"John\"}}", 50)
                .assertJsonExists("$.user.email");
    }

    // ── assertJsonNull ─────────────────────────────────────────────────────────

    @Test
    public void assertJsonNull_nullValue_passes() {
        response(200, "{\"user\": null}", 50)
                .assertJsonNull("$.user");
    }

    @Test
    public void assertJsonNull_missingPath_passes() {
        response(200, "{\"user\": {\"name\": \"John\"}}", 50)
                .assertJsonNull("$.user.email");
    }

    @Test(expectedExceptions = AssertionError.class)
    public void assertJsonNull_nonNullValue_fails() {
        response(200, "{\"user\": \"John\"}", 50)
                .assertJsonNull("$.user");
    }

    // ── Fluent chaining ────────────────────────────────────────────────────────

    @Test
    public void allAssertions_supportFluentChaining() {
        ApiResponse result = responseWithHeaders(200, "{\"items\": [1, 2]}",
                Map.of("content-type", List.of("application/json")))
                .assertStatus(200)
                .assertDurationLessThan(200)
                .assertHeaderPresent("content-type")
                .assertJsonExists("$.items")
                .assertJsonArraySize("$.items", 2);

        assertSame(result.getClass(), ApiResponse.class, "Fluent chaining must return ApiResponse");
    }
}
