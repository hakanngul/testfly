package io.testfly.unit.client;

import io.testfly.client.ApiAuth;
import io.testfly.client.ApiClient;
import io.testfly.client.ApiHmacSigner;
import io.testfly.client.ApiRequest;
import org.testng.annotations.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.testng.Assert.*;

@Test(singleThreaded = true)
public class ApiAuthHmacTest extends ApiClientTestBase {

    private static final Clock FIXED = Clock.fixed(Instant.ofEpochSecond(1_700_000_000L), ZoneOffset.UTC);
    private final Map<String, String> seen = new ConcurrentHashMap<>();

    private String recordingServer() throws Exception {
        return server(ex -> {
            seen.clear();
            ex.getRequestHeaders().forEach((k, v) -> seen.put(k.toLowerCase(), v.get(0)));
            seen.put("body", new String(readBody(ex), StandardCharsets.UTF_8));
            seen.put("pathAndQuery", ex.getRequestURI().getRawPath()
                    + (ex.getRequestURI().getRawQuery() == null ? "" : "?" + ex.getRequestURI().getRawQuery()));
            seen.put("method", ex.getRequestMethod());
            reply(ex, 200, "ok");
        });
    }

    private static byte[] mac(String secret, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256Hex(String s) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void defaultSignerSignsMethodPathTimestampAndBody() throws Exception {
        String base = recordingServer();
        ApiAuth signer = ApiAuth.hmacSigner().apiKey("key-1").secret("s3cret").clock(FIXED).build();

        ApiClient.post(base + "/orders").queryParam("dry", true).body("{\"id\":1}").auth(signer).send().assertStatus(200);

        String canonical = "POST\n/orders?dry=true\n1700000000\n" + sha256Hex("{\"id\":1}");
        assertEquals(seen.get("x-api-key"), "key-1");
        assertEquals(seen.get("x-timestamp"), "1700000000");
        assertEquals(seen.get("x-signature"), Base64.getEncoder().encodeToString(mac("s3cret", canonical)));
    }

    @Test
    public void customHeadersHexEncodingAndCanonical() throws Exception {
        String base = recordingServer();
        ApiAuth signer = ApiAuth.hmacSigner()
                .secret("k")
                .keyHeader(null)
                .signatureHeader("X-Sig")
                .timestampHeader("X-Date")
                .timestampFormat(Instant::toString)
                .encoding(ApiHmacSigner.Encoding.HEX)
                .canonical(ctx -> ctx.method() + " " + ctx.pathAndQuery() + " " + ctx.timestamp())
                .clock(FIXED)
                .build();

        ApiClient.get(base + "/a").auth(signer).send();

        assertNull(seen.get("x-api-key"));
        assertEquals(seen.get("x-date"), "2023-11-14T22:13:20Z");
        assertEquals(seen.get("x-sig"), HexFormat.of().formatHex(mac("k", "GET /a 2023-11-14T22:13:20Z")));
    }

    @Test
    public void signatureIsComputedPerRequestForGlobalAuthAndAsync() throws Exception {
        String base = recordingServer();
        ApiHmacSigner signer = ApiAuth.hmacSigner().secret("s").clock(FIXED).build();
        ApiClient.setGlobalAuth(signer);

        ApiClient.get(base + "/one").sendAsync().get();
        String one = seen.get("x-signature");
        ApiClient.get(base + "/two").send();
        String two = seen.get("x-signature");

        assertNotEquals(one, two);
        ApiRequest expected = ApiRequest.builder().method("GET").uri(URI.create(base + "/two")).build();
        assertEquals(two, signer.sign(expected, "1700000000"));
    }

    @Test
    @SuppressWarnings("deprecation")
    public void legacyHmacKeepsItsWireFormat() throws Exception {
        String base = recordingServer();

        ApiClient.get(base + "/a").auth(ApiAuth.hmac("key-1", "s3cret", null)).send();

        assertEquals(seen.get("x-api-key"), "key-1");
        assertEquals(seen.get("x-signature"), Base64.getEncoder().encodeToString(mac("s3cret", "key-1")));
        assertNull(seen.get("x-timestamp"));
    }

    @Test
    public void invalidConfigurationRejected() {
        expectThrows(NullPointerException.class, () -> ApiAuth.hmacSigner().build());
        expectThrows(IllegalArgumentException.class, () -> ApiAuth.hmacSigner().secret("s").algorithm("NoSuchMac").build());
    }
}
