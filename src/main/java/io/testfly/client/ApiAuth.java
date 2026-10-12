package io.testfly.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.testfly.api.TestFlyApi;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Auth strategies for {@link ApiClient}.
 *
 * <pre>
 * // Per-request
 * ApiClient.get("/api/me").auth(ApiAuth.bearerToken("token")).send();
 *
 * // Per test thread (set in @BeforeMethod; cleared automatically after each test)
 * ApiClient.setGlobalAuth(ApiAuth.bearerToken(loginResponse.json("$.token")));
 *
 * // OAuth2 client credentials (token fetched + cached automatically)
 * ApiClient.setGlobalAuth(ApiAuth.oauth2(tokenUrl, clientId, clientSecret));
 * </pre>
 */
@TestFlyApi(since = "1.0.0")
@FunctionalInterface
public interface ApiAuth {

    void apply(HttpRequest.Builder builder);

    /** Hook to modify ApiClient before URL is built (e.g. add query param). */
    default void applyToClient(io.testfly.client.ApiClient client) {
    }

    /**
     * Auth strategy that needs the final request or the response, such as challenge/response
     * (Digest) or request signing (HMAC). {@link ApiClient} installs it as the innermost
     * {@link ApiInterceptor}, right before the transport, so it sees the exact request that is
     * sent and may call {@link ApiInterceptor.Chain#proceed(ApiRequest)} more than once.
     * {@link #apply(HttpRequest.Builder)} is a no-op for these strategies.
     *
     * <p>Implementations must be thread-safe: one instance may serve parallel calls.
     */
    @TestFlyApi(since = "1.1.0")
    interface Intercepting extends ApiAuth, ApiInterceptor {
        @Override
        default void apply(HttpRequest.Builder builder) {
        }
    }

    // ── Static factories ──────────────────────────────────────────────────────

    /** Sets {@code Authorization: Bearer <token>}. */
    public static ApiAuth bearerToken(String token) {
        return builder -> builder.header("Authorization", "Bearer " + token);
    }

