package io.testfly.client;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/** RFC 7616 HTTP Digest authentication; created by {@link ApiAuth#digest(String, String)}. */
final class DigestAuth implements ApiAuth.Intercepting {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    private final String username;
    private final String password;

    DigestAuth(String username, String password) {
        this.username = Objects.requireNonNull(username, "username");
        this.password = Objects.requireNonNull(password, "password");
    }

    /** Server challenge cached per origin for the current test; the nonce count is shared by its calls. */
    static final class Challenge {
        final String realm;
        final String nonce;
        final String opaque;
        final String algorithm;
        final Set<String> qop;
        final boolean stale;
        final AtomicInteger nonceCount = new AtomicInteger();

        Challenge(String realm, String nonce, String opaque, String algorithm, Set<String> qop, boolean stale) {
            this.realm = realm;
            this.nonce = nonce;
            this.opaque = opaque;
            this.algorithm = algorithm;
            this.qop = qop;
            this.stale = stale;
        }
    }

    @Override
    public ApiResponse intercept(Chain chain) {
        ApiRequest request = chain.request();
        String origin = origin(request.uri());
        Map<String, Challenge> cache = ApiClient.challengeCache();
        Challenge cached = cache.get(origin);
        ApiResponse response = chain.proceed(cached == null ? request : authorize(request, cached));
        if (response.status() != 401) return response;
        Challenge fresh = parse(response.headerValues("WWW-Authenticate"));
        if (fresh == null) return response;
        // Same nonce and not stale: the credentials were rejected, do not retry.
        if (cached != null && !fresh.stale && fresh.nonce.equals(cached.nonce)) return response;
        cache.put(origin, fresh);
        return chain.proceed(authorize(request, fresh));
    }

    private ApiRequest authorize(ApiRequest request, Challenge challenge) {
        String algorithm = challenge.algorithm;
        boolean session = algorithm.toUpperCase(Locale.ROOT).endsWith("-SESS");
        String digestName = digestName(algorithm);
        URI uri = request.uri();
        String digestUri = (uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath())
                + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
        String qop = challenge.qop.contains("auth") ? "auth" : challenge.qop.contains("auth-int") ? "auth-int" : null;
        String cnonce = HEX.formatHex(randomBytes());
        String nc = String.format("%08x", challenge.nonceCount.incrementAndGet());

        String ha1 = hash(digestName, username + ":" + challenge.realm + ":" + password);
        if (session) ha1 = hash(digestName, ha1 + ":" + challenge.nonce + ":" + cnonce);
        String a2 = request.method() + ":" + digestUri;
        if ("auth-int".equals(qop)) a2 += ":" + hash(digestName, request.body());
        String ha2 = hash(digestName, a2);
        String response = qop == null
                ? hash(digestName, ha1 + ":" + challenge.nonce + ":" + ha2)
                : hash(digestName, ha1 + ":" + challenge.nonce + ":" + nc + ":" + cnonce + ":" + qop + ":" + ha2);

        StringBuilder header = new StringBuilder("Digest ")
                .append("username=").append(quote(username))
                .append(", realm=").append(quote(challenge.realm))
                .append(", nonce=").append(quote(challenge.nonce))
                .append(", uri=").append(quote(digestUri))
                .append(", algorithm=").append(algorithm)
                .append(", response=").append(quote(response));
        if (challenge.opaque != null) header.append(", opaque=").append(quote(challenge.opaque));
        if (qop != null) header.append(", qop=").append(qop).append(", nc=").append(nc)
                .append(", cnonce=").append(quote(cnonce));
        return request.newBuilder().header("Authorization", header.toString()).build();
    }

    /** Picks the strongest supported Digest challenge from the WWW-Authenticate headers. */
    static Challenge parse(List<String> headers) {
        List<Challenge> candidates = new ArrayList<>();
        for (String header : headers) {
            String lower = header.toLowerCase(Locale.ROOT);
            int from = 0;
            while (true) {
                int at = lower.indexOf("digest", from);
                if (at < 0) break;
                boolean startsScheme = (at == 0 || lower.charAt(at - 1) == ' ' || lower.charAt(at - 1) == ',')
                        && at + 6 < lower.length() && Character.isWhitespace(lower.charAt(at + 6));
                from = at + 6;
                if (!startsScheme) continue;
                Map<String, String> params = params(header, at + 7);
                Challenge challenge = toChallenge(params);
                if (challenge != null) candidates.add(challenge);
            }
        }
        Challenge best = null;
        for (Challenge c : candidates) if (best == null || strength(c.algorithm) > strength(best.algorithm)) best = c;
        return best;
    }

