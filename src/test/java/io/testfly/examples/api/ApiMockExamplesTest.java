package io.testfly.examples.api;

import io.testfly.client.*;
import io.testfly.internal.api.ApiExecution;
import org.testng.annotations.*;

/** Run with mvn test -Dtest=ApiMockExamplesTest; no external service required. */
@Test(singleThreaded = true)
public class ApiMockExamplesTest {
    private ApiMockRule payment() {
        return ApiMockRule.builder()
            .match(request -> request.method().equals("POST") && request.uri().getPath().equals("/payment"))
            .respond(request -> ApiResponse.builder().request(request).status(200)
                .header("Content-Type", "application/json").body("{\"status\":\"mocked\"}").build()).build();
    }
    @AfterMethod public void cleanup() { ApiExecution.cleanupTestContext(); }
    @Test public void requestScopedMock() {
        ApiClient.post("http://localhost:1/payment").mockRule(payment()).send().assertStatus(200).assertJson("$.status", "mocked");
    }
    @Test public void testScopedAsyncMock() {
        ApiClient.addMockRule(payment());
        ApiClient.post("http://localhost:1/payment").sendAsync().join().assertStatus(200).assertJson("$.status", "mocked");
    }
}
