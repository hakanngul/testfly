---
title: "Interceptor Chain"
---

# Interceptor Chain

`ApiInterceptor` can change a prepared request, inspect or replace a response, retry downstream middleware, or return a synthetic response without using the network. The same blocking contract runs in `send()` and, on a managed virtual thread, in `sendAsync()`.

```java
ApiClient.addInterceptor(chain -> chain.proceed(
    chain.request().newBuilder().header("X-Tenant", "test").build()));

ApiClient.get("https://api.example.com/users")
    .interceptor(chain -> chain.proceed(chain.request()))
    .send();

ApiClient.clearChainInterceptors();
```

`addInterceptor()` registers on the current test thread; `.interceptor()` registers on the request instance. Test-scoped middleware runs first, then request middleware, in registration order. Framework cleanup removes test-scoped registrations, auth and cookies, and cancels unfinished API futures. It never removes another test's middleware. `clearInterceptors()` still clears the legacy global hooks and also clears the calling thread's chain registrations.

### Execution contract

- Each `proceed(request)` runs the next middleware; repeated sequential calls restart the same downstream portion. It does not restart the current interceptor.
- A chain is valid only on its owner thread while `intercept()` is executing. Using a saved chain later or from another thread throws `IllegalStateException`. Null requests/responses are rejected.
- Requests contain a resolved URI, method, timeout, multi-value headers and defensively copied body bytes. `header()` replaces values case-insensitively; `addHeader()` appends; `removeHeader()` removes. JSON, form and multipart bodies are serialized once per logical call and replayed unchanged.
- Middleware may return any valid HTTP response, including 401/503. Assertions remain explicit test code. `ApiResponse.builder()` requires a request and status (100–599); body defaults to an empty string and duration to zero. `newBuilder()` retains the original data for response rewriting.
- YAML API retry wraps the entire chain. Each automatic attempt reruns middleware and authentication from the original snapshot. `maxAttempts` counts outer attempts; additional `proceed()` calls can produce more HTTP exchanges. Retry is disabled by default.
- Only transport I/O failures and configured HTTP statuses trigger automatic retry. Interceptor/hook errors and assertion failures do not. Interrupts/cancellation terminate the call and backoff.
- Legacy request hooks run immediately before every real send and do not run for a synthetic response. Their original method/body assignment behavior is retained. Legacy response hooks see one final chain response per outer attempt, including synthetic responses; intermediate responses inside a refresh interceptor are observed by the new middleware.
- Redirects remain managed by JDK HttpClient. Middleware is not called separately for every redirect hop.

### Compiled examples

The following examples are compiled with the framework test sources. The refresh example performs at most one extra send per interceptor invocation, excludes the token endpoint, and leaves a second 401 visible. Register it on the protected request. Its token supplier should call the token endpoint without registering this request-scoped refresh interceptor. This example does not invalidate TestFly's OAuth2 cache or coordinate concurrent refreshes; use a thread-safe supplier when calls are parallel.

```java
package io.testfly.examples.api;

import io.testfly.client.*;
import io.testfly.steps.StepLogger;
import java.net.URI;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Compile-checked examples shared with the API testing guide. */
public final class ApiInterceptorExamples {
    private ApiInterceptorExamples() {}

    public static final class LoggingInterceptor implements ApiInterceptor {
        @Override public ApiResponse intercept(Chain chain) {
            StepLogger.step("API start: " + chain.request().method() + " " + chain.request().uri());
            try { return chain.proceed(chain.request()); }
            finally { StepLogger.step("API end"); }
        }
    }

    public static final class AuthRefreshInterceptor implements ApiInterceptor {
        private final URI tokenEndpoint;
        private final Supplier<String> refreshToken;
        public AuthRefreshInterceptor(URI tokenEndpoint, Supplier<String> refreshToken) {
            this.tokenEndpoint = Objects.requireNonNull(tokenEndpoint);
            this.refreshToken = Objects.requireNonNull(refreshToken);
        }
        @Override public ApiResponse intercept(Chain chain) {
            ApiRequest request = chain.request();
            ApiResponse response = chain.proceed(request);
            // Token calls are excluded; the second response is returned without another refresh.
            if (response.status() != 401 || request.uri().equals(tokenEndpoint)) return response;
            String token = Objects.requireNonNull(refreshToken.get(), "refreshed token");
            if (token.isBlank()) throw new IllegalStateException("Empty refreshed token");
            return chain.proceed(request.newBuilder().header("Authorization", "Bearer " + token).build());
        }
    }

    public static ApiInterceptor paymentMock() {
        return chain -> {
            ApiRequest request = chain.request();
            if (request.method().equals("POST") && request.uri().getPath().equals("/payment")) {
                return ApiResponse.builder().request(request).status(200)
                        .header("Content-Type", "application/json")
                        .body("{\"status\":\"mocked\"}").build();
            }
            return chain.proceed(request);
        };
    }

    public static ApiResponse syncPayment(String baseUrl) {
        return ApiClient.post(baseUrl + "/payment")
                .interceptor(new LoggingInterceptor()).interceptor(paymentMock()).send();
    }

    public static CompletableFuture<ApiResponse> asyncPayment(String baseUrl) {
        return ApiClient.post(baseUrl + "/payment")
                .interceptor(new LoggingInterceptor()).interceptor(paymentMock()).sendAsync();
    }

    public static ApiResponse protectedRequest(String baseUrl, String expiredToken, Supplier<String> tokenProvider) {
        return ApiClient.get(baseUrl + "/protected").auth(ApiAuth.bearerToken(expiredToken))
                .interceptor(new AuthRefreshInterceptor(URI.create(baseUrl + "/token"), tokenProvider)).send();
    }

    public static ApiResponse negativeAuthTest(String baseUrl, String invalidToken) {
        // No refresh interceptor: a genuine 401 remains available for the assertion.
        return ApiClient.get(baseUrl + "/protected").auth(ApiAuth.bearerToken(invalidToken)).send().assertStatus(401);
    }
}
```

See [Async Calls & Batch](api-async-batch.md) for context and cancellation, and [Logging & Reporting](api-reporting.md) for output rules.
