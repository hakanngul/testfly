package io.testfly.client;

import io.testfly.api.TestFlyApi;

/**
 * Reusable response expectations — DRY for status, content-type, duration, schema.
 *
 * <pre>
 * ApiResponseSpec ok = ApiResponseSpec.expect().status(200).timeLessThan(500).build();
 * apiClient().get("/users/1").send().expect(ok);
 * </pre>
 */
@TestFlyApi(since = "1.10.0")
public final class ApiResponseSpec {

    private final Integer expectedStatus;
    private final String expectedContentType;
    private final Long maxDurationMs;
    private final String schemaPath;

    private ApiResponseSpec(Builder b) {
        this.expectedStatus = b.expectedStatus;
        this.expectedContentType = b.expectedContentType;
        this.maxDurationMs = b.maxDurationMs;
        this.schemaPath = b.schemaPath;
    }

    public void validate(ApiResponse res) {
        if (expectedStatus != null) res.assertStatus(expectedStatus);
        if (expectedContentType != null) {
            String actual = res.header("Content-Type");
            if (actual == null || !actual.contains(expectedContentType)) {
                throw new ApiException(null, null, res.status(), res.body(),
                        "[ApiResponseSpec] Expected Content-Type to contain '" + expectedContentType + "' but got '" + actual + "'");
            }
        }
        if (maxDurationMs != null) res.assertDurationLessThan(maxDurationMs);
        if (schemaPath != null) res.assertSchema(schemaPath);
    }

    public static Builder expect() { return new Builder(); }

    public static final class Builder {
        private Integer expectedStatus;
        private String expectedContentType;
        private Long maxDurationMs;
        private String schemaPath;

        public Builder status(int v) { expectedStatus = v; return this; }
        public Builder contentType(String v) { expectedContentType = v; return this; }
        public Builder timeLessThan(long ms) { maxDurationMs = ms; return this; }
        public Builder schema(String path) { schemaPath = path; return this; }
        public ApiResponseSpec build() { return new ApiResponseSpec(this); }
    }
}
