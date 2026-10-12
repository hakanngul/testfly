package io.testfly.unit.client;

import io.testfly.client.ApiClient;
import io.testfly.client.ApiRequest;
import io.testfly.client.ApiResponse;
import org.testng.annotations.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.testng.Assert.*;

@Test(singleThreaded = true)
public class ApiResponseBodyTest extends ApiClientTestBase {

    private static final byte[] BINARY = {(byte) 0x89, 'P', 'N', 'G', 0, (byte) 0xFF, (byte) 0xFE, 10};

    @Test
    public void bodyIsDecodedWithResponseCharset() throws Exception {
        byte[] latin1 = "café".getBytes(StandardCharsets.ISO_8859_1);
        String base = server(ex -> reply(ex, 200, latin1, Map.of("Content-Type", "text/plain; charset=ISO-8859-1")));

        ApiResponse res = ApiClient.get(base + "/text").send();

        assertEquals(res.body(), "café");
        assertEquals(res.asBytes(), latin1);
        assertEquals(res.charset(), StandardCharsets.ISO_8859_1);
        assertEquals(res.contentType(), "text/plain; charset=ISO-8859-1");
    }

    @Test
    public void unknownCharsetFallsBackToUtf8() throws Exception {
        String base = server(ex -> reply(ex, 200, "ğüş", Map.of("Content-Type", "text/plain; charset=\"no-such-charset\"")));
        ApiResponse res = ApiClient.get(base + "/text").send();
        assertEquals(res.body(), "ğüş");
        assertEquals(res.charset(), StandardCharsets.UTF_8);
    }

    @Test
    public void binaryBodySurvivesTransportAndRebuilds() throws Exception {
        String base = server(ex -> reply(ex, 200, BINARY, Map.of("Content-Type", "application/octet-stream")));

        ApiResponse res = ApiClient.get(base + "/file.png").send();

        assertEquals(res.asBytes(), BINARY);
        assertEquals(res.newBuilder().build().asBytes(), BINARY, "newBuilder must copy raw bytes, not a re-encoded string");
        assertNotNull(res.request(), "transport response carries the sent request");
    }

    @Test
    public void saveToWritesRawBytes() throws Exception {
        String base = server(ex -> reply(ex, 200, BINARY, Map.of("Content-Type", "application/octet-stream")));
        Path dir = Files.createTempDirectory("testfly-api");
        try {
            Path saved = ApiClient.get(base + "/f").send().saveTo(dir.resolve("nested/out.bin"));
            assertEquals(Files.readAllBytes(saved), BINARY);
        } finally {
            try (var files = Files.walk(dir)) {
                files.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    @Test
    public void headersAccessorsAndSuccessFlag() throws Exception {
        String base = server(ex -> {
            ex.getResponseHeaders().add("X-Multi", "a");
            ex.getResponseHeaders().add("X-Multi", "b");
            reply(ex, 204, "");
        });

        ApiResponse res = ApiClient.get(base + "/h").send();

        assertEquals(res.headerValues("x-multi"), java.util.List.of("a", "b"));
        assertTrue(res.headers().containsKey("x-multi") || res.headers().containsKey("X-Multi"));
        assertTrue(res.isSuccessful());
        assertEquals(res.asBytes().length, 0);
        assertEquals(res.body(), "");
    }

    @Test
    public void builderSupportsTextAndBytes() {
        ApiRequest request = ApiRequest.builder().uri(URI.create("http://localhost/x")).build();
        ApiResponse text = ApiResponse.builder().request(request).status(200)
                .header("Content-Type", "text/plain; charset=UTF-16").body("hi").build();
        assertEquals(text.asBytes(), "hi".getBytes(StandardCharsets.UTF_16));
        assertEquals(text.body(), "hi");

        ApiResponse bytes = ApiResponse.builder().request(request).status(500).body(BINARY).build();
        assertEquals(bytes.asBytes(), BINARY);
        assertFalse(bytes.isSuccessful());
    }

    @Test
    public void prettyBodyFormatsJsonAndKeepsText() {
        ApiRequest request = ApiRequest.builder().uri(URI.create("http://localhost/x")).build();
        String pretty = ApiResponse.builder().request(request).status(200).body("{\"a\":1}").build().prettyBody();
        assertTrue(pretty.contains("\n"), pretty);
        assertEquals(ApiResponse.builder().request(request).status(200).body("not json").build().prettyBody(), "not json");
    }
}
