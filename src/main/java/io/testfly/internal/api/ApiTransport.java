package io.testfly.internal.api;

import io.testfly.config.TestFlyConfig;
import io.testfly.steps.StepLogger;
import io.testfly.steps.StepStatus;
import javax.net.ssl.*;
import java.net.Socket;
import java.net.http.HttpClient;
import java.nio.file.*;
import java.security.KeyStore;
import java.security.cert.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Semaphore;

/** Managed JDK clients; no JVM SSL or connection-pool properties are mutated. */
public final class ApiTransport {
    private ApiTransport() {}
    private record ClientKey(int connectSeconds, ProxySelection proxy) {}
    private static final Map<ClientKey, HttpClient> DEFAULTS = new HashMap<>();
    private static final Map<ApiExecution.Scope, List<Profile>> SCOPED = new IdentityHashMap<>();
    private static final Set<ApiExecution.Scope> WARNED = Collections.newSetFromMap(new IdentityHashMap<>());
    private static Semaphore limiter;
    private static Integer limit;

    public static final class SslSelection {
        private final boolean trustAll;
        private final Path path;
        private final char[] password;
        private final String type;
        private SslSelection(boolean all, Path path, char[] password, String type) {
            this.trustAll = all; this.path = path; this.password = password == null ? new char[0] : password.clone(); this.type = type;
        }
        public boolean trustAll() { return trustAll; }
        public static SslSelection trustAllSelection() { return new SslSelection(true, null, null, null); }
        public static SslSelection store(Path path, char[] password, String type) {
            Objects.requireNonNull(path, "truststore path");
            if (!"PKCS12".equalsIgnoreCase(type) && !"JKS".equalsIgnoreCase(type)) throw new IllegalArgumentException("Truststore type must be PKCS12 or JKS");
            return new SslSelection(false, path.toAbsolutePath().normalize(), password, type.toUpperCase(Locale.ROOT));
        }
        private boolean same(SslSelection other) {
            return other != null && trustAll == other.trustAll && Objects.equals(path, other.path)
                    && Objects.equals(type, other.type) && Arrays.equals(password, other.password);
        }
    }
    /**
     * HTTP proxy selection. A {@code null} host means direct connections (used by
     * {@code ApiClient.noProxy()} to override a configured proxy).
     */
    public record ProxySelection(String host, int port, List<String> nonProxyHosts) {
        public ProxySelection {
            nonProxyHosts = nonProxyHosts == null ? List.of() : List.copyOf(nonProxyHosts);
            if (host != null && host.isBlank()) host = null;
            if (host != null && (port < 1 || port > 65535)) throw new IllegalArgumentException("Proxy port must be 1..65535");
        }
        public static ProxySelection direct() { return new ProxySelection(null, 0, List.of()); }
        public static ProxySelection of(String host, int port) {
            Objects.requireNonNull(host, "proxy host");
            if (host.isBlank()) throw new IllegalArgumentException("Proxy host must not be blank");
            return new ProxySelection(host, port, List.of());
        }
        public boolean isDirect() { return host == null; }
        /** True when requests to {@code targetHost} must bypass the proxy. */
        public boolean bypasses(String targetHost) {
            if (host == null || targetHost == null) return true;
            String target = targetHost.startsWith("[") && targetHost.endsWith("]")
                    ? targetHost.substring(1, targetHost.length() - 1) : targetHost;
            for (String pattern : nonProxyHosts) {
                if (pattern == null || pattern.isBlank()) continue;
                String regex = ("\\Q" + pattern.trim() + "\\E").replace("*", "\\E.*\\Q");
                if (java.util.regex.Pattern.compile(regex, java.util.regex.Pattern.CASE_INSENSITIVE).matcher(target).matches()) return true;
            }
            return false;
        }
        static ProxySelection fromConfig(TestFlyConfig.Api api) {
            if (api == null || api.getProxy() == null) return null;
            var proxy = api.getProxy();
            if (proxy.getHost() == null || proxy.getHost().isBlank()) return null;
            return new ProxySelection(proxy.getHost().trim(), proxy.getPort(), proxy.getNonProxyHosts());
        }
    }

