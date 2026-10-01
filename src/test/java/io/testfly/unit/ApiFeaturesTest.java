package io.testfly.unit;

import com.sun.net.httpserver.*;
import io.testfly.client.*;
import io.testfly.config.TestFlyConfig;
import io.testfly.internal.TestFlyContext;
import io.testfly.internal.api.*;
import org.mockito.MockedStatic;
import org.testng.annotations.*;
import javax.net.ssl.*;
import java.net.*;
import java.nio.file.*;
import java.security.KeyStore;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

@Test(singleThreaded = true)
public class ApiFeaturesTest {
    private MockedStatic<TestFlyContext> context;
    private TestFlyConfig config;
    private HttpServer server;
    private ExecutorService executor;
    private Path directory;
    private static final char[] PASSWORD = "test-password".toCharArray();
    @BeforeMethod public void setup() {
        directory = null;
        ApiExecution.shutdown(); ApiExecution.cleanupTestContext();
        config = new TestFlyConfig(); config.setApi(new TestFlyConfig.Api());
        context = mockStatic(TestFlyContext.class, CALLS_REAL_METHODS);
        context.when(TestFlyContext::isInitialized).thenReturn(true);
        context.when(TestFlyContext::getConfig).thenReturn(config);
    }
    @AfterMethod public void cleanup() throws Exception {
        ApiExecution.cleanupTestContext(); ApiExecution.shutdown(); context.close();
        if (server != null) { server.stop(0); server = null; }
        if (executor != null) executor.shutdownNow();
        if (directory != null) try (var files = Files.walk(directory)) { for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file); }
    }
    private ApiMockRule rule(int status) { return ApiMockRule.builder().match(r -> true).respond(r -> ApiResponse.builder().request(r).status(status).body("mock").build()).build(); }
    private String server(HttpHandler handler) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0); executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor); server.createContext("/",handler); server.start(); return "http://127.0.0.1:"+server.getAddress().getPort();
    }
    private void reply(HttpExchange exchange) throws java.io.IOException { exchange.sendResponseHeaders(200,2); exchange.getResponseBody().write("ok".getBytes()); exchange.close(); }
    @Test public void mockPriorityAndMiddleware() {
        ApiClient.addMockRule(rule(201)); AtomicInteger requestHooks = new AtomicInteger(); AtomicInteger responseHooks = new AtomicInteger();
        ApiClient.addRequestInterceptor(b -> requestHooks.incrementAndGet()); ApiClient.addResponseInterceptor(r -> responseHooks.incrementAndGet());
        try {
            ApiResponse response = ApiClient.get("http://localhost:1/a").mockRule(ApiMockRule.builder()
                .match(r -> r.uri().getPath().equals("/b"))
                .respond(r -> ApiResponse.builder().request(r).status(202).build()).build())
                .interceptor(c -> c.proceed(c.request().newBuilder().uri(URI.create("http://localhost:1/b")).build())).send();
            assertEquals(response.status(),202); assertTrue(response.isSynthetic()); assertEquals(requestHooks.get(),0); assertEquals(responseHooks.get(),1);
        } finally { ApiClient.clearInterceptors(); }
    }
    @Test public void mockSnapshotRetryAndCleanup() {
        config.getApi().getRetry().setEnabled(true); config.getApi().getRetry().setMaxAttempts(2); config.getApi().getRetry().setBackoffMs(0); config.getApi().getRetry().setRetryOnStatus(List.of(503));
        AtomicInteger calls = new AtomicInteger(); ApiClient.addMockRule(ApiMockRule.builder().match(r -> true).respond(r -> ApiResponse.builder().request(r).status(calls.incrementAndGet()==1?503:200).build()).build());
        var future=ApiClient.get("http://localhost:1/test").sendAsync(); ApiClient.clearMockRules(); assertEquals(future.join().status(),200); assertEquals(calls.get(),2);
        ApiClient.addMockRule(rule(201)); ApiExecution.cleanupTestContext();
        assertThrows(ApiException.class, () -> ApiClient.get("http://127.0.0.1:0/").send());
    }
    @Test public void mockErrorsDoNotRetry() {
        config.getApi().getRetry().setEnabled(true); config.getApi().getRetry().setRetryOnException(true);
        AtomicInteger calls = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> ApiClient.get("http://localhost:1/").mockRule(ApiMockRule.builder().match(r -> { calls.incrementAndGet(); throw new IllegalStateException(); }).respond(r -> null).build()).send());
        assertEquals(calls.get(),1);
        assertThrows(NullPointerException.class, () -> ApiClient.get("http://localhost:1/").mockRule(ApiMockRule.builder().match(r -> true).respond(r -> null).build()).send());
    }
    @Test public void unmatchedRuleUsesTransport() throws Exception {
        String url=server(this::reply); assertFalse(ApiClient.get(url).mockRule(ApiMockRule.builder().match(r -> false).respond(r -> null).build()).send().isSynthetic());
    }
    @Test public void durationTimeoutAndValidation() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> ApiClient.get("http://localhost:1/").requestTimeout(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> ApiClient.get("http://localhost:1/").timeout(-1));
        ApiResponse mock=ApiClient.get("http://localhost:1/").timeout(9).requestTimeout(Duration.ofMillis(50)).mockRule(rule(200)).send();
        assertEquals(mock.request().timeout(),Duration.ofMillis(50));
        String url=server(e -> { try { new CountDownLatch(1).await(500,TimeUnit.MILLISECONDS); reply(e); } catch (Exception ignored) {} });
        assertThrows(CompletionException.class, () -> ApiClient.get(url).requestTimeout(Duration.ofMillis(30)).sendAsync().join());
    }
    @Test public void transportLimitAndMockBypass() throws Exception {
        config.getApi().setMaxConcurrentRequests(1); CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger active=new AtomicInteger(),maximum=new AtomicInteger();
        String url=server(e -> { int count=active.incrementAndGet(); maximum.accumulateAndGet(count,Math::max); entered.countDown(); try { release.await(3,TimeUnit.SECONDS); reply(e); } catch(Exception ignored) {} finally { active.decrementAndGet(); } });
        var first=ApiClient.get(url).sendAsync(); assertTrue(entered.await(2,TimeUnit.SECONDS));
        var second=ApiClient.get(url).sendAsync(); assertEquals(ApiClient.get(url).mockRule(rule(203)).sendAsync().get(1,TimeUnit.SECONDS).status(),203);
        assertTrue(second.cancel(true)); release.countDown(); assertEquals(first.get(2,TimeUnit.SECONDS).status(),200);
        assertEquals(ApiClient.get(url).send().status(),200); assertEquals(maximum.get(),1);
    }
    private Path store(String hostname) throws Exception {
        directory=Files.createTempDirectory("testfly-tls-"); Path store=directory.resolve("server.p12");
        String keytool=Path.of(System.getProperty("java.home"),"bin","keytool").toString();
        Process process=new ProcessBuilder(keytool,"-genkeypair","-alias","server","-keyalg","RSA","-keysize","2048","-validity","2","-dname","CN="+hostname,"-ext","SAN=dns:"+hostname,"-storetype","PKCS12","-keystore",store.toString(),"-storepass",new String(PASSWORD),"-keypass",new String(PASSWORD)).redirectErrorStream(true).start();
        String output=new String(process.getInputStream().readAllBytes()); assertEquals(process.waitFor(),0,output); return store;
    }
    private String https(Path store) throws Exception {
        KeyStore keys=KeyStore.getInstance("PKCS12"); try(var input=Files.newInputStream(store)){keys.load(input,PASSWORD);}
        KeyManagerFactory km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());km.init(keys,PASSWORD);
        SSLContext ssl=SSLContext.getInstance("TLS");ssl.init(km.getKeyManagers(),null,null);
        HttpsServer https=HttpsServer.create(new InetSocketAddress("127.0.0.1",0),0);server=https;
        https.setHttpsConfigurator(new HttpsConfigurator(ssl));executor=Executors.newVirtualThreadPerTaskExecutor();https.setExecutor(executor);https.createContext("/",this::reply);https.start();return "https://localhost:"+https.getAddress().getPort();
    }
    @Test public void sslDefaultTruststoreAndTrustAll() throws Exception {
        Path store=store("localhost");String url=https(store);
        assertThrows(ApiException.class, () -> ApiClient.get(url).send());
        assertEquals(ApiClient.get(url).trustStore(store,PASSWORD).send().status(),200);
        assertEquals(ApiClient.get(url).trustAllCerts().sendAsync().get(3,TimeUnit.SECONDS).status(),200);
        var a=ApiTransport.prepare(ApiExecution.scope(),config.getApi(),ApiTransport.SslSelection.store(store,PASSWORD,"PKCS12"));
        var b=ApiTransport.prepare(ApiExecution.scope(),config.getApi(),ApiTransport.SslSelection.store(store,PASSWORD,"PKCS12"));assertSame(a.client(),b.client());
        ApiExecution.cleanupTestContext(); assertTrue(a.client().awaitTermination(Duration.ofSeconds(3)));
    }
    @Test public void jksAndDefensivePasswordCopy() throws Exception {
        Path original = store("localhost"); String url = https(original);
        KeyStore source = KeyStore.getInstance("PKCS12");
        try (var input = Files.newInputStream(original)) { source.load(input, PASSWORD); }
        KeyStore trust = KeyStore.getInstance("JKS"); trust.load(null, null);
        trust.setCertificateEntry("server", source.getCertificate("server"));
        Path jks = directory.resolve("trust.jks");
        try (var output = Files.newOutputStream(jks)) { trust.store(output, PASSWORD); }
        char[] callerPassword = PASSWORD.clone();
        ApiClient request = ApiClient.get(url).trustStore(jks, callerPassword, "JKS");
        Arrays.fill(callerPassword, 'x');
        assertEquals(request.send().status(), 200);
        assertThrows(IllegalArgumentException.class, () -> ApiClient.get(url).trustStore(jks, PASSWORD, "unknown"));
    }
    @Test public void mockRulesAreIsolatedBetweenTests() throws Exception {
        List<CompletableFuture<Integer>> results = new ArrayList<>();
        CountDownLatch ready = new CountDownLatch(2);
        for (int status : List.of(201, 202)) {
            CompletableFuture<Integer> result = new CompletableFuture<>(); results.add(result);
            Thread.ofVirtual().start(() -> {
                try {
                    ApiClient.addMockRule(rule(status)); ready.countDown();
                    if (!ready.await(2, TimeUnit.SECONDS)) throw new AssertionError("Parallel setup timed out");
                    result.complete(ApiClient.get("http://localhost:1/").sendAsync().join().status());
                } catch (Throwable error) { result.completeExceptionally(error); }
                finally { ApiExecution.cleanupTestContext(); }
            });
        }
        assertEquals(results.get(0).get(3, TimeUnit.SECONDS), Integer.valueOf(201));
        assertEquals(results.get(1).get(3, TimeUnit.SECONDS), Integer.valueOf(202));
    }
    @Test public void hostnameMismatchFailsInAllModes() throws Exception {
        Path store=store("wrong.example");String url=https(store);
        assertThrows(ApiException.class, () -> ApiClient.get(url).send());
        assertThrows(ApiException.class, () -> ApiClient.get(url).trustStore(store,PASSWORD).send());
        assertThrows(ApiException.class, () -> ApiClient.get(url).trustAllCerts().send());
    }
    @Test public void badTruststoreAndYamlOverride() throws Exception {
        Path store=store("localhost");String url=https(store);
        assertThrows(IllegalArgumentException.class, () -> ApiClient.get(url).trustStore(store,"wrong".toCharArray()).send());
        assertTrue(ApiClient.get(url).trustStore(directory.resolve("absent"),PASSWORD).sendAsync().isCompletedExceptionally());
        assertTrue(ApiClient.get(url).trustStore(store,PASSWORD).trustAllCerts().sendAsync().isCompletedExceptionally());
        var ssl=config.getApi().getSsl(); ssl.setTrustAll(true);var ts=new TestFlyConfig.Api.TrustStore();ts.setPath(store.toString());ssl.setTrustStore(ts);
        assertThrows(IllegalArgumentException.class, () -> ApiClient.get(url).send());
        assertEquals(ApiClient.get(url).trustAllCerts().send().status(),200);
    }
}
