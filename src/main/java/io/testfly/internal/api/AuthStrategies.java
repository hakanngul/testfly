package io.testfly.internal.api;

import io.testfly.client.ApiAuth;
import io.testfly.client.ApiHmacSigner;
import io.testfly.config.TestFlyConfig;

import java.util.Locale;

/** Maps {@code api.auth.<name>} YAML strategies to {@link ApiAuth}; shared by the TestNG and JUnit 5 bridges. */
public final class AuthStrategies {

    static final String SUPPORTED = "bearer, basic, oauth2, oauth2_password, oauth2-refresh, apiKey, apikey-query, "
            + "digest, hmac (legacy), hmac-signed";

    private AuthStrategies() {
    }

    /** Returns the auth for {@code s}, or {@code null} when no type is set. */
    @SuppressWarnings("deprecation")
    public static ApiAuth resolve(TestFlyConfig.Api.AuthStrategy s) {
        String type = s.getType();
        if (type == null)
            return null;
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "bearer" -> ApiAuth.bearerToken(resolveEnvVar(s.getToken()));
            case "basic" -> ApiAuth.basicAuth(resolveEnvVar(s.getUsername()), resolveEnvVar(s.getPassword()));
            case "oauth2" -> ApiAuth.oauth2(resolveEnvVar(s.getTokenUrl()),
                    resolveEnvVar(s.getClientId()),
                    resolveEnvVar(s.getClientSecret()));
            case "apikey", "api_key", "apikey-header" -> ApiAuth.apiKey(
                    s.getHeaderName() != null ? s.getHeaderName() : "X-Api-Key",
                    resolveEnvVar(s.getApiKey() != null ? s.getApiKey() : s.getToken()));
            case "apikey-query", "api_key_query" -> ApiAuth.apiKeyQuery(
                    s.getHeaderName() != null ? s.getHeaderName() : "api_key",
                    resolveEnvVar(s.getApiKey() != null ? s.getApiKey() : s.getToken()));
            case "digest" -> ApiAuth.digest(resolveEnvVar(s.getUsername()), resolveEnvVar(s.getPassword()));
            case "hmac" -> ApiAuth.hmac(resolveEnvVar(s.getApiKey()), resolveEnvVar(s.getSecret()), s.getAlgorithm());
            case "hmac-signed", "hmac_signed" -> hmacSigner(s);
            case "oauth2_password", "oauth2-password", "password" -> ApiAuth.oauth2Password(
                    resolveEnvVar(s.getTokenUrl()), resolveEnvVar(s.getClientId()),
                    resolveEnvVar(s.getClientSecret()), resolveEnvVar(s.getUsername()), resolveEnvVar(s.getPassword()));
            case "oauth2-refresh", "oauth2_refresh", "refresh_token" -> ApiAuth.oauth2Refresh(
                    resolveEnvVar(s.getTokenUrl()), resolveEnvVar(s.getClientId()),
                    resolveEnvVar(s.getClientSecret()), resolveEnvVar(s.getRefreshToken()));
            default -> throw new IllegalArgumentException(
                    "[UseAuth] Unknown auth type: '" + type + "'. Use " + SUPPORTED);
        };
    }

    private static ApiAuth hmacSigner(TestFlyConfig.Api.AuthStrategy s) {
        ApiHmacSigner.Builder builder = ApiAuth.hmacSigner()
                .apiKey(resolveEnvVar(s.getApiKey()))
                .secret(resolveEnvVar(s.getSecret()));
        if (s.getAlgorithm() != null) builder.algorithm(s.getAlgorithm());
        String keyHeader = s.getKeyHeader() != null ? s.getKeyHeader() : s.getHeaderName();
        if (keyHeader != null) builder.keyHeader(disabled(keyHeader) ? null : keyHeader);
        if (s.getSignatureHeader() != null) builder.signatureHeader(s.getSignatureHeader());
        if (s.getTimestampHeader() != null) builder.timestampHeader(disabled(s.getTimestampHeader()) ? null : s.getTimestampHeader());
        if (s.getEncoding() != null) {
            builder.encoding(ApiHmacSigner.Encoding.valueOf(s.getEncoding().trim().toUpperCase(Locale.ROOT)));
        }
        return builder.build();
    }

    private static boolean disabled(String header) {
        return header.isBlank() || "none".equalsIgnoreCase(header.trim());
    }

    /** Resolves a whole-value {@code ${NAME}} placeholder from the environment, then system properties. */
    public static String resolveEnvVar(String value) {
        if (value == null)
            return null;
        if (value.startsWith("${") && value.endsWith("}")) {
            String varName = value.substring(2, value.length() - 1);
            String resolved = System.getenv(varName);
            if (resolved == null)
                resolved = System.getProperty(varName);
            return resolved != null ? resolved : value;
        }
        return value;
    }
}
