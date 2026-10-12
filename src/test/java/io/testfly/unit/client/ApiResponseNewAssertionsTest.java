package io.testfly.unit.client;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.testfly.client.ApiException;
import io.testfly.client.ApiRequest;
import io.testfly.client.ApiResponse;
import org.testng.annotations.Test;

import java.net.URI;
import java.util.Map;

import static org.testng.Assert.*;

public class ApiResponseNewAssertionsTest {

    private static ApiResponse res(int status, String body) {
        ApiRequest request = ApiRequest.builder().uri(URI.create("http://localhost/x")).build();
        return ApiResponse.builder().request(request).status(status).body(body).build();
    }

    @Test
    public void statusFamilies() {
        ApiResponse ok = res(201, "{}");
        ok.assertStatusIn(200, 201).assertStatusRange(200, 299).assertSuccessful();
        expectThrows(ApiException.class, () -> ok.assertStatusIn(200, 204));
        expectThrows(ApiException.class, () -> res(404, "{}").assertSuccessful());
        expectThrows(ApiException.class, () -> res(500, "{}").assertStatusRange(400, 499));
    }

    @Test
    public void jsonEqualsIgnoresKeyOrderAndNumberFormat() {
        ApiResponse r = res(200, "{\"b\":1,\"a\":{\"x\":[1,2]},\"c\":2.50}");
        r.assertJsonEquals("{\"a\":{\"x\":[1,2]},\"c\":2.5,\"b\":1.0}");
        r.assertJsonEquals("$.a", "{\"x\":[1,2]}");
    }

    @Test
    public void jsonEqualsReportsFirstDifference() {
        ApiResponse r = res(200, "{\"items\":[{\"name\":\"a\"},{\"name\":\"b\"}],\"extra\":true}");

        ApiException diff = expectThrows(ApiException.class,
                () -> r.assertJsonEquals("$.items", "[{\"name\":\"a\"},{\"name\":\"x\"}]"));
        assertTrue(diff.getMessage().contains("at /1/name"), diff.getMessage());

        ApiException extra = expectThrows(ApiException.class,
                () -> r.assertJsonEquals("{\"items\":[{\"name\":\"a\"},{\"name\":\"b\"}]}"));
        assertTrue(extra.getMessage().contains("unexpected field 'extra'"), extra.getMessage());

        ApiException type = expectThrows(ApiException.class, () -> res(200, "{\"n\":\"1\"}").assertJsonEquals("{\"n\":1}"));
        assertTrue(type.getMessage().contains("at /n"), type.getMessage());

        expectThrows(ApiException.class, () -> r.assertJsonEquals("$.missing", "1"));
        expectThrows(IllegalArgumentException.class, () -> r.assertJsonEquals("{not json"));
    }

    @Test
    public void arrayOrderCanBeIgnored() {
        ApiResponse r = res(200, "[3,1,2,{\"k\":[\"b\",\"a\"]}]");
        expectThrows(ApiException.class, () -> r.assertJsonEquals("[1,2,3,{\"k\":[\"a\",\"b\"]}]"));
        r.assertJsonEquals("[1,2,3,{\"k\":[\"a\",\"b\"]}]", true);
        expectThrows(ApiException.class, () -> r.assertJsonEquals("[1,1,2,{\"k\":[\"a\",\"b\"]}]", true));
    }

    @Test
    public void arrayContains() {
        ApiResponse r = res(200, "{\"ids\":[1,2,3],\"names\":[\"ada\"],\"objs\":[{\"id\":1,\"n\":\"x\"}]}");
        r.assertJsonArrayContains("$.ids", 2)
                .assertJsonArrayContains("$.ids", "3")
                .assertJsonArrayContains("$.names", "ada")
                .assertJsonArrayContains("$.objs", "{\"n\":\"x\",\"id\":1}")
                .assertJsonArrayContains("$.objs", Map.of("id", 1, "n", "x"))
                .assertJsonArrayContains("$.objs", JsonNodeFactory.instance.objectNode().put("id", 1).put("n", "x"));
        expectThrows(ApiException.class, () -> r.assertJsonArrayContains("$.ids", 9));
        expectThrows(ApiException.class, () -> r.assertJsonArrayContains("$.names", "{\"id\":1}"));
        expectThrows(ApiException.class, () -> r.assertJsonArrayContains("$.missing", 1));
    }
}
