package io.testfly.unit.client;

import io.testfly.client.ApiClient;
import io.testfly.client.ApiRequestSpec;
import io.testfly.internal.api.ApiExecution;
import org.testng.annotations.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import static org.testng.Assert.*;

@Test(singleThreaded = true)
public class ApiSpecScopingTest extends ApiClientTestBase {

    private final Map<String, String> seen = new ConcurrentHashMap<>();

    private String echoServer() throws Exception {
        return server(ex -> {
            seen.clear();
            ex.getRequestHeaders().forEach((k, v) -> seen.put(k.toLowerCase(), v.get(0)));
            if (ex.getRequestURI().getQuery() != null) seen.put("query", ex.getRequestURI().getQuery());
            reply(ex, 200, "ok");
        });
    }

    @Test
    public void testSpecAppliesOnlyToCurrentThreadAndIsClearedAfterTest() throws Exception {
        String base = echoServer();
        ApiClient.setTestSpec(ApiRequestSpec.builder().header("X-Scope", "test").queryParam("tenant", "t1").build());

        ApiClient.get(base + "/a").send();
        assertEquals(seen.get("x-scope"), "test");
        assertEquals(seen.get("query"), "tenant=t1");

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            executor.submit(() -> ApiClient.get(base + "/other-thread").send()).get();
        }
        assertNull(seen.get("x-scope"), "test spec must not leak to another thread");

        ApiExecution.cleanupTestContext();
        ApiClient.get(base + "/b").send();
        assertNull(seen.get("x-scope"), "test spec must be cleared after the test");
    }

    @Test
    public void explicitValuesWinOverTestSpecWhichWinsOverGlobalSpec() throws Exception {
        String base = echoServer();
        ApiClient.setGlobalSpec(ApiRequestSpec.builder().header("X-A", "global").header("X-B", "global").header("X-C", "global").build());
        ApiClient.setTestSpec(ApiRequestSpec.builder().header("X-A", "test").header("X-B", "test").build());

        ApiClient.get(base + "/a").header("X-A", "explicit").send();

        assertEquals(seen.get("x-a"), "explicit");
        assertEquals(seen.get("x-b"), "test");
        assertEquals(seen.get("x-c"), "global");
    }

    @Test
    public void globalSpecIsJvmGlobalAndSurvivesTestCleanup() throws Exception {
        String base = echoServer();
        ApiClient.setGlobalSpec(ApiRequestSpec.builder().header("X-Global", "yes").bearerToken("g").build());

        ApiExecution.cleanupTestContext();
        ApiClient.get(base + "/a").send();

        assertEquals(seen.get("x-global"), "yes");
        assertEquals(seen.get("authorization"), "Bearer g");
    }

    @Test
    public void explicitSpecOverwritesClientValues() throws Exception {
        String base = echoServer();
        ApiRequestSpec spec = ApiRequestSpec.builder().baseUrl(base).header("Accept", "application/json").contentType("text/plain").build();

        ApiClient.get("/p").spec(spec).send();

        assertEquals(seen.get("accept"), "application/json");
        assertEquals(seen.get("content-type"), "text/plain");
    }
}
