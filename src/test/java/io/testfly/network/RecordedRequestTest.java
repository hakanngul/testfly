package io.testfly.network;

import org.testng.annotations.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests for {@link RecordedRequest}. No browser.
 */
public class RecordedRequestTest {

    @Test
    public void headerKeys_lowerCased() {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("Content-Type", "application/json");
        h.put("AUTHORIZATION", "Bearer x");
        RecordedRequest r = new RecordedRequest("https://h/api", "GET", h, null, Instant.now());
        assertTrue(r.headers().containsKey("content-type"));
        assertTrue(r.headers().containsKey("authorization"));
        assertFalse(r.headers().containsKey("Content-Type"));
    }

    @Test
    public void header_lookupCaseInsensitive() {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("X-Custom", "42");
        RecordedRequest r = new RecordedRequest("https://h", "POST", h, "{}", Instant.now());
        assertEquals(r.header("x-custom").orElse(null), "42");
        assertEquals(r.header("X-CUSTOM").orElse(null), "42");
        assertTrue(r.header("missing").isEmpty());
        assertTrue(r.header(null).isEmpty());
    }

    @Test
    public void nullHeaders_becomeEmptyMap() {
        RecordedRequest r = new RecordedRequest("https://h", "GET", null, null, Instant.now());
        assertTrue(r.headers().isEmpty());
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void headers_areImmutable() {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("a", "b");
        new RecordedRequest("u", "GET", h, null, Instant.now()).headers().put("c", "d");
    }

    @Test
    public void accessors_returnConstructedValues() {
        Instant now = Instant.now();
        RecordedRequest r = new RecordedRequest("https://h/x", "PUT", Map.of(), "body", now);
        assertEquals(r.url(), "https://h/x");
        assertEquals(r.method(), "PUT");
        assertEquals(r.body(), "body");
        assertEquals(r.timestamp(), now);
    }
}
