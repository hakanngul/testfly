package io.testfly.unit.client;

import io.testfly.client.ApiAuth;
import io.testfly.client.ApiClient;
import io.testfly.internal.api.ApiExecution;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.*;

@Test(singleThreaded = true)
public class ApiAuthOAuth2Test extends ApiClientTestBase {

    private final List<Map<String, String>> grants = new CopyOnWriteArrayList<>();
    private final AtomicInteger issued = new AtomicInteger();
    private final AtomicReference<String> seenAuth = new AtomicReference<>();
    private volatile int expiresIn;
    private volatile boolean issueRefresh;
    private volatile int tokenStatus;

    @BeforeMethod
    public void resetTokenServer() {
        grants.clear();
        issued.set(0);
        seenAuth.set(null);
        expiresIn = 3600;
        issueRefresh = false;
        tokenStatus = 200;
    }

    private String tokenServer() throws Exception {
        return server(ex -> {
            if (ex.getRequestURI().getPath().equals("/token")) {
                Map<String, String> form = new ConcurrentHashMap<>();
                for (String pair : new String(readBody(ex), StandardCharsets.UTF_8).split("&")) {
                    String[] kv = pair.split("=", 2);
                    form.put(kv[0], URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
                }
                grants.add(form);
                if (tokenStatus != 200) { reply(ex, tokenStatus, "{\"error\":\"invalid_client\"}"); return; }
                int n = issued.incrementAndGet();
                String refresh = issueRefresh ? ",\"refresh_token\":\"r-" + n + "\"" : "";
                reply(ex, 200, "{\"access_token\":\"t-" + n + "\",\"expires_in\":" + expiresIn + refresh + "}",
                        Map.of("Content-Type", "application/json"));
            } else {
                seenAuth.set(ex.getRequestHeaders().getFirst("Authorization"));
                reply(ex, 200, "ok");
            }
        });
    }

    @Test
    public void clientCredentialsTokenIsFetchedOnceAndCached() throws Exception {
        String base = tokenServer();
        ApiAuth auth = ApiAuth.oauth2(base + "/token", "cid", "csecret");

        ApiClient.get(base + "/a").auth(auth).send();
        ApiClient.get(base + "/b").auth(auth).send();

        assertEquals(grants.size(), 1);
        assertEquals(grants.get(0).get("grant_type"), "client_credentials");
        assertEquals(grants.get(0).get("client_id"), "cid");
        assertEquals(seenAuth.get(), "Bearer t-1");
    }

    @Test
    public void expiredTokenIsRefreshedWithRefreshTokenAndRotated() throws Exception {
        String base = tokenServer();
        expiresIn = 1; // already inside the 60 s safety margin
        issueRefresh = true;
        ApiAuth auth = ApiAuth.oauth2(base + "/token", "cid", "csecret");

        ApiClient.get(base + "/a").auth(auth).send();
        ApiClient.get(base + "/b").auth(auth).send();
        ApiClient.get(base + "/c").auth(auth).send();

        assertEquals(grants.get(0).get("grant_type"), "client_credentials");
        assertEquals(grants.get(1).get("grant_type"), "refresh_token");
        assertEquals(grants.get(1).get("refresh_token"), "r-1");
        assertEquals(grants.get(2).get("refresh_token"), "r-2", "rotated refresh token is used next");
        assertEquals(seenAuth.get(), "Bearer t-3");
    }

    @Test
    public void failedRefreshFallsBackToOriginalGrant() throws Exception {
        String base = tokenServer();
        expiresIn = 1;
        issueRefresh = true;
        ApiAuth auth = ApiAuth.oauth2Password(base + "/token", "cid", "cs", "ada", "pw");
        ApiClient.get(base + "/a").auth(auth).send();

        assertEquals(grants.get(0).get("grant_type"), "password");
        assertEquals(grants.get(0).get("username"), "ada");
        assertEquals(grants.get(0).get("password"), "pw");
        grants.clear();
        tokenStatus = 400;
        expectThrows(RuntimeException.class, () -> ApiClient.get(base + "/b").auth(auth).send());
        assertEquals(grants.get(0).get("grant_type"), "refresh_token");
        assertEquals(grants.get(1).get("grant_type"), "password");
    }

    @Test
    public void explicitRefreshGrant() throws Exception {
        String base = tokenServer();
        ApiClient.get(base + "/a").auth(ApiAuth.oauth2Refresh(base + "/token", "cid", "cs", "long-lived")).send();

        assertEquals(grants.get(0).get("grant_type"), "refresh_token");
        assertEquals(grants.get(0).get("refresh_token"), "long-lived");
        assertEquals(seenAuth.get(), "Bearer t-1");
    }

    @Test
    public void tokenCacheIsClearedExplicitlyAndAtSuiteEnd() throws Exception {
        String base = tokenServer();
        ApiAuth auth = ApiAuth.oauth2(base + "/token", "cid", "cs");
        ApiClient.get(base + "/a").auth(auth).send();

        ApiAuth.clearTokenCache();
        ApiClient.get(base + "/b").auth(auth).send();
        assertEquals(grants.size(), 2);

        ApiExecution.shutdown();
        ApiClient.get(base + "/c").auth(auth).send();
        assertEquals(grants.size(), 3);
    }

    @Test
    public void tokenEndpointErrorsAreReported() throws Exception {
        String base = tokenServer();
        tokenStatus = 401;
        RuntimeException error = expectThrows(RuntimeException.class,
                () -> ApiClient.get(base + "/a").auth(ApiAuth.oauth2(base + "/token", "cid", "bad")).send());
        assertTrue(error.getMessage().contains("HTTP 401"), error.getMessage());
    }
}