    public record Profile(HttpClient client, SslSelection ssl, int connectSeconds, Semaphore permits, ProxySelection proxy) {}

    public static Profile prepare(ApiExecution.Scope scope, TestFlyConfig.Api api, SslSelection override) {
        return prepare(scope, api, override, null);
    }

    /**
     * @param proxyOverride {@code null} uses {@code api.proxy}; {@link ProxySelection#direct()} forces direct connections
     */
    public static synchronized Profile prepare(ApiExecution.Scope scope, TestFlyConfig.Api api, SslSelection override,
                                               ProxySelection proxyOverride) {
        int connect = api == null ? 30 : api.getConnectTimeoutSeconds();
        int max = api == null ? 0 : api.getMaxConcurrentRequests();
        if (connect <= 0 || max < 0) throw new IllegalArgumentException("Invalid API connect timeout/concurrency");
        SslSelection selection = override;
        if (selection == null && api != null && api.getSsl() != null) {
            var ssl = api.getSsl(); var store = ssl.getTrustStore();
            if (ssl.isTrustAll() && store != null) throw new IllegalArgumentException("Conflicting SSL selection");
            if (ssl.isTrustAll()) selection = SslSelection.trustAllSelection();
            else if (store != null) selection = SslSelection.store(Path.of(Objects.requireNonNull(store.getPath(), "truststore path")),
                    store.getPassword() == null ? null : store.getPassword().toCharArray(), store.getType());
        }
        ProxySelection proxySelection = proxyOverride != null ? proxyOverride : ProxySelection.fromConfig(api);
        final ProxySelection proxy = proxySelection == null || proxySelection.isDirect() ? null : proxySelection;
        if (limit == null) { limit = max; limiter = max == 0 ? null : new Semaphore(max, true); }
        else if (limit != max) throw new IllegalArgumentException("API concurrency configuration cannot change while test scopes are active");
        List<Profile> profiles = SCOPED.computeIfAbsent(scope, ignored -> new ArrayList<>());
        for (Profile p : profiles) if (p.connectSeconds == connect && Objects.equals(p.proxy, proxy)
                && (selection == null ? p.ssl == null : selection.same(p.ssl))) return p;
        HttpClient client;
        if (selection == null) client = DEFAULTS.computeIfAbsent(new ClientKey(connect, proxy), ignored -> builder(connect, proxy).build());
        else {
            try { client = builder(connect, proxy).sslContext(context(selection)).build(); }
            catch (Exception error) { throw new IllegalArgumentException("Cannot initialize API truststore/SSL profile", error); }
        }
        Profile profile = new Profile(client, selection, connect, limiter, proxy); profiles.add(profile); return profile;
    }
    private static HttpClient.Builder builder(int connect, ProxySelection proxy) {
        HttpClient.Builder builder = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(connect))
                .followRedirects(HttpClient.Redirect.NORMAL);
        if (proxy != null) builder.proxy(selector(proxy));
        return builder;
    }
    private static java.net.ProxySelector selector(ProxySelection selection) {
        java.net.Proxy proxy = new java.net.Proxy(java.net.Proxy.Type.HTTP,
                java.net.InetSocketAddress.createUnresolved(selection.host(), selection.port()));
        return new java.net.ProxySelector() {
            @Override public List<java.net.Proxy> select(java.net.URI uri) {
                return selection.bypasses(uri.getHost()) ? List.of(java.net.Proxy.NO_PROXY) : List.of(proxy);
            }
            @Override public void connectFailed(java.net.URI uri, java.net.SocketAddress address, java.io.IOException error) { }
        };
    }
    private static SSLContext context(SslSelection selection) throws Exception {
        TrustManager[] managers;
        if (selection.trustAll) managers = new TrustManager[]{new CertificateTrustOnlyManager()};
        else {
            KeyStore store = KeyStore.getInstance(selection.type);
            try (var input = Files.newInputStream(selection.path)) { store.load(input, selection.password); }
            TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            factory.init(store); managers = factory.getTrustManagers();
        }
        SSLContext context = SSLContext.getInstance("TLS"); context.init(null, managers, null); return context;
    }
    /** Trust the peer leaf as an anchor but delegate HTTPS identity checks to the JDK. */
    private static final class CertificateTrustOnlyManager extends X509ExtendedTrustManager {
        private X509ExtendedTrustManager peerManager(X509Certificate[] chain) throws CertificateException {
            if (chain == null || chain.length == 0) throw new CertificateException("Missing peer certificate");
            try {
                KeyStore store = KeyStore.getInstance("PKCS12"); store.load(null, null); store.setCertificateEntry("peer", chain[0]);
                TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()); factory.init(store);
                for (TrustManager manager : factory.getTrustManagers()) if (manager instanceof X509ExtendedTrustManager extended) return extended;
                throw new CertificateException("HTTPS identity verification unavailable");
            } catch (CertificateException error) { throw error; }
            catch (Exception error) { throw new CertificateException("Cannot verify peer identity", error); }
        }
        @Override public void checkServerTrusted(X509Certificate[] chain, String auth, SSLEngine engine) throws CertificateException {
            if (engine == null || !"HTTPS".equalsIgnoreCase(engine.getSSLParameters().getEndpointIdentificationAlgorithm())) throw new CertificateException("HTTPS identity verification required");
            peerManager(chain).checkServerTrusted(chain, auth, engine);
        }
        @Override public void checkServerTrusted(X509Certificate[] chain, String auth, Socket socket) throws CertificateException {
            if (!(socket instanceof SSLSocket ssl) || !"HTTPS".equalsIgnoreCase(ssl.getSSLParameters().getEndpointIdentificationAlgorithm())) throw new CertificateException("HTTPS identity verification required");
            peerManager(chain).checkServerTrusted(chain, auth, socket);
        }
        @Override public void checkServerTrusted(X509Certificate[] c, String a) throws CertificateException { throw new CertificateException("Peer identity unavailable"); }
        @Override public void checkClientTrusted(X509Certificate[] c, String a) throws CertificateException { throw new CertificateException("Client authentication unsupported"); }
        @Override public void checkClientTrusted(X509Certificate[] c, String a, Socket s) throws CertificateException { checkClientTrusted(c,a); }
        @Override public void checkClientTrusted(X509Certificate[] c, String a, SSLEngine e) throws CertificateException { checkClientTrusted(c,a); }
        @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
    }
    public interface Permit extends AutoCloseable { @Override void close(); }
    public static Permit acquire(Profile profile, ApiExecution.Scope scope) throws InterruptedException {
        if (profile.permits != null) profile.permits.acquire();
        return () -> { if (profile.permits != null) profile.permits.release(); };
    }
    public static synchronized void warnTrustAll(Profile profile, ApiExecution.Scope scope, java.net.URI uri) {
        if (profile.ssl != null && profile.ssl.trustAll && "https".equalsIgnoreCase(uri.getScheme()) && WARNED.add(scope))
            scope.log(() -> StepLogger.step("[API] Certificate trust bypass enabled; HTTPS hostname verification remains enabled", StepStatus.WARN));
    }
    public static synchronized void closeScope(ApiExecution.Scope scope) {
        List<Profile> profiles = SCOPED.remove(scope); WARNED.remove(scope);
        if (profiles != null) for (Profile profile : profiles) if (profile.ssl != null) profile.client.shutdownNow();
        if (SCOPED.isEmpty()) { limit = null; limiter = null; }
    }
    public static synchronized void shutdown() {
        for (var scope : List.copyOf(SCOPED.keySet())) closeScope(scope);
        DEFAULTS.values().forEach(HttpClient::shutdownNow); DEFAULTS.clear();
    }
}
