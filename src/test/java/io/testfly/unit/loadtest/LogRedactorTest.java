package io.testfly.unit.loadtest;

import io.testfly.loadtest.internal.LogRedactor;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.*;

/**
 * Tests {@link LogRedactor}: credentials in Gatling subprocess log lines are masked before the
 * log tail is put into an exception message.
 */
@Test(singleThreaded = true)
public class LogRedactorTest {

    private static void assertMasked(String line, String secret) {
        String out = LogRedactor.redact(line);
        assertFalse(out.contains(secret), "secret leaked: " + out);
        assertTrue(out.contains(LogRedactor.MASK), "no mask in: " + out);
    }

    @Test
    public void testHeaderStyleSecrets() {
        assertMasked("Authorization: Bearer eyJhbGciOi.payload.sig", "eyJhbGciOi");
        assertMasked("authorization=Basic dXNlcjpwYXNz", "dXNlcjpwYXNz");
        assertMasked("Cookie: SESSION=abc123; other=1", "abc123");
        assertMasked("set-cookie: JSESSIONID=s3cr3t; Path=/", "s3cr3t");
        assertMasked("\"Authorization\":\"Bearer xyz987\"", "xyz987");
    }

    @Test
    public void testBearerAnywhere() {
        assertMasked("request failed for bearer tok_live_42 at /api", "tok_live_42");
    }

    @Test
    public void testKeyValueSecrets() {
        assertMasked("GET /api?token=abc123&page=2", "abc123");
        assertMasked("{\"password\":\"hunter2\",\"user\":\"bob\"}", "hunter2");
        assertMasked("client_secret: shh-very-secret", "shh-very-secret");
        assertMasked("x-api-key=K-1", "K-1");
        assertMasked("API_KEY=K-2", "K-2");
        assertMasked("apikey: K-3", "K-3");
        assertMasked("access_token=at.value", "at.value");
    }

    @Test
    public void testQuotedValuesAreMaskedUpToClosingQuote() {
        String out = LogRedactor.redact("{\"password\" : \"my secret pass\", \"user\":\"bob\"}");
        assertFalse(out.contains("my") || out.contains("secret pass") || out.contains("pass\""), out);
        assertTrue(out.contains("\"user\":\"bob\""), "non-secret keys stay readable: " + out);
        assertMasked("token='abc def ghi'", "def ghi");
        assertMasked("{\\\"api_key\\\":\\\"k with spaces\\\"}", "with spaces");
    }

    @Test
    public void testHeaderNamesContainingSensitiveWordsAreMasked() {
        assertMasked("X-Auth-Token: abc def", "abc def");
        assertMasked("x-goog-api-key: AIzaSy123", "AIzaSy123");
        assertMasked("X-Amz-Signature: deadbeef", "deadbeef");
        assertMasked("Proxy-Authorization: Basic Zm9vOmJhcg==", "Zm9vOmJhcg");
        assertMasked("X-Client-Secret: s3", "s3");
        assertMasked("x-session-cookie: sid=1", "sid=1");
        assertEquals(LogRedactor.redact("Content-Type: application/json"), "Content-Type: application/json");
    }

    @Test
    public void testSensitiveQueryParamsAreMasked() {
        assertMasked("GET https://h/x?key=AIza123&page=1", "AIza123");
        assertMasked("GET /x?a=1&sig=abcd1234", "abcd1234");
        assertMasked("GET /x?signature=ZZZ", "ZZZ");
        assertMasked("GET /x?apikey=K9&b=2", "K9");
        assertTrue(LogRedactor.redact("GET /x?key=AIza123&page=1").contains("page=1"));
        assertEquals(LogRedactor.redact("GET /x?monkey=1&keyword=2"), "GET /x?monkey=1&keyword=2");
    }

    @Test
    public void testNonSecretContentIsKept() {
        String line = "Simulation io.testfly.Sim failed: #{unclosed is not a valid EL at page=2";
        assertEquals(LogRedactor.redact(line), line);
        assertTrue(LogRedactor.redact("GET /api?token=abc&page=2").contains("page=2"));
        assertNull(LogRedactor.redact(null));
        assertEquals(LogRedactor.redact(""), "");
    }

    @Test
    public void testRedactedTailLimitsLinesAndLength() {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            lines.add("line-" + i);
        }
        lines.add("password=topsecret " + "x".repeat(500));

        String tail = LogRedactor.redactedTail(lines, 20, 300);

        assertFalse(tail.contains("line-10"), tail);
        assertFalse(tail.contains("line-9"), "only the last 20 lines");
        assertTrue(tail.contains("line-11"));
        assertFalse(tail.contains("topsecret"));
        for (String l : tail.split("\n")) {
            assertTrue(l.length() <= "  [gatling] ".length() + 300 + "...".length(), "line too long: " + l.length());
        }
    }
}
