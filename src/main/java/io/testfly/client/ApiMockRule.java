package io.testfly.client;

import io.testfly.api.TestFlyApi;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/** Immutable client-side mock rule. Predicates and factories must be thread-safe. */
@TestFlyApi(since = "1.1.0")
public final class ApiMockRule {
    private final Predicate<ApiRequest> matcher;
    private final Function<ApiRequest, ApiResponse> factory;
    private ApiMockRule(Builder builder) { matcher = Objects.requireNonNull(builder.matcher, "matcher"); factory = Objects.requireNonNull(builder.factory, "response factory"); }
    public static Builder builder() { return new Builder(); }
    public boolean matches(ApiRequest request) { return matcher.test(Objects.requireNonNull(request)); }
    public ApiResponse respond(ApiRequest request) {
        ApiResponse response = Objects.requireNonNull(factory.apply(request), "mock response");
        return response.asSynthetic(request);
    }
    @TestFlyApi(since = "1.1.0")
    public static final class Builder {
        private Predicate<ApiRequest> matcher;
        private Function<ApiRequest, ApiResponse> factory;
        private Builder() {}
        public Builder match(Predicate<ApiRequest> value) { matcher = Objects.requireNonNull(value); return this; }
        public Builder respond(Function<ApiRequest, ApiResponse> value) { factory = Objects.requireNonNull(value); return this; }
        public ApiMockRule build() { return new ApiMockRule(this); }
    }
}
