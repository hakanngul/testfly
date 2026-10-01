package io.testfly.client;

import io.testfly.api.TestFlyApi;

/** Around middleware. Instances used by parallel calls must be thread-safe. */
@TestFlyApi(since = "1.1.0")
@FunctionalInterface
public interface ApiInterceptor {
    ApiResponse intercept(Chain chain);

    /** Valid only on the interceptor thread during intercept(). */
    @TestFlyApi(since = "1.1.0")
    interface Chain {
        ApiRequest request();
        /** May be called zero or more times sequentially; restarts the downstream chain. */
        ApiResponse proceed(ApiRequest request);
    }
}
