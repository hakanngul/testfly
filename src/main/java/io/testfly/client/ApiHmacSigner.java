package io.testfly.client;

import io.testfly.api.TestFlyApi;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * HMAC request signer: signs the final request (method, path and query, timestamp, body hash)
 * and adds key, signature and timestamp headers. Created with {@link ApiAuth#hmacSigner()}.
 *
 * <p>Default canonical string (lines joined with {@code \n}):
 * <pre>
 * METHOD
 * /path?query
 * timestamp            (epoch seconds)
 * hex(SHA-256(body))   (hash of an empty body for GET)
 * </pre>
 *
 * <pre>
 * ApiClient.setGlobalAuth(ApiAuth.hmacSigner()
 *         .apiKey(System.getenv("API_KEY"))
 *         .secret(System.getenv("API_SECRET"))
 *         .signatureHeader("X-Signature")
 *         .encoding(ApiHmacSigner.Encoding.HEX)
 *         .build());
 * </pre>
 *
 * <p>The signer runs as the innermost interceptor. Headers added later by legacy
 * {@link ApiClient.RequestInterceptor} hooks are not part of the signature.
 */
@TestFlyApi(since = "1.1.0")
public final class ApiHmacSigner implements ApiAuth.Intercepting {

    /** Output encoding of the signature. */
    @TestFlyApi(since = "1.1.0")
    public enum Encoding { BASE64, HEX }

    /** Data available to a custom canonical-string function. */
    @TestFlyApi(since = "1.1.0")
    public record SigningContext(String method, URI uri, String timestamp, byte[] body,
                                 Map<String, List<String>> headers) {
        /** Path plus raw query, e.g. {@code /orders?id=1}. */
        public String pathAndQuery() {
            String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
            return uri.getRawQuery() == null ? path : path + "?" + uri.getRawQuery();
        }

        /** Lower-case hex SHA-256 of the body. */
        public String bodySha256() {
            try {
                return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private final String apiKey;
    private final byte[] secret;
    private final String algorithm;
    private final String keyHeader;
    private final String signatureHeader;
    private final String timestampHeader;
    private final Encoding encoding;
    private final Function<SigningContext, String> canonical;
    private final Function<Instant, String> timestampFormat;
    private final Clock clock;

    private ApiHmacSigner(Builder b) {
        this.apiKey = b.apiKey;
        this.secret = Objects.requireNonNull(b.secret, "secret").getBytes(StandardCharsets.UTF_8);
        this.algorithm = b.algorithm;
        this.keyHeader = b.keyHeader;
        this.signatureHeader = Objects.requireNonNull(b.signatureHeader, "signatureHeader");
        this.timestampHeader = b.timestampHeader;
        this.encoding = b.encoding;
        this.canonical = b.canonical;
        this.timestampFormat = b.timestampFormat;
        this.clock = b.clock;
        try {
            Mac.getInstance(algorithm);
        } catch (Exception e) {
            throw new IllegalArgumentException("[ApiHmacSigner] Unsupported HMAC algorithm: " + algorithm, e);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /** The default canonical string: method, path and query, timestamp and body SHA-256, joined by newlines. */
    public static String defaultCanonical(SigningContext ctx) {
        return ctx.method() + "\n" + ctx.pathAndQuery() + "\n" + ctx.timestamp() + "\n" + ctx.bodySha256();
    }

    /** Computes the signature for {@code request} at {@code timestamp}; useful to verify a server implementation. */
    public String sign(ApiRequest request, String timestamp) {
        SigningContext ctx = new SigningContext(request.method(), request.uri(), timestamp, request.body(), request.headers());
        try {
            Mac mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(secret, algorithm));
            byte[] raw = mac.doFinal(canonical.apply(ctx).getBytes(StandardCharsets.UTF_8));
            return encoding == Encoding.HEX ? HexFormat.of().formatHex(raw) : Base64.getEncoder().encodeToString(raw);
        } catch (Exception e) {
            throw new IllegalStateException("[ApiHmacSigner] Signing failed with " + algorithm, e);
        }
    }

    @Override
    public ApiResponse intercept(Chain chain) {
        ApiRequest request = chain.request();
        String timestamp = timestampFormat.apply(clock.instant());
        ApiRequest.Builder signed = request.newBuilder();
        if (keyHeader != null && apiKey != null) signed.header(keyHeader, apiKey);
        if (timestampHeader != null) signed.header(timestampHeader, timestamp);
        // Sign after adding key/timestamp so custom canonical functions can include them.
        ApiRequest withHeaders = signed.build();
        return chain.proceed(withHeaders.newBuilder().header(signatureHeader, sign(withHeaders, timestamp)).build());
    }

    @TestFlyApi(since = "1.1.0")
    public static final class Builder {
        private String apiKey;
        private String secret;
        private String algorithm = "HmacSHA256";
        private String keyHeader = "X-Api-Key";
        private String signatureHeader = "X-Signature";
        private String timestampHeader = "X-Timestamp";
        private Encoding encoding = Encoding.BASE64;
        private Function<SigningContext, String> canonical = ApiHmacSigner::defaultCanonical;
        private Function<Instant, String> timestampFormat = instant -> String.valueOf(instant.getEpochSecond());
        private Clock clock = Clock.systemUTC();

        private Builder() {
        }

        /** API key sent in {@link #keyHeader(String)}; optional. */
        public Builder apiKey(String value) { apiKey = value; return this; }
        /** Shared secret (required). */
        public Builder secret(String value) { secret = Objects.requireNonNull(value, "secret"); return this; }
        /** JCA MAC algorithm, default {@code HmacSHA256}. */
        public Builder algorithm(String value) { algorithm = Objects.requireNonNull(value, "algorithm"); return this; }
        /** Header carrying the API key, default {@code X-Api-Key}; {@code null} omits it. */
        public Builder keyHeader(String value) { keyHeader = value; return this; }
        /** Header carrying the signature, default {@code X-Signature}. */
        public Builder signatureHeader(String value) { signatureHeader = Objects.requireNonNull(value); return this; }
        /** Header carrying the timestamp, default {@code X-Timestamp}; {@code null} omits it. */
        public Builder timestampHeader(String value) { timestampHeader = value; return this; }
        /** Signature encoding, default {@link Encoding#BASE64}. */
        public Builder encoding(Encoding value) { encoding = Objects.requireNonNull(value); return this; }
        /** Custom canonical string; default {@link ApiHmacSigner#defaultCanonical(SigningContext)}. */
        public Builder canonical(Function<SigningContext, String> value) { canonical = Objects.requireNonNull(value); return this; }
        /** Timestamp format, default epoch seconds. */
        public Builder timestampFormat(Function<Instant, String> value) { timestampFormat = Objects.requireNonNull(value); return this; }
        /** Clock used for timestamps (tests). */
        public Builder clock(Clock value) { clock = Objects.requireNonNull(value); return this; }
        public ApiHmacSigner build() { return new ApiHmacSigner(this); }
    }
}
