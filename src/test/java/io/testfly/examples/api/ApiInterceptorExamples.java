package io.testfly.examples.api;

import io.testfly.client.*;
import io.testfly.steps.StepLogger;
import java.net.URI;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Compile-checked examples shared with the API testing guide. */
public final class ApiInterceptorExamples {
    private ApiInterceptorExamples() {
    }

    public static final class LoggingInterceptor implements ApiInterceptor {
        @Override
        public ApiResponse intercept(Chain chain) {
            StepLogger.step("API start: " + chain.request().method() + " " + chain.request().uri());
            try {
                return chain.proceed(chain.request());
            } finally {
                StepLogger.step("API end");
            }
        }
    }

    public static final class AuthRefreshInterceptor implements ApiInterceptor {
        private final URI tokenEndpoint;
        private final Supplier<String> refreshToken;

        public AuthRefreshInterceptor(URI tokenEndpoint, Supplier<String> refreshToken) {
            this.tokenEndpoint = Objects.requireNonNull(tokenEndpoint);
            this.refreshToken = Objects.requireNonNull(refreshToken);
        }

        @Override
        public ApiResponse intercept(Chain chain) {
            ApiRequest request = chain.request();
            ApiResponse response = chain.proceed(request);
            // Token calls are excluded; the second response is returned without another
            // refresh.
            if (response.status() != 401 || request.uri().equals(tokenEndpoint))
                return response;
            String token = Objects.requireNonNull(refreshToken.get(), "refreshed token");
            if (token.isBlank())
                throw new IllegalStateException("Empty refreshed token");
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
