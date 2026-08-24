package io.testfly.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.testfly.api.TestFlyApi;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Auth strategies for {@link ApiClient}.
 *
 * <pre>
 * // Per-request
 * ApiClient.get("/api/me").auth(ApiAuth.bearerToken("token")).send();
 *
 * // Suite-wide (apply once in @BeforeSuite, all requests use it automatically)
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
    default void applyToClient(io.testfly.client.ApiClient client) {}

    // ── Static factories ──────────────────────────────────────────────────────

    /** Sets {@code Authorization: Bearer <token>}. */
    public static ApiAuth bearerToken(String token) {
        return builder -> builder.header("Authorization", "Bearer " + token);
    }

    /** Sets {@code Authorization: Basic <base64(user:pass)>}. */
    public static ApiAuth basicAuth(String username, String password) {
        return builder -> {
            String encoded = Base64.getEncoder()
                    .encodeToString((username + ":" + password).getBytes());
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
            @Override public void apply(HttpRequest.Builder builder) {}
            @Override public void applyToClient(io.testfly.client.ApiClient client) {
                client.queryParam(paramName, apiKey);
            }
        };
    }

    /** Simple Digest placeholder — sends Basic-like header with Digest prefix. For full RFC 2617 use a custom interceptor. */
    public static ApiAuth digest(String username, String password) {
        return builder -> {
            String encoded = Base64.getEncoder()
                    .encodeToString((username + ":" + password).getBytes());
            builder.header("Authorization", "Digest " + encoded);
        };
    }

    /** HMAC signature — adds {@code X-Api-Key} and {@code X-Signature} headers. */
    public static ApiAuth hmac(String apiKey, String secret, String algorithm) {
        String algo = algorithm != null ? algorithm : "HmacSHA256";
        return builder -> {
            try {
                javax.crypto.Mac mac = javax.crypto.Mac.getInstance(algo);
                mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), algo));
                String sig = Base64.getEncoder().encodeToString(mac.doFinal(apiKey.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
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
     * Token is fetched on first use and cached per {@code tokenUrl + clientId} until expiry.
     *
     * <pre>
     * ApiClient.setGlobalAuth(ApiAuth.oauth2(
     *     "https://auth.example.com/token",
     *     System.getenv("CLIENT_ID"),
     *     System.getenv("CLIENT_SECRET")
     * ));
     * </pre>
     */
    public static ApiAuth oauth2(String tokenUrl, String clientId, String clientSecret) {
        return builder -> {
            String token = OAuth2TokenCache.getToken(tokenUrl, clientId, clientSecret);
            builder.header("Authorization", "Bearer " + token);
        };
    }

    // ── OAuth2 token cache ────────────────────────────────────────────────────

    static final class OAuth2TokenCache {

        private static final ObjectMapper MAPPER = new ObjectMapper();
        private static final HttpClient   HTTP   = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15)).build();

        private static final ConcurrentHashMap<String, CachedToken> CACHE = new ConcurrentHashMap<>();

        static String getToken(String tokenUrl, String clientId, String clientSecret) {
            String key = tokenUrl + "|" + clientId;
            CachedToken cached = CACHE.get(key);
            if (cached != null && !cached.isExpired()) return cached.token;

            // Synchronized to prevent thundering herd — only one thread fetches,
            // others wait and use the cached result (double-checked locking).
            synchronized (CACHE) {
                cached = CACHE.get(key);
                if (cached != null && !cached.isExpired()) return cached.token;
                CachedToken fresh = fetchToken(tokenUrl, clientId, clientSecret);
                CACHE.put(key, fresh);
                return fresh.token;
            }
        }

        static String getPasswordToken(String tokenUrl, String clientId, String clientSecret,
                                         String username, String password) {
            String key = tokenUrl + "|" + clientId + "|" + username;
            CachedToken cached = CACHE.get(key);
            if (cached != null && !cached.isExpired()) return cached.token;
            synchronized (CACHE) {
                cached = CACHE.get(key);
                if (cached != null && !cached.isExpired()) return cached.token;
                CachedToken fresh = fetchPasswordToken(tokenUrl, clientId, clientSecret, username, password);
                CACHE.put(key, fresh);
                return fresh.token;
            }
        }

        private static CachedToken fetchPasswordToken(String tokenUrl, String clientId, String clientSecret,
                                                      String username, String password) {
            try {
                String form = "grant_type=password"
                            + "&client_id=" + clientId
                            + "&client_secret=" + clientSecret
                            + "&username=" + username
                            + "&password=" + password;
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(tokenUrl))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(form))
                        .timeout(Duration.ofSeconds(15))
                        .build();
                HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() != 200) {
                    throw new RuntimeException("[ApiAuth] OAuth2 password token request failed: HTTP "
                            + res.statusCode() + " — " + res.body());
                }
                JsonNode json      = MAPPER.readTree(res.body());
                String   token     = json.path("access_token").asText();
                int      expiresIn = json.path("expires_in").asInt(3600);
                return new CachedToken(token, Instant.now().plusSeconds(expiresIn - 60));
            } catch (RuntimeException e) { throw e; }
            catch (Exception e) { throw new RuntimeException("[ApiAuth] Failed to fetch OAuth2 password token from: " + tokenUrl, e); }
        }

        private static CachedToken fetchToken(String tokenUrl, String clientId, String clientSecret) {
            try {
                String form = "grant_type=client_credentials"
                            + "&client_id=" + clientId
                            + "&client_secret=" + clientSecret;

                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(tokenUrl))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(form))
                        .timeout(Duration.ofSeconds(15))
                        .build();

                HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() != 200) {
                    throw new RuntimeException("[ApiAuth] OAuth2 token request failed: HTTP "
                            + res.statusCode() + " — " + res.body());
                }

                JsonNode json       = MAPPER.readTree(res.body());
                String   token      = json.path("access_token").asText();
                int      expiresIn  = json.path("expires_in").asInt(3600);

                return new CachedToken(token, Instant.now().plusSeconds(expiresIn - 60));

            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException("[ApiAuth] Failed to fetch OAuth2 token from: " + tokenUrl, e);
            }
        }

        /** Clears cached tokens — useful in tests or after suite. */
        static void clearCache() {
            CACHE.clear();
        }

        private static final class CachedToken {
            final String  token;
            final Instant expiresAt;

            CachedToken(String token, Instant expiresAt) {
                this.token     = token;
                this.expiresAt = expiresAt;
            }

            boolean isExpired() {
                return Instant.now().isAfter(expiresAt);
            }
        }
    }
}
