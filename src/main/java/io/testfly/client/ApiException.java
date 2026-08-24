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
public class ApiException extends RuntimeException {

    private final int status;
    private final String body;
    private final String url;
    private final String method;

    public ApiException(String method, String url, int status, String body, String message) {
        super(message + " — " + method + " " + url + " → " + status + " Body: " + truncate(body, 500));
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
        super(message + " — " + method + " " + url + " → " + status + " Body: " + truncate(body, 500), cause);
        this.method = method;
        this.url = url;
        this.status = status;
        this.body = body;
    }

    public int getStatus() { return status; }
    public String getBody() { return body; }
    public String getUrl() { return url; }
    public String getMethod() { return method; }

    /** 4xx client error. */
    public boolean isClientError() { return status >= 400 && status < 500; }

    /** 5xx server error. */
    public boolean isServerError() { return status >= 500; }

    /** True if the cause is an HTTP timeout. */
    public boolean isTimeout() {
        return getCause() instanceof java.net.http.HttpTimeoutException
                || (getCause() != null && getCause().getCause() instanceof java.net.http.HttpTimeoutException);
    }

    private static String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
