package io.testfly.client;

import java.net.URI;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Thread-safe cookie store following the RFC 6265 rules that matter for API tests: cookies are
 * scoped to their host (or {@code Domain}), {@code Path}, {@code Secure} flag and lifetime
 * ({@code Max-Age} / {@code Expires}).
 *
 * <p>Deliberate deviation: a cookie without a {@code Path} attribute applies to {@code /} instead of
 * the RFC default-path, so a session cookie set by {@code /api/auth/login} still reaches
 * {@code /api/users}.
 */
final class CookieJar {

    private record Cookie(String name, String value, String domain, boolean hostOnly, String path,
                          Instant expiresAt, boolean secure) {
        String key() {
            return name + "|" + domain + "|" + path;
        }

        boolean expired(Instant now) {
            return expiresAt != null && !now.isBefore(expiresAt);
        }

        boolean matches(String host, String requestPath, boolean https) {
            boolean hostOk = hostOnly ? host.equals(domain) : host.equals(domain) || host.endsWith("." + domain);
            return hostOk && (!secure || https) && pathMatches(requestPath, path);
        }
    }

    private static final DateTimeFormatter NETSCAPE =
            DateTimeFormatter.ofPattern("EEE, dd-MMM-yyyy HH:mm:ss zzz", Locale.US);

    private final Map<String, Cookie> cookies = new ConcurrentHashMap<>();

    /** Stores the {@code Set-Cookie} headers of a response to {@code requestUri}. */
    void capture(URI requestUri, List<String> setCookieHeaders) {
        String host = host(requestUri);
        Instant now = Instant.now();
        for (String header : setCookieHeaders) {
            Cookie cookie = parse(header, host, now);
            if (cookie == null) continue;
            if (cookie.expired(now)) cookies.remove(cookie.key());
            else cookies.put(cookie.key(), cookie);
        }
    }

    /** {@code Cookie} header value for {@code uri}, or {@code null} when no cookie applies. */
    String headerFor(URI uri) {
        String host = host(uri);
        String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        boolean https = "https".equalsIgnoreCase(uri.getScheme());
        Instant now = Instant.now();
        cookies.values().removeIf(c -> c.expired(now));
        List<Cookie> matching = new ArrayList<>();
        for (Cookie c : cookies.values()) if (c.matches(host, path, https)) matching.add(c);
        if (matching.isEmpty()) return null;
        matching.sort(Comparator.comparingInt((Cookie c) -> c.path().length()).reversed());
        return matching.stream().map(c -> c.name() + "=" + c.value()).collect(Collectors.joining("; "));
    }

    boolean isEmpty() {
        return cookies.isEmpty();
    }

    private static Cookie parse(String header, String host, Instant now) {
        String[] parts = header.split(";");
        int eq = parts[0].indexOf('=');
        if (eq <= 0) return null;
        String name = parts[0].substring(0, eq).trim();
        String value = parts[0].substring(eq + 1).trim();
        if (name.isEmpty()) return null;
        String domain = null;
        String path = null;
        Instant expires = null;
        Long maxAge = null;
        boolean secure = false;
        for (int i = 1; i < parts.length; i++) {
            String attr = parts[i].trim();
            int aeq = attr.indexOf('=');
            String key = (aeq < 0 ? attr : attr.substring(0, aeq)).trim().toLowerCase(Locale.ROOT);
            String val = aeq < 0 ? "" : attr.substring(aeq + 1).trim();
            switch (key) {
                case "domain" -> domain = val.startsWith(".") ? val.substring(1) : val;
                case "path" -> path = val.startsWith("/") ? val : null;
                case "secure" -> secure = true;
                case "max-age" -> {
                    try {
                        maxAge = Long.parseLong(val);
                    } catch (NumberFormatException ignored) {
                        // invalid Max-Age is ignored (RFC 6265 5.2.2)
                    }
                }
                case "expires" -> expires = parseDate(val);
                default -> { }
            }
        }
        boolean hostOnly = domain == null || domain.isEmpty();
        String effectiveDomain = hostOnly ? host : domain.toLowerCase(Locale.ROOT);
        if (!hostOnly && !(host.equals(effectiveDomain) || host.endsWith("." + effectiveDomain))) {
            return null; // a server may not set cookies for a foreign domain
        }
        Instant expiresAt = maxAge != null ? (maxAge <= 0 ? Instant.EPOCH : now.plusSeconds(maxAge)) : expires;
        return new Cookie(name, value, effectiveDomain, hostOnly, path == null ? "/" : path, expiresAt, secure);
    }

    private static Instant parseDate(String value) {
        for (DateTimeFormatter format : List.of(DateTimeFormatter.RFC_1123_DATE_TIME, NETSCAPE)) {
            try {
                return ZonedDateTime.parse(value, format).toInstant();
            } catch (RuntimeException ignored) {
                // try next format
            }
        }
        return null;
    }

    private static boolean pathMatches(String requestPath, String cookiePath) {
        if (requestPath.equals(cookiePath)) return true;
        if (!requestPath.startsWith(cookiePath)) return false;
        return cookiePath.endsWith("/") || requestPath.charAt(cookiePath.length()) == '/';
    }

    private static String host(URI uri) {
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        return host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
    }
}
