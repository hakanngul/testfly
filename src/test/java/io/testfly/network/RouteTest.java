package io.testfly.network;

import org.openqa.selenium.devtools.v152.fetch.model.RequestId;
import org.testng.annotations.Test;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

/**
 * Unit tests for {@link Route}. The owning {@link NetworkMock} is mocked so we
 * can verify delegation of terminal actions without a real CDP session.
 */
public class RouteTest {

    private Route newRoute(NetworkMock owner) {
        Route r = new Route(owner, "REQ-1", "https://h/api/me", "GET",
                Map.of("authorization", "Bearer x"), "{\"q\":1}");
        r.bindResponseContext(200, Map.of("content-type", "application/json"));
        return r;
    }

    @Test
    public void accessors_returnRequestData() {
        Route r = newRoute(mock(NetworkMock.class));
        assertEquals(r.url(), "https://h/api/me");
        assertEquals(r.method(), "GET");
        assertEquals(r.headers().get("authorization"), "Bearer x");
        assertEquals(r.body(), "{\"q\":1}");
    }

    @Test
    public void fulfill_delegatesToOwnerFulfill() {
        NetworkMock owner = mock(NetworkMock.class);
        Route r = newRoute(owner);
        r.fulfill(Response.json(200, "{\"ok\":true}"));
        verify(owner).fulfill(any(RequestId.class), any(Response.class));
    }

    @Test
    public void abort_delegatesToOwnerFailRequest() {
        NetworkMock owner = mock(NetworkMock.class);
        Route r = newRoute(owner);
        r.abort(AbortReason.CONNECTION_REFUSED);
        verify(owner).failRequest(any(RequestId.class), any(AbortReason.class));
    }

    @Test
    public void passthrough_delegatesToOwnerContinueResponse() {
        NetworkMock owner = mock(NetworkMock.class);
        Route r = newRoute(owner);
        r.passthrough();
        verify(owner).continueResponse(any(RequestId.class));
    }

    @Test
    public void doubleTerminate_isIgnored() {
        NetworkMock owner = mock(NetworkMock.class);
        Route r = newRoute(owner);
        r.passthrough();
        r.fulfill(Response.json(200, "{}"));   // second terminal — ignored
        r.abort();                              // third — ignored
        verify(owner).continueResponse(any(RequestId.class));
        verify(owner, never()).fulfill(any(RequestId.class), any(Response.class));
        verify(owner, never()).failRequest(any(RequestId.class), any(AbortReason.class));
    }

    @Test
    public void fulfillAbortResponse_delegatesToFailRequest() {
        NetworkMock owner = mock(NetworkMock.class);
        Route r = newRoute(owner);
        r.fulfill(Response.abort(AbortReason.TIMED_OUT));
        verify(owner).failRequest(any(RequestId.class), any(AbortReason.class));
        verify(owner, never()).fulfill(any(RequestId.class), any(Response.class));
    }

    @Test
    public void fetchOriginal_delegatesToOwner() {
        NetworkMock owner = mock(NetworkMock.class);
        OriginalResponse expected = new OriginalResponse(200, Map.of(), "{\"x\":1}");
        when(owner.fetchResponseBody(any(RequestId.class), org.mockito.ArgumentMatchers.anyInt(),
                any())).thenReturn(expected);
        Route r = newRoute(owner);
        OriginalResponse got = r.fetchOriginal();
        assertEquals(got, expected);
        verify(owner, times(1)).fetchResponseBody(any(RequestId.class),
                org.mockito.ArgumentMatchers.anyInt(), any());
    }
}
