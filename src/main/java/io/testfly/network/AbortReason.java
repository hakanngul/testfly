package io.testfly.network;

import io.testfly.api.TestFlyApi;
import org.openqa.selenium.devtools.v152.network.model.ErrorReason;

/**
 * Reasons a mocked route can abort a request, mapped to the underlying
 * Chrome DevTools Protocol {@link ErrorReason}.
 *
 * <p>Used with {@link Response#abort(AbortReason)} to simulate offline modes,
 * connection loss, timeouts, and client-side blocking.
 *
 * <pre>
 * mockRoute("**&#47;api/checkout", Response.abort(AbortReason.CONNECTION_REFUSED));
 * </pre>
 */
@TestFlyApi(since = "1.6.0")
public enum AbortReason {

    /** Generic failure — the CDP default. */
    FAILED,
    /** Request aborted. */
    ABORTED,
    /** Request timed out. */
    TIMED_OUT,
    /** Access to the resource was denied. */
    ACCESS_DENIED,
    /** The connection was refused. */
    CONNECTION_REFUSED,
    /** The connection failed. */
    CONNECTION_FAILED,
    /** The host name could not be resolved. */
    NAME_NOT_RESOLVED,
    /** The internet connection is unavailable. */
    INTERNET_DISCONNECTED,
    /** Blocked by the client (used by the global URL blocklist). */
    BLOCKED_BY_CLIENT;

    /** Maps this reason to the CDP {@link ErrorReason}. Package-private. */
    ErrorReason toCdp() {
        switch (this) {
            case ABORTED:               return ErrorReason.ABORTED;
            case TIMED_OUT:             return ErrorReason.TIMEDOUT;
            case ACCESS_DENIED:         return ErrorReason.ACCESSDENIED;
            case CONNECTION_REFUSED:    return ErrorReason.CONNECTIONREFUSED;
            case CONNECTION_FAILED:     return ErrorReason.CONNECTIONFAILED;
            case NAME_NOT_RESOLVED:     return ErrorReason.NAMENOTRESOLVED;
            case INTERNET_DISCONNECTED: return ErrorReason.INTERNETDISCONNECTED;
            case BLOCKED_BY_CLIENT:     return ErrorReason.BLOCKEDBYCLIENT;
            case FAILED:
            default:                    return ErrorReason.FAILED;
        }
    }
}
