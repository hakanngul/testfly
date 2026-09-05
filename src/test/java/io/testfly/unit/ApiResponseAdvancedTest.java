package io.testfly.unit;

import com.fasterxml.jackson.databind.JsonNode;
import io.testfly.client.ApiResponse;
import org.testng.annotations.Test;

import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

public class ApiResponseAdvancedTest {

    @SuppressWarnings("unchecked")
    private HttpResponse<String> mockHttpResponse(int status, String body, Map<String, List<String>> headers) {
        HttpResponse<String> mock = mock(HttpResponse.class);
        when(mock.statusCode()).thenReturn(status);
        when(mock.body()).thenReturn(body);

        HttpHeaders httpHeaders = HttpHeaders.of(headers != null ? headers : Map.of(), (k, v) -> true);
        when(mock.headers()).thenReturn(httpHeaders);
        return mock;
    }

    private ApiResponse createResponse(int status, String body) {
        HttpResponse<String> raw = mockHttpResponse(status, body, Map.of());
        try {
            java.lang.reflect.Constructor<ApiResponse> ctor =
                    ApiResponse.class.getDeclaredConstructor(HttpResponse.class, long.class, String.class, String.class);
            ctor.setAccessible(true);
            return ctor.newInstance(raw, 120L, "GET", "https://api.example.com/data");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testJsonNodeExtraction() {
        String body = "{\"user\": {\"id\": 42, \"name\": \"Antigravity\", \"active\": true, \"scores\": [10, 20, 30]}}";
        ApiResponse res = createResponse(200, body);

        JsonNode idNode = res.jsonNode("$.user.id");
        assertNotNull(idNode);
        assertEquals(idNode.asInt(), 42);

        JsonNode nameNode = res.jsonNode("$.user.name");
        assertEquals(nameNode.asText(), "Antigravity");
    }

    @Test
    public void testAssertJsonPredicate() {
        String body = "{\"order\": {\"status\": \"PROCESSING_STAGE_1\", \"total\": 150.50}}";
        ApiResponse res = createResponse(200, body);

        res.assertJson("$.order.status", node -> node.asText().startsWith("PROCESSING"), "Status starts with PROCESSING");
        res.assertJson("$.order.total", node -> node.asDouble() > 100.0, "Total exceeds 100");
    }

    @Test(expectedExceptions = RuntimeException.class)
    public void testAssertJsonPredicate_failsWhenFalse() {
        String body = "{\"order\": {\"status\": \"FAILED\"}}";
        ApiResponse res = createResponse(200, body);

        res.assertJson("$.order.status", node -> node.asText().equals("SUCCESS"), "Status should be SUCCESS");
    }

    @Test
    public void testAssertJsonComparison() {
        String body = "{\"metrics\": {\"latency\": 45.2, \"errors\": 0}}";
        ApiResponse res = createResponse(200, body);

        res.assertJsonGreaterThan("$.metrics.latency", 40.0);
        res.assertJsonLessThan("$.metrics.latency", 50.0);
    }

    @Test
    public void testAssertJsonContains() {
        String body = "{\"description\": \"Order placed via Mobile Android App\"}";
        ApiResponse res = createResponse(200, body);

        res.assertJsonContains("$.description", "Mobile");
        res.assertJsonContains("$.description", "Android");
    }

    @Test
    public void testAssertJsonBoolean() {
        String body = "{\"flags\": {\"isVerified\": true, \"isBlocked\": false}}";
        ApiResponse res = createResponse(200, body);

        res.assertJsonTrue("$.flags.isVerified");
        res.assertJsonFalse("$.flags.isBlocked");
    }
}
