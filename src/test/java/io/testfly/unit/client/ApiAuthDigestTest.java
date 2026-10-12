package io.testfly.unit.client;

import com.sun.net.httpserver.HttpExchange;
import io.testfly.client.ApiAuth;
import io.testfly.client.ApiClient;
import io.testfly.client.ApiResponse;
import io.testfly.internal.api.ApiExecution;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.testng.Assert.*;

/** Verifies the client against an independent, minimal RFC 7616 server implementation. */
@Test(singleThreaded = true)
public class ApiAuthDigestTest extends ApiClientTestBase {

    private static final String REALM = "testfly@example";
    private static final String USER = "Mufasa";
    private static final String PASSWORD = "Circle of Life";

    private final AtomicInteger hits = new AtomicInteger();
    private final List<String> authorizations = new CopyOnWriteArrayList<>();
    private volatile String nonce;
    private volatile String algorithm;
    private volatile String qop;
    private volatile boolean rotateAfterSuccess;
    private volatile List<String> extraChallenges;

    @BeforeMethod
    public void resetServerState() {
        hits.set(0);
        authorizations.clear();
        nonce = "n-1";
        algorithm = "MD5";
        qop = "auth";
        rotateAfterSuccess = false;
        extraChallenges = List.of();
    }

    private String digestServer() throws Exception {
        return server(this::handle);
    }

    private void handle(HttpExchange ex) throws IOException {
        hits.incrementAndGet();
        byte[] body = readBody(ex);
        String header = ex.getRequestHeaders().getFirst("Authorization");
        if (header != null) authorizations.add(header);
        if (header == null || !header.startsWith("Digest ")) {
            challenge(ex, false);
            return;
        }
        Map<String, String> p = params(header.substring(7));
        if (!nonce.equals(p.get("nonce"))) {
            challenge(ex, true);
            return;
        }
        if (!expected(p, ex.getRequestMethod(), body).equals(p.get("response"))) {
            challenge(ex, false);
            return;
        }
        if (rotateAfterSuccess) nonce = nonce + "-next";
        reply(ex, 200, "ok nc=" + p.get("nc"));
    }

    private void challenge(HttpExchange ex, boolean stale) throws IOException {
        extraChallenges.forEach(c -> ex.getResponseHeaders().add("WWW-Authenticate", c));
        ex.getResponseHeaders().add("WWW-Authenticate", "Digest realm=\"" + REALM + "\", qop=\"" + qop
                + "\", algorithm=" + algorithm + ", nonce=\"" + nonce + "\", opaque=\"op-1\""
                + (stale ? ", stale=true" : ""));
        reply(ex, 401, "unauthorized");
    }

    private String expected(Map<String, String> p, String method, byte[] body) {
        String alg = p.get("algorithm");
        String name = alg.replace("-sess", "").replace("-SESS", "").equals("SHA-256") ? "SHA-256" : "MD5";
        String ha1 = h(name, p.get("username") + ":" + p.get("realm") + ":" + PASSWORD);
        if (alg.toLowerCase().endsWith("-sess")) ha1 = h(name, ha1 + ":" + p.get("nonce") + ":" + p.get("cnonce"));
        String a2 = method + ":" + p.get("uri");
        if ("auth-int".equals(p.get("qop"))) a2 += ":" + h(name, body);
        return h(name, ha1 + ":" + p.get("nonce") + ":" + p.get("nc") + ":" + p.get("cnonce") + ":" + p.get("qop")
                + ":" + h(name, a2));
    }

    private static String h(String alg, String s) {
        return h(alg, s.getBytes(StandardCharsets.UTF_8));
    }