    /** Sets {@code Authorization: Basic <base64(user:pass)>}. */
    public static ApiAuth basicAuth(String username, String password) {
        return builder -> {
            String encoded = Base64.getEncoder()
                    .encodeToString((username + ":" + password).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            builder.header("Authorization", "Basic " + encoded);
        };
    }

    /** Sets {@code X-Api-Key} or custom header to {@code apiKey}. */
    public static ApiAuth apiKey(String headerName, String apiKey) {
        String h = headerName != null ? headerName : "X-Api-Key";
        return builder -> builder.header(h, apiKey);
    }

    /** Adds API key as query parameter — applied before URL is built. */
    public static ApiAuth apiKeyQuery(String paramName, String apiKey) {
        return new ApiAuth() {
            @Override
            public void apply(HttpRequest.Builder builder) {
            }

            @Override
            public void applyToClient(io.testfly.client.ApiClient client) {
                client.queryParam(paramName, apiKey);
            }
        };
    }

    /**
     * HTTP Digest authentication (RFC 7616): algorithms MD5, MD5-sess, SHA-256, SHA-256-sess,
     * SHA-512-256(-sess); qop {@code auth} and {@code auth-int}.
     *
     * <p>The first request to an origin is sent without credentials; on a
     * {@code 401 WWW-Authenticate: Digest} challenge it is re-sent once with an
     * {@code Authorization} header. The challenge is then cached per origin for the current
     * test (shared with its {@code sendAsync} calls), so later requests authenticate
     * preemptively with an increasing nonce count. A stale or rotated nonce triggers one
     * re-authentication. Rejected credentials leave the final 401 response to the caller.
     */
    public static ApiAuth digest(String username, String password) {
        return new DigestAuth(username, password);
    }

    /**
     * Returns a builder for an HMAC request signer that signs method, path, timestamp and body.
     *
     * <pre>
     * ApiAuth signer = ApiAuth.hmacSigner()
     *         .apiKey(System.getenv("API_KEY"))
     *         .secret(System.getenv("API_SECRET"))
     *         .build();
     * </pre>
     *
     * @see ApiHmacSigner
     */
    @TestFlyApi(since = "1.1.0")
    public static ApiHmacSigner.Builder hmacSigner() {
        return ApiHmacSigner.builder();
    }

    /**
     * HMAC signature of the API key only — adds {@code X-Api-Key} and {@code X-Signature} headers.
     *
     * @deprecated the signature covers only the API key, never the request, so it does not
     *             match servers that verify request signatures. Use {@link #hmacSigner()}.
     *             The wire format of this method is kept unchanged for compatibility.
     */
    @Deprecated(since = "1.1.0")
    public static ApiAuth hmac(String apiKey, String secret, String algorithm) {
        String algo = algorithm != null ? algorithm : "HmacSHA256";
        return builder -> {
            try {
                javax.crypto.Mac mac = javax.crypto.Mac.getInstance(algo);
                mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        algo));
                String sig = Base64.getEncoder()
                        .encodeToString(mac.doFinal(apiKey.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                builder.header("X-Api-Key", apiKey);
                builder.header("X-Signature", sig);
            } catch (Exception e) {
                throw new RuntimeException("[ApiAuth] HMAC failed with algorithm " + algo, e);
            }
        };
    }

    /**
     * OAuth2 Resource Owner Password Credentials grant.
     * Fetches token via {@code grant_type=password} and caches it.
     */
    public static ApiAuth oauth2Password(String tokenUrl, String clientId, String clientSecret,
            String username, String password) {
        return builder -> {
            String token = OAuth2TokenCache.getPasswordToken(tokenUrl, clientId, clientSecret, username, password);
            builder.header("Authorization", "Bearer " + token);
        };
    }

    /**
     * OAuth2 client credentials flow.
     * Token is fetched on first use and cached per {@code tokenUrl + clientId}
     * until expiry.
     *
     * <pre>
     * ApiClient.setGlobalAuth(ApiAuth.oauth2(
     *         "https://auth.example.com/token",
     *         System.getenv("CLIENT_ID"),
     *         System.getenv("CLIENT_SECRET")));
     * </pre>
     */
    public static ApiAuth oauth2(String tokenUrl, String clientId, String clientSecret) {
        return builder -> {
            String token = OAuth2TokenCache.getToken(tokenUrl, clientId, clientSecret);
            builder.header("Authorization", "Bearer " + token);
        };
    }

    /**
     * OAuth2 refresh-token grant: exchanges {@code refreshToken} for an access token and caches
     * it until expiry. A rotated refresh token returned by the server is used for the next refresh.
     */
    @TestFlyApi(since = "1.1.0")
    public static ApiAuth oauth2Refresh(String tokenUrl, String clientId, String clientSecret, String refreshToken) {
        java.util.Objects.requireNonNull(refreshToken, "refreshToken");
        return builder -> {
            String token = OAuth2TokenCache.getRefreshedToken(tokenUrl, clientId, clientSecret, refreshToken);
            builder.header("Authorization", "Bearer " + token);
        };
    }

    /**
     * Clears every cached OAuth2 token. The framework also calls this at suite end, so tokens
     * are never reused across suites in the same JVM.
     */
    @TestFlyApi(since = "1.1.0")
    public static void clearTokenCache() {
        OAuth2TokenCache.clearCache();
    }

    // ── OAuth2 token cache ────────────────────────────────────────────────────

    /**
     * Process-wide OAuth2 token cache. Tokens are refreshed with the refresh-token grant when the
     * server issued one, otherwise re-fetched with the original grant. Cleared at suite end.
     */
    static final class OAuth2TokenCache {

        private static final ObjectMapper MAPPER = new ObjectMapper();
        private static final HttpClient HTTP = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15)).build();

        private static final ConcurrentHashMap<String, CachedToken> CACHE = new ConcurrentHashMap<>();

        private OAuth2TokenCache() {
        }

