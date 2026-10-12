package io.testfly.unit.client;

import io.testfly.client.ApiAuth;
import io.testfly.client.ApiClient;
import io.testfly.client.ApiHmacSigner;
import io.testfly.config.TestFlyConfig.Api.AuthStrategy;
import io.testfly.internal.api.AuthStrategies;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.testng.Assert.*;

@Test(singleThreaded = true)
public class AuthStrategiesTest extends ApiClientTestBase {

    private final Map<String, String> seen = new ConcurrentHashMap<>();

    @AfterMethod(alwaysRun = true)
    public void clearProperty() {
        System.clearProperty("TESTFLY_AUTH_TOKEN");
    }

    private String echoServer() throws Exception {
        return server(ex -> {
            seen.clear();
            ex.getRequestHeaders().forEach((k, v) -> seen.put(k.toLowerCase(), v.get(0)));
            seen.put("query", String.valueOf(ex.getRequestURI().getQuery()));
            reply(ex, 200, "ok");
        });
    }

    private static AuthStrategy strategy(String type) {
        AuthStrategy s = new AuthStrategy();
        s.setType(type);
        return s;
    }

    private void send(String base, ApiAuth auth) {
        ApiClient.get(base + "/x").auth(auth).send();
    }

    @Test
    public void headerStrategiesAndEnvPlaceholders() throws Exception {
        String base = echoServer();
        System.setProperty("TESTFLY_AUTH_TOKEN", "from-env");

        AuthStrategy bearer = strategy("bearer");
        bearer.setToken("${TESTFLY_AUTH_TOKEN}");
        send(base, AuthStrategies.resolve(bearer));
        assertEquals(seen.get("authorization"), "Bearer from-env");

        AuthStrategy basic = strategy("basic");
        basic.setUsername("u");
        basic.setPassword("p");
        send(base, AuthStrategies.resolve(basic));
        assertEquals(seen.get("authorization"), "Basic dTpw");

        AuthStrategy key = strategy("apiKey");
        key.setHeaderName("X-Key");
        key.setApiKey("k");
        send(base, AuthStrategies.resolve(key));
        assertEquals(seen.get("x-key"), "k");

        AuthStrategy query = strategy("apikey-query");
        query.setToken("q");
        send(base, AuthStrategies.resolve(query));
        assertEquals(seen.get("query"), "api_key=q");

        assertEquals(AuthStrategies.resolveEnvVar("${TESTFLY_UNSET_VAR_XYZ}"), "${TESTFLY_UNSET_VAR_XYZ}");
    }

    @Test
    public void signingAndChallengeStrategiesResolveToInterceptingAuth() throws Exception {
        String base = echoServer();

        AuthStrategy signed = strategy("hmac-signed");
        signed.setApiKey("k");
        signed.setSecret("s");
        signed.setSignatureHeader("X-Sig");
        signed.setTimestampHeader("none");
        signed.setEncoding("hex");
        ApiAuth auth = AuthStrategies.resolve(signed);
        assertTrue(auth instanceof ApiHmacSigner);
        send(base, auth);
        assertEquals(seen.get("x-api-key"), "k");
        assertNotNull(seen.get("x-sig"));
        assertTrue(seen.get("x-sig").matches("[0-9a-f]{64}"), seen.get("x-sig"));
        assertNull(seen.get("x-timestamp"));

        AuthStrategy legacy = strategy("hmac");
        legacy.setApiKey("k");
        legacy.setSecret("s");
        send(base, AuthStrategies.resolve(legacy));
        assertEquals(seen.get("x-api-key"), "k");
        assertNull(seen.get("x-timestamp"));

        AuthStrategy digest = strategy("digest");
        digest.setUsername("u");
        digest.setPassword("p");
        assertTrue(AuthStrategies.resolve(digest) instanceof ApiAuth.Intercepting);
    }

    @Test
    public void oauthStrategiesResolve() {
        AuthStrategy refresh = strategy("oauth2-refresh");
        refresh.setTokenUrl("http://127.0.0.1:1/token");
        refresh.setClientId("c");
        refresh.setRefreshToken("r");
        assertNotNull(AuthStrategies.resolve(refresh));
        assertNotNull(AuthStrategies.resolve(strategy("oauth2")));
        assertNotNull(AuthStrategies.resolve(strategy("oauth2_password")));
    }

    @Test
    public void unknownAndMissingTypes() {
        assertNull(AuthStrategies.resolve(new AuthStrategy()));
        IllegalArgumentException error = expectThrows(IllegalArgumentException.class,
                () -> AuthStrategies.resolve(strategy("kerberos")));
        assertTrue(error.getMessage().contains("hmac-signed"), error.getMessage());
        assertTrue(error.getMessage().contains("oauth2-refresh"), error.getMessage());
    }
}
