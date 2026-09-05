package io.testfly.network;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Unit tests for {@link NetworkAssert}. Seeds recorded requests via the
 * package-private {@code recordForTest} hook — no browser required.
 */
public class NetworkAssertTest {

    @AfterMethod
    public void cleanup() {
        NetworkMock.cleanup();
    }

    private NetworkMock seed(RecordedRequest... requests) {
        NetworkMock mock = NetworkMock.get();
        for (RecordedRequest r : requests) mock.recordForTest(r);
        return mock;
    }

    private RecordedRequest req(String url, String method, Map<String, String> headers, String body) {
        return new RecordedRequest(url, method, headers, body, Instant.now());
    }

    // ------------------------------------------------------------------
    // hasCount / request scoping
    // ------------------------------------------------------------------

    @Test
    public void hasCount_matchesScopedRequests() {
        NetworkMock mock = seed(
                req("https://h/api/a", "GET", Map.of(), null),
                req("https://h/api/a", "GET", Map.of(), null),
                req("https://h/api/b", "GET", Map.of(), null));
        mock.assertThat().request("**/api/a").hasCount(2);
        mock.assertThat().request("**/api/b").hasCount(1);
    }

    @Test
    public void hasCount_mismatch_throwsWithDetail() {
        NetworkMock mock = seed(req("https://h/api/a", "GET", Map.of(), null));
        try {
            mock.assertThat().request("**/api/a").hasCount(5);
            fail("expected AssertionError");
        } catch (AssertionError e) {
            assertTrue(e.getMessage().contains("expected 5"), e.getMessage());
            assertTrue(e.getMessage().contains("found 1"), e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // hasMethod / hasHeader
    // ------------------------------------------------------------------

    @Test
    public void hasMethod_caseInsensitive() {
        NetworkMock mock = seed(req("https://h/api/x", "POST", Map.of(), "{}"));
        mock.assertThat().request("**/api/x").hasMethod("post");
    }

    @Test
    public void hasHeader_presenceValueAndPredicate() {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("Authorization", "Bearer abc123");
        NetworkMock mock = seed(req("https://h/api/x", "GET", h, null));

        mock.assertThat().request("**/api/x").hasHeader("authorization");
        mock.assertThat().request("**/api/x").hasHeader("Authorization", "Bearer abc123");
        mock.assertThat().request("**/api/x")
                .hasHeader("authorization", v -> v.startsWith("Bearer "));
    }

    @Test
    public void hasHeader_missing_throws() {
        NetworkMock mock = seed(req("https://h/api/x", "GET", Map.of(), null));
        try {
            mock.assertThat().request("**/api/x").hasHeader("x-absent");
            fail("expected AssertionError");
        } catch (AssertionError e) {
            assertTrue(e.getMessage().contains("x-absent"), e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // hasJsonBody
    // ------------------------------------------------------------------

    @Test
    public void hasJsonBody_matchesPointerValue() {
        NetworkMock mock = seed(req("https://h/api/order", "POST", Map.of(),
                "{\"currency\":\"USD\",\"amount\":42}"));
        mock.assertThat().request("**/api/order").hasJsonBody("/currency", "USD");
        mock.assertThat().request("**/api/order").hasJsonBody("/amount", 42);
    }

    @Test
    public void hasJsonBody_predicate() {
        NetworkMock mock = seed(req("https://h/api/order", "POST", Map.of(),
                "{\"amount\":100}"));
        mock.assertThat().request("**/api/order")
                .hasJsonBody("/amount", node -> node.asInt() >= 50);
    }

    @Test
    public void hasJsonBody_nonJsonBody_throwsCleanMessage() {
        NetworkMock mock = seed(req("https://h/api/x", "POST", Map.of(), "not-json"));
        try {
            mock.assertThat().request("**/api/x").hasJsonBody("/a", "b");
            fail("expected AssertionError");
        } catch (AssertionError e) {
            assertTrue(e.getMessage().contains("valid JSON body"), e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Unsupported browser: empty recorded set
    // ------------------------------------------------------------------

    @Test
    public void emptyRecorded_trafficExpectation_failsWithInterceptionMessage() {
        NetworkMock mock = NetworkMock.get(); // nothing recorded, no CDP active
        try {
            mock.assertThat().request("**/api").hasCount(1);
            fail("expected AssertionError");
        } catch (AssertionError e) {
            assertTrue(e.getMessage().contains("interception is unavailable")
                            || e.getMessage().contains("Chromium-only"),
                    "should explain interception was unavailable: " + e.getMessage());
        }
    }
}
