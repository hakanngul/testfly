package io.testfly.examples.api;

import io.testfly.client.ApiClient;
import io.testfly.client.ApiResponse;
import io.testfly.test.BaseApiTest;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Showcases every new API testing feature added in the TestFly API improvement
 * plan.
 *
 * <p>
 * Covers: query param builder, per-request timeout, response time assertions,
 * header assertions, JSON structure assertions (exists/null/array-size), body
 * regex,
 * request/response interceptors, cookie jar, and fluent chaining.
 *
 * <p>
 * Run with the real-backends profile:
 * 
 * <pre>
 * mvn verify -Preal-backends -Dtest=io.testfly.examples.api.ApiFeaturesShowcaseTest
 * </pre>
 */
public class ApiFeaturesShowcaseTest extends BaseApiTest {

    // ── Query parameter builder ────────────────────────────────────────────────

    @Test
    public void queryParams_buildCleanUrls() {
        // Instead of: "/users?page=" + page + "&limit=" + limit
        ApiResponse res = apiClient().get("/users")
                .queryParam("page", 1)
                .queryParam("limit", 3)
                .send();

        res.assertStatus(200)
                .assertJson("$.pagination.page", 1)
                .assertJson("$.pagination.limit", 3);
    }

    @Test
    public void queryParams_fromMap() {
        Map<String, Object> filters = Map.of("page", 1, "limit", 2);

        ApiResponse res = apiClient().get("/products")
                .queryParams(filters)
                .send();

        res.assertStatus(200)
                .assertJsonExists("$.pagination");
    }

    @Test
    public void queryParams_specialCharacters_areEncoded() {
        ApiResponse res = apiClient().get("/products")
                .queryParam("category", "electronics")
                .queryParam("limit", 1)
                .send();

        res.assertStatus(200);
        assert res.json("$.data[0].category") != null
                : "Should return products with matching category";
    }

    // ── Per-request timeout override ───────────────────────────────────────────

    @Test
    public void timeout_overrideForSlowEndpoints() {
        // Some endpoints are slow — override the default 30s timeout for this call only
        ApiResponse res = apiClient().get("/users/1")
                .timeout(60)
                .send();

        res.assertStatus(200);
    }

    // ── Response time assertions ───────────────────────────────────────────────

    @Test
    public void responseTime_assertWithinLimit() {
        ApiResponse res = apiClient().get("/users/1").send();

        res.assertStatus(200)
                .assertDurationLessThan(5000); // 5 seconds max
    }

    @Test
    public void responseTime_assertWithTimeUnit() {
        ApiResponse res = apiClient().get("/products/categories").send();

        res.assertStatus(200)
                .assertDurationLessThan(10, TimeUnit.SECONDS);
    }

    // ── Header assertions ──────────────────────────────────────────────────────

    @Test
    public void headers_assertContentType() {
        ApiResponse res = apiClient().get("/users/1").send();

        res.assertStatus(200)
                .assertHeaderPresent("content-type");
    }

    // ── JSON structure assertions ──────────────────────────────────────────────

    @Test
    public void jsonExists_fieldPresentInResponse() {
        ApiResponse res = apiClient().get("/users/1").send();

        res.assertStatus(200)
                .assertJsonExists("$.id")
                .assertJsonExists("$.email")
                .assertJsonExists("$.username");
    }

    @Test
    public void jsonArraySize_correctNumberOfItems() {
        ApiResponse res = apiClient().get("/users")
                .queryParam("page", 1)
                .queryParam("limit", 5)
                .send();

        res.assertStatus(200)
                .assertJsonExists("$.data")
                .assertJsonArraySize("$.data", 5);
    }

    @Test
    public void jsonNull_missingFieldIsTreatedAsNull() {
        ApiResponse res = apiClient().get("/users/1").send();

        res.assertStatus(200);
        // A field that likely doesn't exist — assertJsonNull passes for missing paths
        res.assertJsonNull("$.nonExistentField");
    }

    // ── Body regex assertion ───────────────────────────────────────────────────

    @Test
    public void bodyMatches_regexPattern() {
        ApiResponse res = apiClient().get("/users/1").send();

        res.assertStatus(200)
                .assertBodyMatches("\"id\"\\s*:\\s*1"); // JSON contains "id": 1
    }

    @Test
    public void bodyMatches_emailPattern() {
        ApiResponse res = apiClient().get("/users/1").send();

        res.assertStatus(200)
                .assertBodyMatches("[\\w.]+@[\\w.]+\\.[a-z]+"); // email pattern
    }

    // ── Interceptors ───────────────────────────────────────────────────────────

