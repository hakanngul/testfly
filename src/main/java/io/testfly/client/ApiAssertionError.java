package io.testfly.client;

import io.testfly.api.TestFlyApi;

/**
 * Assertion failure raised by API contract checks such as
 * {@link ApiResponse#assertSchema(String)} and {@link ApiResponse#assertOpenApi(String)}.
 *
 * <p>Extends {@link AssertionError} so existing {@code catch (AssertionError)} blocks and
 * test-runner failure classification keep working, and implements {@link ApiFailure} so
 * the request context is available just like on {@link ApiException}.
 */
@TestFlyApi(since = "1.1.0")
public class ApiAssertionError extends AssertionError implements ApiFailure {

    private static final long serialVersionUID = 1L;

    private final int status;
    private final String body;
    private final String url;
    private final String method;

    public ApiAssertionError(String method, String url, int status, String body, String message) {
        this(method, url, status, body, message, null);
    }

    public ApiAssertionError(String method, String url, int status, String body, String message, Throwable cause) {
        super(ApiException.describe(message, method, url, status, body), cause);
        this.method = method;
        this.url = url;
        this.status = status;
        this.body = body;
    }

    @Override public int getStatus() { return status; }
    @Override public String getBody() { return body; }
    @Override public String getUrl() { return url; }
    @Override public String getMethod() { return method; }
}