    private static String h(String alg, byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(alg).digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Map<String, String> params(String s) {
        Map<String, String> map = new LinkedHashMap<>();
        Matcher m = Pattern.compile("(\\w+)=(?:\"((?:[^\"\\\\]|\\\\.)*)\"|([^,\\s]+))").matcher(s);
        while (m.find()) map.put(m.group(1), m.group(2) != null ? m.group(2).replace("\\\"", "\"") : m.group(3));
        return map;
    }

    @Test
    public void md5ChallengeThenPreemptiveAuth() throws Exception {
        String base = digestServer();
        ApiAuth auth = ApiAuth.digest(USER, PASSWORD);

        ApiResponse first = ApiClient.get(base + "/dir/index.html?x=1").auth(auth).send();
        assertEquals(first.status(), 200);
        assertEquals(hits.get(), 2, "challenge + authorized retry");
        Map<String, String> p = params(authorizations.get(0).substring(7));
        assertEquals(p.get("uri"), "/dir/index.html?x=1");
        assertEquals(p.get("opaque"), "op-1");
        assertEquals(p.get("username"), USER);

        hits.set(0);
        ApiResponse second = ApiClient.get(base + "/dir/other").auth(auth).send();
        assertEquals(second.status(), 200);
        assertEquals(hits.get(), 1, "cached challenge authenticates preemptively");
        assertEquals(second.body(), "ok nc=00000002");
    }

    @Test
    public void sha256SessionWithAuthIntBody() throws Exception {
        algorithm = "SHA-256-sess";
        qop = "auth-int";
        String base = digestServer();

        ApiResponse res = ApiClient.post(base + "/orders").body("{\"id\":1}").auth(ApiAuth.digest(USER, PASSWORD)).send();

        assertEquals(res.status(), 200);
        assertTrue(authorizations.get(0).contains("qop=auth-int"), authorizations.get(0));
        assertTrue(authorizations.get(0).contains("algorithm=SHA-256-sess"), authorizations.get(0));
    }

    @Test
    public void staleNonceTriggersSingleReauthentication() throws Exception {
        rotateAfterSuccess = true;
        String base = digestServer();
        ApiAuth auth = ApiAuth.digest(USER, PASSWORD);
        ApiClient.get(base + "/a").auth(auth).send().assertStatus(200);

        hits.set(0);
        ApiClient.get(base + "/b").auth(auth).send().assertStatus(200);
        assertEquals(hits.get(), 2, "stale nonce: one rejected attempt and one re-authenticated attempt");
    }

    @Test
    public void wrongPasswordReturnsFinal401WithoutLooping() throws Exception {
        String base = digestServer();
        ApiAuth wrong = ApiAuth.digest(USER, "wrong");

        assertEquals(ApiClient.get(base + "/a").auth(wrong).send().status(), 401);
        assertEquals(hits.get(), 2);

        hits.set(0);
        assertEquals(ApiClient.get(base + "/a").auth(wrong).send().status(), 401);
        assertEquals(hits.get(), 1);
    }

    @Test
    public void strongestChallengeIsChosenAndOtherSchemesIgnored() throws Exception {
        algorithm = "SHA-256";
        extraChallenges = List.of("Basic realm=\"x\"", "Digest realm=\"" + REALM + "\", qop=\"auth\", algorithm=MD5, nonce=\"n-1\"");
        String base = digestServer();

        ApiClient.get(base + "/a").auth(ApiAuth.digest(USER, PASSWORD)).send().assertStatus(200);
        assertTrue(authorizations.get(0).contains("algorithm=SHA-256"), authorizations.get(0));
    }

    @Test
    public void non401AndNonDigestResponsesPassThrough() throws Exception {
        String plain = server(ex -> {
            hits.incrementAndGet();
            ex.getResponseHeaders().add("WWW-Authenticate", "Bearer realm=\"api\"");
            reply(ex, 401, "nope");
        });
        assertEquals(ApiClient.get(plain + "/a").auth(ApiAuth.digest(USER, PASSWORD)).send().status(), 401);
        assertEquals(hits.get(), 1);
    }

    @Test
    public void challengeCacheIsTestScopedAndSharedWithAsyncCalls() throws Exception {
        String base = digestServer();
        ApiClient.setGlobalAuth(ApiAuth.digest(USER, PASSWORD));
        ApiClient.get(base + "/a").send().assertStatus(200);

        hits.set(0);
        ApiClient.get(base + "/async").sendAsync().get().assertStatus(200);
        assertEquals(hits.get(), 1, "async call reuses the test's cached challenge");

        ApiExecution.cleanupTestContext();
        hits.set(0);
        ApiClient.get(base + "/b").auth(ApiAuth.digest(USER, PASSWORD)).send().assertStatus(200);
        assertEquals(hits.get(), 2, "challenge cache is cleared after the test");
    }

    @Test
    public void digestWorksWithConfiguredRetry() throws Exception {
        var retry = config.getApi().getRetry();
        retry.setEnabled(true);
        retry.setMaxAttempts(3);
        retry.setBackoffMs(0);
        String base = digestServer();

        ApiClient.get(base + "/a").auth(ApiAuth.digest(USER, PASSWORD)).send().assertStatus(200);
        assertEquals(hits.get(), 2);
    }
}