        static String getToken(String tokenUrl, String clientId, String clientSecret) {
            return obtain(tokenUrl + "|" + clientId, tokenUrl, clientId, clientSecret,
                    () -> fetch(tokenUrl, "OAuth2 token",
                            "grant_type=client_credentials" + param("client_id", clientId) + param("client_secret", clientSecret)));
        }

        static String getPasswordToken(String tokenUrl, String clientId, String clientSecret,
                String username, String password) {
            return obtain(tokenUrl + "|" + clientId + "|" + username, tokenUrl, clientId, clientSecret,
                    () -> fetch(tokenUrl, "OAuth2 password token",
                            "grant_type=password" + param("client_id", clientId) + param("client_secret", clientSecret)
                                    + param("username", username) + param("password", password)));
        }

        static String getRefreshedToken(String tokenUrl, String clientId, String clientSecret, String refreshToken) {
            String key = tokenUrl + "|" + clientId + "|refresh|" + Integer.toHexString(refreshToken.hashCode());
            return obtain(key, tokenUrl, clientId, clientSecret,
                    () -> refresh(tokenUrl, clientId, clientSecret, refreshToken));
        }

        /** Double-checked: only one thread fetches, others reuse the cached result. */
        private static String obtain(String key, String tokenUrl, String clientId, String clientSecret,
                java.util.function.Supplier<CachedToken> grant) {
            CachedToken cached = CACHE.get(key);
            if (cached != null && !cached.isExpired())
                return cached.token;
            synchronized (CACHE) {
                cached = CACHE.get(key);
                if (cached != null && !cached.isExpired())
                    return cached.token;
                CachedToken fresh = null;
                if (cached != null && cached.refreshToken != null) {
                    try {
                        fresh = refresh(tokenUrl, clientId, clientSecret, cached.refreshToken);
                    } catch (RuntimeException refreshFailed) {
                        fresh = null; // fall back to the original grant
                    }
                }
                if (fresh == null)
                    fresh = grant.get();
                CACHE.put(key, fresh);
                return fresh.token;
            }
        }

        private static CachedToken refresh(String tokenUrl, String clientId, String clientSecret, String refreshToken) {
            CachedToken token = fetch(tokenUrl, "OAuth2 refresh token",
                    "grant_type=refresh_token" + param("refresh_token", refreshToken)
                            + param("client_id", clientId) + param("client_secret", clientSecret));
            // Servers without rotation omit refresh_token: keep using the current one.
            return token.refreshToken != null ? token : new CachedToken(token.token, token.expiresAt, refreshToken);
        }

        private static String param(String name, String value) {
            if (value == null) return "";
            return "&" + name + "=" + URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
        }

        private static CachedToken fetch(String tokenUrl, String label, String form) {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(tokenUrl))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("Accept", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(form))
                        .timeout(Duration.ofSeconds(15))
                        .build();
                HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() != 200) {
                    throw new RuntimeException("[ApiAuth] " + label + " request failed: HTTP "
                            + res.statusCode() + " — " + res.body());
                }
                JsonNode json = MAPPER.readTree(res.body());
                String token = json.path("access_token").asText(null);
                if (token == null || token.isEmpty()) {
                    throw new RuntimeException("[ApiAuth] " + label + " response has no access_token: " + res.body());
                }
                int expiresIn = json.path("expires_in").asInt(3600);
                String refresh = json.path("refresh_token").asText(null);
                return new CachedToken(token, Instant.now().plusSeconds(expiresIn - 60L), refresh);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("[ApiAuth] Interrupted while fetching " + label + " from: " + tokenUrl, e);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException("[ApiAuth] Failed to fetch " + label + " from: " + tokenUrl, e);
            }
        }

        /** Clears cached tokens — useful in tests or after suite. */
        static void clearCache() {
            CACHE.clear();
        }

        private static final class CachedToken {
            final String token;
            final Instant expiresAt;
            final String refreshToken;

            CachedToken(String token, Instant expiresAt, String refreshToken) {
                this.token = token;
                this.expiresAt = expiresAt;
                this.refreshToken = refreshToken;
            }

            boolean isExpired() {
                return Instant.now().isAfter(expiresAt);
            }
        }
    }
}
