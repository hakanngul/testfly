package io.testfly.client;

import io.testfly.api.TestFlyApi;

/**
 * Hierarchical exception for API failures — provides structured access to
 * HTTP status, body, URL and method for consistent error handling.
 *
 * <p>Replaces generic {@code RuntimeException} / {@code AssertionError} in
 * {@link ApiClient} and {@link ApiResponse} so callers can distinguish
 * 401 vs 500 vs timeout and the framework can classify flaky vs hard failures.
 */
@TestFlyApi(since = "1.10.0")
public class ApiException extends RuntimeException implements ApiFailure {

    private final int status;
    private final String body;
    private final String url;
    private final String method;

    public ApiException(String method, String url, int status, String body, String message) {
        super(describe(message, method, url, status, body));
        this.method = method;
        this.url = url;
        this.status = status;
        this.body = body;
    }

    public ApiException(String method, String url, Throwable cause) {
        super("[ApiClient] Request failed: " + method + " " + url, cause);
        this.method = method;
        this.url = url;
        this.status = -1;
        this.body = null;
    }

    public ApiException(String method, String url, int status, String body, String message, Throwable cause) {
        super(describe(message, method, url, status, body), cause);
        this.method = method;
        this.url = url;
        this.status = status;
        this.body = body;
    }

    @Override public int getStatus() { return status; }
    @Override public String getBody() { return body; }
    @Override public String getUrl() { return url; }
    @Override public String getMethod() { return method; }

    /** 4xx client error. */
    public boolean isClientError() { return status >= 400 && status < 500; }

    /** 5xx server error. */
    public boolean isServerError() { return status >= 500; }

    /** True if the cause is an HTTP timeout. */
    public boolean isTimeout() {
        return getCause() instanceof java.net.http.HttpTimeoutException
                || (getCause() != null && getCause().getCause() instanceof java.net.http.HttpTimeoutException);
    }

    /** Builds the shared failure message; omits the request part when method and URL are unknown. */
    static String describe(String message, String method, String url, int status, String body) {
        String request = method == null && url == null ? "" : " — " + method + " " + url;
        return message + request + " → " + status + " Body: " + truncate(body, 500);
    }

    private static String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
