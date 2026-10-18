package io.testfly.mobile.internal.rpc;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable internal transport options. No mobilecli capabilities are implied. */
public final class JsonRpcOptions {
    private static final Duration DEFAULT_RPC_TIMEOUT = Duration.ofSeconds(30);
    private static final int DEFAULT_MAX_RESPONSE_BYTES = 8 * 1024 * 1024;
    private static final int MAX_RESPONSE_BYTES = 64 * 1024 * 1024;
    private static final Set<String> RESERVED_HEADERS = Set.of(
            "host", "content-length", "connection", "expect", "upgrade",
            "transfer-encoding", "content-type", "accept");

    private final URI normalizedServerUrl;
    private final URI rpcEndpoint;
    private final boolean allowRemote;
    private final Map<String, String> headers;
    private final Duration rpcTimeout;
    private final int maxResponseBytes;

    public JsonRpcOptions(URI serverUrl, boolean allowRemote, Map<String, String> headers,
                          Duration rpcTimeout, int maxResponseBytes) {
        this.normalizedServerUrl = normalizeEndpoint(serverUrl, allowRemote);
        this.rpcEndpoint = URI.create(normalizedServerUrl.toASCIIString() + "/rpc");
        this.allowRemote = allowRemote;
        this.headers = validateHeaders(headers);
        this.rpcTimeout = validateTimeout(rpcTimeout);
        if (maxResponseBytes <= 0 || maxResponseBytes > MAX_RESPONSE_BYTES) {
            throw new IllegalArgumentException("Response limit must be between 1 and 67108864 bytes");
        }
        this.maxResponseBytes = maxResponseBytes;
    }

    public static JsonRpcOptions defaults(URI serverUrl) {
        return new JsonRpcOptions(serverUrl, false, Map.of(), DEFAULT_RPC_TIMEOUT,
                DEFAULT_MAX_RESPONSE_BYTES);
    }

    /** Canonical server identity, including effective port and any validated proxy base path. */
    public URI normalizedServerUrl() {
        return normalizedServerUrl;
    }

    public URI rpcEndpoint() {
        return rpcEndpoint;
    }

    public boolean allowRemote() {
        return allowRemote;
    }

    public Map<String, String> headers() {
        return headers;
    }

    public Duration rpcTimeout() {
        return rpcTimeout;
    }

    public int maxResponseBytes() {
        return maxResponseBytes;
    }

    @Override
    public String toString() {
        return "JsonRpcOptions[endpoint=<redacted>, headers=<redacted>, allowRemote="
                + allowRemote + ", rpcTimeout=" + rpcTimeout + ", maxResponseBytes="
                + maxResponseBytes + "]";
    }

    private static URI normalizeEndpoint(URI serverUrl, boolean allowRemote) {
        Objects.requireNonNull(serverUrl, "serverUrl");
        String scheme = serverUrl.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                || serverUrl.isOpaque() || serverUrl.getHost() == null
                || serverUrl.getRawUserInfo() != null || serverUrl.getRawQuery() != null
                || serverUrl.getRawFragment() != null) {
            throw new IllegalArgumentException("Server URL must be HTTP(S), without credentials, query or fragment");
        }
        scheme = scheme.toLowerCase(Locale.ROOT);
        String host = serverUrl.getHost().toLowerCase(Locale.ROOT);
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        boolean loopback = false;
        if (host.equals("localhost")) {
            validateLocalhost();
            loopback = true;
        } else if (host.indexOf(':') >= 0 && host.matches("[0-9a-f:.]+")) {
            try {
                InetAddress address = InetAddress.getByName(host);
                loopback = address.isLoopbackAddress();
                host = address.getHostAddress();
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException("Invalid server IP address");
            }
        } else if (host.matches("[0-9.]+")) {
            String[] octets = host.split("\\.", -1);
            if (octets.length != 4) {
                throw new IllegalArgumentException("Server IPv4 address must use four canonical octets");
            }
            for (String octet : octets) {
                if (octet.isEmpty() || octet.length() > 3
                        || (octet.length() > 1 && octet.charAt(0) == '0')
                        || Integer.parseInt(octet) > 255) {
                    throw new IllegalArgumentException("Server IPv4 address must use four canonical octets");
                }
            }
            loopback = octets[0].equals("127");
        }
        if (!loopback && (!allowRemote || !scheme.equals("https"))) {
            throw new IllegalArgumentException("Remote servers require allowRemote and HTTPS");
        }
        int port = serverUrl.getPort();
        if (port == 0 || port > 65535 || port < -1) {
            throw new IllegalArgumentException("Invalid server port");
        }
        if (port == -1) {
            port = scheme.equals("https") ? 443 : 80;
        }
        String path = serverUrl.getRawPath();
        if (path == null || path.equals("/")) {
            path = "";
        } else if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        // Restrict proxy prefixes to unreserved segments. This excludes encoded traversal,
        // separators and ambiguous double-decoding rather than relying on URI normalization.
        if (!path.isEmpty()) {
            if (!path.startsWith("/") || path.contains("//")) {
                throw new IllegalArgumentException("Invalid server base path");
            }
            for (String segment : path.substring(1).split("/", -1)) {
                if (!segment.matches("[A-Za-z0-9._~-]+") || segment.equals(".") || segment.equals("..")) {
                    throw new IllegalArgumentException("Server base path must use unreserved, non-traversing segments");
                }
            }
        }
        try {
            return new URI(scheme, null, host, port, path, null, null);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid server URL");
        }
    }

    private static void validateLocalhost() {
        try {
            InetAddress[] addresses = InetAddress.getAllByName("localhost");
            if (addresses.length == 0) {
                throw new IllegalArgumentException("localhost must resolve exclusively to loopback addresses");
            }
            for (InetAddress address : addresses) {
                if (!address.isLoopbackAddress()) {
                    throw new IllegalArgumentException("localhost must resolve exclusively to loopback addresses");
                }
            }
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("localhost could not be validated as loopback");
        }
    }

    private static Duration validateTimeout(Duration timeout) {
        Objects.requireNonNull(timeout, "rpcTimeout");
        try {
            if (timeout.toNanos() <= 0) {
                throw new IllegalArgumentException("RPC timeout must be positive");
            }
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("RPC timeout is too large");
        }
        return timeout;
    }

    private static Map<String, String> validateHeaders(Map<String, String> headers) {
        Objects.requireNonNull(headers, "headers");
        Map<String, String> copy = new LinkedHashMap<>();
        Set<String> names = new java.util.HashSet<>();
        headers.forEach((name, value) -> {
            if (name == null || !name.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+")
                    || RESERVED_HEADERS.contains(name.toLowerCase(Locale.ROOT))
                    || !names.add(name.toLowerCase(Locale.ROOT)) || value == null
                    || value.chars().anyMatch(c -> (c < 32 && c != '\t') || c == 127)) {
                throw new IllegalArgumentException("Invalid, duplicate or reserved HTTP header");
            }
            copy.put(name, value);
        });
        return Map.copyOf(copy);
    }
}