    private static Challenge toChallenge(Map<String, String> params) {
        String nonce = params.get("nonce");
        String realm = params.get("realm");
        if (nonce == null || realm == null) return null;
        String algorithm = params.getOrDefault("algorithm", "MD5");
        try {
            digestName(algorithm);
        } catch (IllegalStateException unsupported) {
            return null;
        }
        Set<String> qop = new LinkedHashSet<>();
        String qopValue = params.get("qop");
        if (qopValue != null) for (String q : qopValue.split(",")) if (!q.isBlank()) qop.add(q.trim().toLowerCase(Locale.ROOT));
        boolean stale = "true".equalsIgnoreCase(params.get("stale"));
        return new Challenge(realm, nonce, params.get("opaque"), algorithm, qop, stale);
    }

    /** Parses {@code k=v, k="quoted"} pairs until a token that starts the next auth scheme. */
    private static Map<String, String> params(String header, int start) {
        Map<String, String> params = new LinkedHashMap<>();
        int i = start;
        int n = header.length();
        while (i < n) {
            while (i < n && (header.charAt(i) == ',' || Character.isWhitespace(header.charAt(i)))) i++;
            int nameStart = i;
            while (i < n && header.charAt(i) != '=' && header.charAt(i) != ',' && !Character.isWhitespace(header.charAt(i))) i++;
            String name = header.substring(nameStart, i).toLowerCase(Locale.ROOT);
            int j = i;
            while (j < n && Character.isWhitespace(header.charAt(j))) j++;
            if (name.isEmpty() || j >= n || header.charAt(j) != '=') break; // next scheme or end
            i = j + 1;
            while (i < n && Character.isWhitespace(header.charAt(i))) i++;
            StringBuilder value = new StringBuilder();
            if (i < n && header.charAt(i) == '"') {
                i++;
                while (i < n && header.charAt(i) != '"') {
                    if (header.charAt(i) == '\\' && i + 1 < n) i++;
                    value.append(header.charAt(i++));
                }
                i++;
            } else {
                while (i < n && header.charAt(i) != ',') value.append(header.charAt(i++));
            }
            params.putIfAbsent(name, value.toString().trim());
        }
        return params;
    }

    private static int strength(String algorithm) {
        String base = algorithm.toUpperCase(Locale.ROOT).replace("-SESS", "");
        return switch (base) {
            case "SHA-512-256" -> 3;
            case "SHA-256" -> 2;
            default -> 1;
        };
    }

    private static String digestName(String algorithm) {
        return switch (algorithm.toUpperCase(Locale.ROOT).replace("-SESS", "")) {
            case "MD5" -> "MD5";
            case "SHA-256" -> "SHA-256";
            case "SHA-512-256" -> "SHA-512/256";
            default -> throw new IllegalStateException("[ApiAuth] Unsupported Digest algorithm: " + algorithm);
        };
    }

    private static String hash(String digestName, String value) {
        return hash(digestName, value.getBytes(StandardCharsets.UTF_8));
    }

    private static String hash(String digestName, byte[] value) {
        try {
            return HEX.formatHex(MessageDigest.getInstance(digestName).digest(value));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("[ApiAuth] Digest algorithm unavailable: " + digestName, e);
        }
    }

    private static byte[] randomBytes() {
        byte[] bytes = new byte[8];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String origin(URI uri) {
        String scheme = uri.getScheme() == null ? "http" : uri.getScheme().toLowerCase(Locale.ROOT);
        int port = uri.getPort() != -1 ? uri.getPort() : "https".equals(scheme) ? 443 : 80;
        return scheme + "://" + String.valueOf(uri.getHost()).toLowerCase(Locale.ROOT) + ":" + port;
    }
}
