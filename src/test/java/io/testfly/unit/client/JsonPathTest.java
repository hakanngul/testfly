package io.testfly.unit.client;

import com.fasterxml.jackson.databind.JsonNode;
import io.testfly.client.ApiRequest;
import io.testfly.client.ApiResponse;
import org.testng.annotations.Test;

import java.net.URI;
import java.util.List;

import static org.testng.Assert.*;

public class JsonPathTest {

    private static final String BODY = """
            {
              "user": {"id": 7, "name": "Ada", "email": null},
              "items": [
                {"id": 1, "tags": ["a", "b"]},
                {"id": 2, "tags": ["c"]},
                {"id": 3}
              ],
              "a.b": "dotted",
              "x/y~z": "escaped",
              "map": {"k1": 10, "k2": 20},
              "0": "zero-key",
              "quote'd": "q"
            }
            """;

    private static ApiResponse res() {
        ApiRequest request = ApiRequest.builder().uri(URI.create("http://localhost/x")).build();
        return ApiResponse.builder().request(request).status(200).body(BODY).build();
    }

    @Test
    public void legacyDottedAndIndexedPaths() {
        ApiResponse r = res();
        assertEquals(r.json("$.user.name"), "Ada");
        assertEquals(r.json("$.items[1].id"), "2");
        assertEquals(r.json("user.id"), "7");
        assertEquals(r.json("$.items.0.id"), "1");
        assertEquals(r.json("$[0]"), "zero-key", "index on an object falls back to the key, as JSON Pointer did");
        assertNull(r.json("$.user.missing"));
        assertNull(r.json("$.items[9].id"));
        assertTrue(r.jsonNode("$").isObject());
    }

    @Test
    public void bracketNotationHandlesSpecialKeys() {
        ApiResponse r = res();
        assertEquals(r.json("$['a.b']"), "dotted");
        assertEquals(r.json("$[\"x/y~z\"]"), "escaped");
        assertEquals(r.json("$['quote\\'d']"), "q");
        assertEquals(r.json("$['user']['name']"), "Ada");
    }

    @Test
    public void wildcardsCollectMatchesIntoArray() {
        ApiResponse r = res();
        assertEquals(r.jsonList("$.items[*].id", Integer.class), List.of(1, 2, 3));
        assertEquals(r.jsonList("$.items[*].tags[*]", String.class), List.of("a", "b", "c"));
        assertEquals(r.jsonList("$.map.*", Integer.class), List.of(10, 20));
        r.assertJsonArraySize("$.items[*].tags", 2);

        JsonNode none = r.jsonNode("$.user.name[*]");
        assertTrue(none.isArray());
        assertEquals(none.size(), 0);
    }

    @Test
    public void nullValuesStayDistinctFromMissing() {
        ApiResponse r = res();
        assertTrue(r.jsonNode("$.user.email").isNull());
        assertTrue(r.jsonNode("$.user.phone").isMissingNode());
        r.assertJsonExists("$.user.email").assertJsonNull("$.user.email").assertJsonNull("$.user.phone");
    }

    @Test
    public void unsupportedSyntaxIsRejectedClearly() {
        ApiResponse r = res();
        IllegalArgumentException filter = expectThrows(IllegalArgumentException.class, () -> r.json("$.items[?(@.id>1)]"));
        assertTrue(filter.getMessage().contains("not supported"), filter.getMessage());
        expectThrows(IllegalArgumentException.class, () -> r.json("$..id"));
        expectThrows(IllegalArgumentException.class, () -> r.json("$.items[0:2]"));
        expectThrows(IllegalArgumentException.class, () -> r.json("$['unterminated"));
    }
}
