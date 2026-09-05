package io.testfly.network;

import io.testfly.exceptions.NetworkMockException;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Unit tests for {@link Response}. In package {@code io.testfly.network} to read
 * the package-private accessors. No browser.
 */
public class ResponseTest {

    @Test
    public void json_string_setsFulfillStatusBodyContentType() {
        Response r = Response.json(200, "{\"a\":1}");
        assertEquals(r.kind(), Response.Kind.FULFILL);
        assertEquals(r.status(), 200);
        assertEquals(r.body(), "{\"a\":1}");
        assertEquals(r.contentType(), "application/json");
        assertEquals(r.delayMs(), 0L);
    }

    @Test
    public void json_pojo_serializesViaJackson() {
        Response r = Response.json(201, Map.of("name", "Alex"));
        assertEquals(r.status(), 201);
        assertTrue(r.body().contains("\"name\":\"Alex\""), "body: " + r.body());
        assertEquals(r.contentType(), "application/json");
    }

    @Test
    public void json_pojo_nonSerializable_throwsNamingType() {
        // An object with a self-referential getter that Jackson cannot serialize
        Object bad = new Object() {
            @SuppressWarnings("unused")
            public Object getSelf() { throw new RuntimeException("boom"); }
        };
        try {
            Response.json(200, bad);
            fail("expected NetworkMockException");
        } catch (NetworkMockException e) {
            assertTrue(e.getMessage().contains(bad.getClass().getName()),
                    "message should name the offending type: " + e.getMessage());
        }
    }

    @Test
    public void text_setsPlainContentType() {
        Response r = Response.text(200, "hello");
        assertEquals(r.kind(), Response.Kind.FULFILL);
        assertEquals(r.body(), "hello");
        assertEquals(r.contentType(), "text/plain");
    }

    @Test
    public void status_hasEmptyBody() {
        Response r = Response.status(404);
        assertEquals(r.status(), 404);
        assertEquals(r.body(), "");
    }

    @Test
    public void abort_defaultsToFailed() {
        Response r = Response.abort();
        assertEquals(r.kind(), Response.Kind.ABORT);
        assertEquals(r.abortReason(), AbortReason.FAILED);
    }

    @Test
    public void abort_withReason() {
        Response r = Response.abort(AbortReason.CONNECTION_REFUSED);
        assertEquals(r.kind(), Response.Kind.ABORT);
        assertEquals(r.abortReason(), AbortReason.CONNECTION_REFUSED);
    }

    @Test
    public void passthrough_hasPassthroughKind() {
        assertEquals(Response.passthrough().kind(), Response.Kind.PASSTHROUGH);
    }

    @Test
    public void delay_wrapsDelegateAndSetsDelay() {
        Response delegate = Response.json(200, "[]");
        Response delayed = Response.delay(Duration.ofSeconds(2), delegate);
        assertEquals(delayed.delayMs(), 2000L);
        assertEquals(delayed.kind(), Response.Kind.FULFILL);
        assertEquals(delayed.body(), "[]");
        // original unchanged
        assertEquals(delegate.delayMs(), 0L);
    }

    @Test
    public void delay_nullDuration_isZero() {
        Response delayed = Response.delay(null, Response.status(200));
        assertEquals(delayed.delayMs(), 0L);
    }

    @Test
    public void withHeader_returnsNewImmutableCopy() {
        Response base = Response.json(200, "{}");
        Response withH = base.withHeader("X-Test", "1");
        assertNotSame(base, withH, "withHeader must return a new instance");
        assertTrue(base.headers().isEmpty(), "original headers unchanged");
        assertEquals(withH.headers().get("X-Test"), "1");
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void headers_areUnmodifiable() {
        Response.json(200, "{}").withHeader("A", "b").headers().put("C", "d");
    }

    @Test
    public void withContentType_returnsNewCopy() {
        Response base = Response.json(200, "{}");
        Response xml = base.withContentType("application/xml");
        assertNotSame(base, xml);
        assertEquals(base.contentType(), "application/json");
        assertEquals(xml.contentType(), "application/xml");
    }
}
