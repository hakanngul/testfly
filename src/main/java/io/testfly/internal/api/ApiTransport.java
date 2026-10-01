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
    private static final Map<Integer, HttpClient> DEFAULTS = new HashMap<>();
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
    public record Profile(HttpClient client, SslSelection ssl, int connectSeconds, Semaphore permits) {}

    public static synchronized Profile prepare(ApiExecution.Scope scope, TestFlyConfig.Api api, SslSelection override) {
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
        if (limit == null) { limit = max; limiter = max == 0 ? null : new Semaphore(max, true); }
        else if (limit != max) throw new IllegalArgumentException("API concurrency configuration cannot change while test scopes are active");
        List<Profile> profiles = SCOPED.computeIfAbsent(scope, ignored -> new ArrayList<>());
        for (Profile p : profiles) if (p.connectSeconds == connect && (selection == null ? p.ssl == null : selection.same(p.ssl))) return p;
        HttpClient client;
        if (selection == null) client = DEFAULTS.computeIfAbsent(connect, ignored -> builder(connect).build());
        else {
            try { client = builder(connect).sslContext(context(selection)).build(); }
            catch (Exception error) { throw new IllegalArgumentException("Cannot initialize API truststore/SSL profile", error); }
        }
        Profile profile = new Profile(client, selection, connect, limiter); profiles.add(profile); return profile;
    }
    private static HttpClient.Builder builder(int connect) {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(connect)).followRedirects(HttpClient.Redirect.NORMAL);
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
