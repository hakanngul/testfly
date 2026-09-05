package io.testfly.network;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests for the package-private {@link RouteRule}. No browser.
 */
public class RouteRuleTest {

    @Test
    public void matches_exactMethod() {
        RouteRule r = new RouteRule("**/api/x", "POST", Response.status(200), null,
                RouteRule.Source.ROUTE);
        assertTrue(r.matches("https://h/api/x", "POST"));
        assertFalse(r.matches("https://h/api/x", "GET"));
    }

    @Test
    public void matches_anyMethodWildcard() {
        RouteRule r = new RouteRule("**/api/x", "*", Response.status(200), null,
                RouteRule.Source.ROUTE);
        assertTrue(r.matches("https://h/api/x", "GET"));
        assertTrue(r.matches("https://h/api/x", "DELETE"));
    }

    @Test
    public void matches_methodCaseInsensitive() {
        RouteRule r = new RouteRule("**/api/x", "post", Response.status(200), null,
                RouteRule.Source.ROUTE);
        assertTrue(r.matches("https://h/api/x", "POST"));
        assertTrue(r.matches("https://h/api/x", "post"));
    }

    @Test
    public void matches_urlMustAlsoMatch() {
        RouteRule r = new RouteRule("**/api/x", "*", Response.status(200), null,
                RouteRule.Source.ROUTE);
        assertFalse(r.matches("https://h/api/y", "GET"));
    }

    @Test
    public void isExactMethod_and_isHandler() {
        RouteRule exact = new RouteRule("**/a", "GET", Response.status(200), null,
                RouteRule.Source.ROUTE);
        assertTrue(exact.isExactMethod());
        assertFalse(exact.isHandler());

        RouteRule anyH = new RouteRule("**/a", "*", null, route -> {},
                RouteRule.Source.ROUTE);
        assertFalse(anyH.isExactMethod());
        assertTrue(anyH.isHandler());
    }

    @Test
    public void nullOrBlankMethod_normalizesToWildcard() {
        RouteRule r1 = new RouteRule("**/a", null, Response.status(200), null,
                RouteRule.Source.ROUTE);
        RouteRule r2 = new RouteRule("**/a", "  ", Response.status(200), null,
                RouteRule.Source.ROUTE);
        assertEquals(r1.method, "*");
        assertEquals(r2.method, "*");
    }

    @Test
    public void fromLegacyStub_copiesResponseFields() {
        NetworkMock mock = NetworkMock.get();
        try {
            StubBuilder stub = mock.stub("**/legacy");
            // configure via existing fluent methods without registering CDP:
            // set fields directly through the returnStatus path would register;
            // instead build a bare StubBuilder-equivalent by reflection-free set.
            stub.statusCode   = 503;
            stub.responseBody = "{\"err\":true}";
            stub.contentType  = "application/json";
            stub.delayMs      = 250;

            RouteRule rule = RouteRule.fromLegacyStub(stub);
            assertEquals(rule.source, RouteRule.Source.LEGACY_STUB);
            assertEquals(rule.method, "*");
            assertEquals(rule.pattern, "**/legacy");
            assertEquals(rule.response.status(), 503);
            assertEquals(rule.response.body(), "{\"err\":true}");
            assertEquals(rule.response.contentType(), "application/json");
            assertEquals(rule.response.delayMs(), 250L);
        } finally {
            NetworkMock.cleanup();
        }
    }
}
