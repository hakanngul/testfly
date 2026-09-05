package io.testfly.network;

import org.testng.annotations.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests for {@link OriginalResponse}. No browser.
 */
public class OriginalResponseTest {

    @Test
    public void accessors_returnConstructedValues() {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("Content-Type", "application/json");
        OriginalResponse r = new OriginalResponse(200, h, "{\"ok\":true}");
        assertEquals(r.status(), 200);
        assertEquals(r.body(), "{\"ok\":true}");
        assertEquals(r.headers().get("content-type"), "application/json");
    }

    @Test
    public void nullBody_becomesEmptyString() {
        OriginalResponse r = new OriginalResponse(204, Map.of(), null);
        assertEquals(r.body(), "");
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void headers_areImmutable() {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("a", "b");
        new OriginalResponse(200, h, "").headers().put("c", "d");
    }

    @Test
    public void nullHeaders_becomeEmptyMap() {
        OriginalResponse r = new OriginalResponse(200, null, "body");
        assertTrue(r.headers().isEmpty());
    }
}
