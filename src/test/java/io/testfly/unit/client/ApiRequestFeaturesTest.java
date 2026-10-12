package io.testfly.unit.client;

import io.testfly.client.ApiClient;
import io.testfly.client.ApiResponse;
import io.testfly.test.support.ApiSupport;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.testng.Assert.*;

@Test(singleThreaded = true)
public class ApiRequestFeaturesTest extends ApiClientTestBase {

    private final Map<String, Object> seen = new ConcurrentHashMap<>();

    private String recordingServer() throws Exception {
        return server(ex -> {
            seen.clear();
            seen.put("method", ex.getRequestMethod());
            seen.put("query", String.valueOf(ex.getRequestURI().getRawQuery()));
            seen.put("contentType", String.valueOf(ex.getRequestHeaders().getFirst("Content-Type")));
            seen.put("body", readBody(ex));
            reply(ex, 200, "{\"ok\":true}", Map.of("Content-Type", "application/json", "Allow", "GET, HEAD, OPTIONS"));
        });
    }

    @Test
    public void headAndOptionsAreSent() throws Exception {
        String base = recordingServer();

        ApiResponse head = ApiClient.head(base + "/r").send();
        assertEquals(seen.get("method"), "HEAD");
        assertEquals(head.status(), 200);
        assertEquals(head.asBytes().length, 0);

        ApiResponse options = ApiClient.create().options().path(base + "/r").send();
        assertEquals(seen.get("method"), "OPTIONS");
        assertEquals(options.header("Allow"), "GET, HEAD, OPTIONS");

        ApiSupport support = new ApiSupport() { };
        support.apiHead(base + "/s").send();
        assertEquals(seen.get("method"), "HEAD");
        support.apiOptions(base + "/s").send();
        assertEquals(seen.get("method"), "OPTIONS");
    }

    @Test
    public void repeatedQueryKeysAndReplaceSemantics() throws Exception {
        String base = recordingServer();

        ApiClient.get(base + "/q").addQueryParam("id", 1).addQueryParam("id", 2).queryParam("page", 1)
                .addQueryParams("tag", List.of("a b", "c&d")).send();
        assertEquals(seen.get("query"), "id=1&id=2&page=1&tag=a+b&tag=c%26d");

        ApiClient.get(base + "/q").addQueryParam("id", 1).addQueryParam("id", 2).queryParam("id", 9).send();
        assertEquals(seen.get("query"), "id=9");
    }

    @Test
    public void rawBytesBodyIsSentUnchanged() throws Exception {
        String base = recordingServer();
        byte[] payload = {0, 1, 2, (byte) 0xFF, 10, 13};

        ApiClient.post(base + "/upload").body(payload).send();
        assertEquals((byte[]) seen.get("body"), payload);
        assertEquals(seen.get("contentType"), "application/octet-stream");

        ApiClient.put(base + "/upload").contentType("image/png").body(payload).send();
        assertEquals(seen.get("contentType"), "image/png");

        Object asObject = payload;
        ApiClient.post(base + "/upload").body(asObject).send();
        assertEquals((byte[]) seen.get("body"), payload, "byte[] passed as Object is also sent raw");
    }

    @Test
    public void lowercaseContentTypeIsNotDuplicatedBySpec() throws Exception {
        String base = recordingServer();
        ApiClient.setTestSpec(io.testfly.client.ApiRequestSpec.builder().contentType("application/xml").build());
        ApiClient.post(base + "/x").header("content-type", "text/plain").body("hello").send();
        assertEquals(seen.get("contentType"), "text/plain");
    }

    @Test
    public void requestBodyIsLoggedWhenBodyLoggingEnabled() throws Exception {
        String base = recordingServer();
        config.getApi().setLogBody(true);

        ApiClient.post(base + "/json").body(Map.of("name", "Ada")).send();
        assertTrue(steps().contains("Request body: {\"name\":\"Ada\"}"), steps());

        Path file = Files.createTempFile("testfly", ".bin");
        try {
            Files.write(file, new byte[]{1, 2, 3});
            ApiClient.post(base + "/upload").multipart("file", file).send();
        } finally {
            Files.deleteIfExists(file);
        }
        assertTrue(steps().contains("Request body: <binary, "), steps());
        assertTrue(steps().contains("multipart/form-data"), steps());
    }

    @Test
    public void requestBodyIsNotLoggedByDefault() throws Exception {
        String base = recordingServer();
        ApiClient.post(base + "/json").body("{\"secret\":\"x\"}").send();
        assertFalse(steps().contains("Request body"), steps());
    }
}
