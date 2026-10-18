package io.testfly.mobile.internal.rpc;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.testng.Assert.*;

public class JsonRpcOptionsTest {
    @Test
    public void canonicalIdentityPreservesProxyPrefixAndEffectivePort() {
        JsonRpcOptions a = JsonRpcOptions.defaults(URI.create("HTTP://LOCALHOST/lab/mobile/"));
        JsonRpcOptions b = JsonRpcOptions.defaults(URI.create("http://localhost:80/lab/mobile"));
        assertEquals(a.normalizedServerUrl(), b.normalizedServerUrl());
        assertEquals(a.rpcEndpoint(), URI.create("http://localhost:80/lab/mobile/rpc"));
        assertEquals(JsonRpcOptions.defaults(URI.create("http://127.0.0.1/")).normalizedServerUrl(),
                URI.create("http://127.0.0.1:80"));
        assertEquals(JsonRpcOptions.defaults(URI.create("http://[::1]")).normalizedServerUrl(),
                JsonRpcOptions.defaults(URI.create("http://[0:0:0:0:0:0:0:1]:80/")).normalizedServerUrl());
    }

    @Test
    public void localAndExplicitHttpsRemoteEndpointsAreAccepted() {
        JsonRpcOptions.defaults(URI.create("http://127.2.3.4:12000"));
        JsonRpcOptions.defaults(URI.create("https://localhost:12000"));
        JsonRpcOptions remote = new JsonRpcOptions(URI.create("https://lab.example/device"), true,
                Map.of(), Duration.ofSeconds(2), 1024);
        assertEquals(remote.rpcEndpoint(), URI.create("https://lab.example:443/device/rpc"));
    }

    @DataProvider
    public Object[][] rejectedEndpoints() {
        return new Object[][] {
                {"http://lab.example", false}, {"https://lab.example", false},
                {"http://lab.example", true}, {"http://192.168.1.1", true},
                {"ftp://127.0.0.1", false}, {"/relative", false},
                {"http://user:secret@127.0.0.1", false},
                {"http://127.0.0.1?token=secret", false},
                {"http://127.0.0.1#secret", false},
                {"http://127.0.0.1:0", false}, {"http://127.0.0.1:65536", false},
                {"http://127.1", false}, {"http://127.000.0.1", false},
                {"http://127.0.0.1/lab/../other", false},
                {"http://127.0.0.1/lab/%2e%2e/other", false},
                {"http://127.0.0.1/lab%2frpc", false},
                {"http://127.0.0.1/lab%5crpc", false},
                {"http://127.0.0.1/lab//other", false}
        };
    }

    @Test(dataProvider = "rejectedEndpoints")
    public void rejectsUnapprovedOrAmbiguousEndpoints(String url, boolean allowRemote) {
        IllegalArgumentException error = expectThrows(IllegalArgumentException.class,
                () -> new JsonRpcOptions(URI.create(url), allowRemote, Map.of(), Duration.ofSeconds(1), 1024));
        assertFalse(error.toString().contains("secret"));
    }

    @Test
    public void headersAreCopiedImmutableAndRedacted() {
        Map<String, String> source = new HashMap<>(Map.of("Authorization", "Bearer private-token"));
        JsonRpcOptions options = new JsonRpcOptions(URI.create("http://127.0.0.1"), false, source,
                Duration.ofSeconds(1), 1024);
        source.put("Authorization", "modified");
        assertEquals(options.headers().get("Authorization"), "Bearer private-token");
        expectThrows(UnsupportedOperationException.class, () -> options.headers().put("X-New", "value"));
        assertFalse(options.toString().contains("private-token"));
        assertFalse(options.toString().contains("Authorization"));
    }

    @Test
    public void invalidHeadersAndBoundsFailWithoutEchoingSecrets() {
        for (Map<String, String> headers : java.util.List.of(
                Map.of("Authorization", "Bearer secret\r\nX-Injected: yes"),
                Map.of("Host", "secret"), Map.of("X-Secret", "one", "x-secret", "secret"))) {
            IllegalArgumentException error = expectThrows(IllegalArgumentException.class,
                    () -> new JsonRpcOptions(URI.create("http://localhost"), false, headers,
                            Duration.ofSeconds(1), 1024));
            assertFalse(error.toString().contains("secret"));
        }
        expectThrows(IllegalArgumentException.class, () -> new JsonRpcOptions(URI.create("http://localhost"),
                false, Map.of(), Duration.ZERO, 1024));
        expectThrows(IllegalArgumentException.class, () -> new JsonRpcOptions(URI.create("http://localhost"),
                false, Map.of(), Duration.ofSeconds(Long.MAX_VALUE), 1024));
        expectThrows(IllegalArgumentException.class, () -> new JsonRpcOptions(URI.create("http://localhost"),
                false, Map.of(), Duration.ofSeconds(1), 0));
    }
}
