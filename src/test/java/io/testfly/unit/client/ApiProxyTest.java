package io.testfly.unit.client;

import io.testfly.client.ApiClient;
import io.testfly.client.ApiException;
import io.testfly.client.ApiResponse;
import io.testfly.internal.api.ApiExecution;
import io.testfly.internal.api.ApiTransport;
import io.testfly.internal.api.ApiTransport.ProxySelection;
import org.testng.annotations.Test;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.*;

@Test(singleThreaded = true)
public class ApiProxyTest extends ApiClientTestBase {

    private final AtomicReference<String> proxiedUri = new AtomicReference<>();

    @org.testng.annotations.BeforeMethod
    public void resetProxyLog() {
        proxiedUri.set(null);
    }

    /** Minimal forward proxy: answers absolute-form requests itself and records the target URI. */
    private int startProxy() throws Exception {
        String base = server(ex -> {
            proxiedUri.set(ex.getRequestURI().toString());
            reply(ex, 200, "via-proxy", Map.of("X-Proxy", "yes"));
        });
        return URI.create(base).getPort();
    }

    @Test
    public void configuredProxyRoutesRequests() throws Exception {
        int port = startProxy();
        config.getApi().getProxy().setHost("127.0.0.1");
        config.getApi().getProxy().setPort(port);

        ApiResponse res = ApiClient.get("http://target.invalid:8081/orders?id=1").send();

        assertEquals(res.header("X-Proxy"), "yes");
        assertEquals(proxiedUri.get(), "http://target.invalid:8081/orders?id=1");
    }

    @Test
    public void nonProxyHostsAndNoProxyBypassTheProxy() throws Exception {
        int port = startProxy();
        String direct = server(ex -> reply(ex, 200, "direct"));
        config.getApi().getProxy().setHost("127.0.0.1");
        config.getApi().getProxy().setPort(port);

        config.getApi().getProxy().setNonProxyHosts(List.of("localhost", "127.*"));
        assertEquals(ApiClient.get(direct + "/a").send().body(), "direct");
        assertNull(proxiedUri.get());

        config.getApi().getProxy().setNonProxyHosts(List.of());
        assertEquals(ApiClient.get(direct + "/b").noProxy().send().body(), "direct");
        assertNull(proxiedUri.get());
    }

    @Test
    public void perRequestProxyOverridesConfiguration() throws Exception {
        int port = startProxy();

        ApiResponse res = ApiClient.get("http://target.invalid/x").proxy("127.0.0.1", port).send();
        assertEquals(res.body(), "via-proxy");

        expectThrows(ApiException.class, () -> ApiClient.get("http://target.invalid/x").send());
    }

    @Test
    public void profilesAreReusedPerProxySelection() {
        var api = config.getApi();
        var plain = ApiTransport.prepare(ApiExecution.scope(), api, null);
        var proxied = ApiTransport.prepare(ApiExecution.scope(), api, null, ProxySelection.of("127.0.0.1", 3128));
        var proxiedAgain = ApiTransport.prepare(ApiExecution.scope(), api, null, ProxySelection.of("127.0.0.1", 3128));
        var direct = ApiTransport.prepare(ApiExecution.scope(), api, null, ProxySelection.direct());

        assertSame(proxied.client(), proxiedAgain.client());
        assertNotSame(plain.client(), proxied.client());
        assertSame(plain.client(), direct.client());
    }

    @Test
    public void bypassPatternsAndValidation() {
        ProxySelection p = new ProxySelection("proxy", 8080, List.of("*.internal", "localhost", "10.*"));
        assertTrue(p.bypasses("api.internal"));
        assertTrue(p.bypasses("LOCALHOST"));
        assertTrue(p.bypasses("10.1.2.3"));
        assertFalse(p.bypasses("example.com"));
        assertFalse(p.bypasses("internal"));
        assertTrue(ProxySelection.direct().isDirect());
        expectThrows(IllegalArgumentException.class, () -> new ProxySelection("proxy", 0, List.of()));
        expectThrows(IllegalArgumentException.class, () -> ProxySelection.of(" ", 80));
    }
}