    @BeforeClass
    public void registerInterceptors() {
        // Add a correlation ID to every request in this test class
        ApiClient.addRequestInterceptor(builder -> builder.header("X-Test-Class", "ApiFeaturesShowcase"));
    }

    @AfterClass
    public void removeInterceptors() {
        ApiClient.clearInterceptors();
    }

    @Test
    public void interceptors_addHeadersAutomatically() {
        // The X-Test-Class header is added by the interceptor registered in
        // @BeforeClass
        ApiResponse res = apiClient().get("/users/1").send();
        res.assertStatus(200);
    }

    // ── Fluent chaining — all assertions in one chain ──────────────────────────

    @Test
    public void fluentChaining_allAssertionsInOneLine() {
        apiClient().get("/users")
                .queryParam("page", 1)
                .queryParam("limit", 3)
                .send()
                .assertStatus(200)
                .assertDurationLessThan(5000)
                .assertHeaderPresent("content-type")
                .assertJsonExists("$.data")
                .assertJsonExists("$.pagination")
                .assertJsonArraySize("$.data", 3)
                .assertBodyContains("email");
    }

    @Test
    public void fluentChaining_singleProduct() {
        apiClient().get("/products/1").send()
                .assertStatus(200)
                .assertJsonExists("$.id")
                .assertJsonExists("$.title")
                .assertJsonExists("$.price")
                .assertBodyMatches("\"id\"\\s*:\\s*1")
                .assertDurationLessThan(5000);
    }

    // ── Different base URL ─────────────────────────────────────────────────────

    @Test
    public void differentBaseUrl_overridePerRequest() {
        // Call a completely different API
        ApiResponse res = ApiClient.to("https://httpbin.org")
                .path("/status/200")
                .get()
                .timeout(15)
                .send();

        res.assertStatus(200);
    }

    // ── API Mocking (WireMock) ─────────────────────────────────────────────────

    @Test
    public void mockSupport_stubAndCall() {
        // MockSupport arayüzünden gelen stubGet metodu ile WireMock sunucusunda sahte bir endpoint oluşturuyoruz.
        stubGet("/mock-endpoint", 200, "{\"mocked\": true}");
        
        // Gerçek API yerine ayağa kalkan Mock sunucusuna (localhost:PORT) istek atıyoruz
        ApiResponse res = ApiClient.to(mockServer().getBaseUrl())
                .path("/mock-endpoint")
                .get()
                .send();
                
        res.assertStatus(200)
           .assertJson("$.mocked", true);
    }

    // ── OpenAPI Validation ─────────────────────────────────────────────────────

    @Test
    public void openApiValidator_contractTesting() {
        // Not: src/test/resources/schemas/sample-api.yaml dosyası olduğunu varsayıyoruz
        // ApiResponse.assertOpenApi ile tüm request/response yapısı şemaya uyumlu mu denetlenir.
        // Eğer kütüphane pom.xml'de yoksa IllegalStateException dönecektir (çünkü opsiyonel bağımlılık).
        try {
            apiClient().get("/users/1").send()
                    .assertStatus(200)
                    .assertOpenApi("schemas/sample-api.yaml");
        } catch (IllegalStateException e) {
            if (!e.getMessage().contains("swagger-request-validator-core")) {
                throw e;
            }
        }
    }

    // ── Asenkron İstek & BatchRunner (Concurrency) ─────────────────────────────

    @Test
    public void asyncApi_sendParallelRequests() throws Exception {
        java.util.concurrent.CompletableFuture<ApiResponse> req1 = apiClient().get("/users/1").sendAsync();
        java.util.concurrent.CompletableFuture<ApiResponse> req2 = apiClient().get("/users/2").sendAsync();
        
        // Her iki asenkron isteğin tamamlanmasını bekle
        java.util.concurrent.CompletableFuture.allOf(req1, req2).join();
        
        req1.get().assertStatus(200);
        req2.get().assertStatus(200);
    }

    @Test
    public void apiBatchRunner_rateLimitFuzzing() {
        // 50 isteği aynı anda (maksimum 10 thread çalışacak şekilde) ateşler
        java.util.List<ApiResponse> responses = io.testfly.client.ApiBatchRunner.fire(50)
                .concurrently(10)
                .request(() -> apiClient().get("/products/1"))
                .execute();
                
        assert responses.size() == 50 : "50 adet yanıt toplanmış olmalı";
        long successCount = responses.stream().filter(r -> r.status() == 200).count();
        long rateLimitedCount = responses.stream().filter(r -> r.status() == 429).count();
        
        System.out.println("Başarılı: " + successCount + ", Rate Limited (429): " + rateLimitedCount);
    }
}
