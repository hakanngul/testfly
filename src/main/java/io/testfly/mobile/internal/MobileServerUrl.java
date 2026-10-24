package io.testfly.mobile.internal;

import io.testfly.exceptions.ConfigurationException;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * Validates {@code mobile.serverUrl} (ADR 0002 D5). mobilecli's endpoint has no authentication and
 * whoever reaches it controls the device, so only loopback addresses are accepted by default. A
 * remote device lab needs {@code mobile.allowRemote: true} and {@code https://}.
 */
public final class MobileServerUrl {

    private MobileServerUrl() {
    }

    /** Returns the {@code /rpc} endpoint of {@code serverUrl}, or throws {@link ConfigurationException}. */
    public static URI rpcEndpoint(String serverUrl, boolean allowRemote) {
        URI base;
        try {
            base = URI.create(serverUrl.trim());
        } catch (IllegalArgumentException e) {
            throw new ConfigurationException("mobile.serverUrl is not a valid URL: " + serverUrl, e);
        }
        String scheme = base.getScheme() == null ? "" : base.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new ConfigurationException("mobile.serverUrl must use http:// or https://, got: " + serverUrl);
        }
        String host = base.getHost();
        if (host == null || host.isBlank()) {
            throw new ConfigurationException("mobile.serverUrl has no host: " + serverUrl);
        }
        if (!isLoopback(host)) {
            if (!allowRemote) {
                throw new ConfigurationException("mobile.serverUrl points to a non-loopback host (" + host + "). "
                        + "mobilecli has no authentication, so remote servers are refused by default. "
                        + "Put it behind a TLS-terminating, authenticating proxy or an SSH tunnel, "
                        + "then set mobile.allowRemote: true and use https://.");
            }
            if (!scheme.equals("https")) {
                throw new ConfigurationException("mobile.allowRemote requires an https:// serverUrl, got: " + serverUrl);
            }
        }
        String path = base.getPath() == null ? "" : base.getPath();
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return base.resolve(path.isEmpty() ? "/rpc" : path + "/rpc");
    }

    static boolean isLoopback(String host) {
        String h = host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
        if (h.equalsIgnoreCase("localhost")) {
            return true;
        }
        // Only literal addresses are checked: a DNS name is never trusted to be loopback.
        if (!h.matches("[0-9.]+") && !h.contains(":")) {
            return false;
        }
        try {
            return InetAddress.getByName(h).isLoopbackAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
