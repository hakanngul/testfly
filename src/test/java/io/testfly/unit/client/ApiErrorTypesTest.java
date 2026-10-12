package io.testfly.unit.client;

import io.testfly.client.ApiAssertionError;
import io.testfly.client.ApiClient;
import io.testfly.client.ApiException;
import io.testfly.client.ApiFailure;
import io.testfly.client.ApiResponse;
import io.testfly.client.ApiResponseSpec;
import org.testng.annotations.Test;

import java.util.Map;

import static org.testng.Assert.*;

@Test(singleThreaded = true)
public class ApiErrorTypesTest extends ApiClientTestBase {

    @Test
    public void assertionFailuresExposeRequestContext() throws Exception {
        String base = server(ex -> reply(ex, 200, "{\"id\":1}", Map.of("Content-Type", "application/json")));
        ApiResponse res = ApiClient.get(base + "/users/1").send();

        ApiException statusError = expectThrows(ApiException.class, () -> res.assertStatus(201));
        assertTrue(statusError instanceof ApiFailure);
        assertEquals(statusError.getStatus(), 200);
        assertEquals(statusError.getMethod(), "GET");
        assertEquals(statusError.getUrl(), base + "/users/1");
    }

    @Test
    public void responseSpecContentTypeFailureNamesTheRequest() throws Exception {
        String base = server(ex -> reply(ex, 200, "plain", Map.of("Content-Type", "text/plain")));
        ApiResponse res = ApiClient.get(base + "/text").send();

        ApiException error = expectThrows(ApiException.class,
                () -> res.expect(ApiResponseSpec.expect().contentType("application/json").build()));
        assertEquals(error.getMethod(), "GET");
        assertTrue(error.getMessage().contains("GET " + base + "/text"), error.getMessage());
        assertFalse(error.getMessage().contains("null null"), error.getMessage());
    }

    @Test
    public void schemaFailureIsAssertionErrorWithApiContext() throws Exception {
        String base = server(ex -> reply(ex, 200, "{\"id\":\"one\"}", Map.of("Content-Type", "application/json")));
        ApiResponse res = ApiClient.get(base + "/users/1").send();

        AssertionError error = expectThrows(AssertionError.class, () -> res.assertSchema("schemas/api-user.schema.json"));
        assertTrue(error instanceof ApiAssertionError);
        ApiFailure failure = (ApiFailure) error;
        assertEquals(failure.getStatus(), 200);
        assertEquals(failure.getMethod(), "GET");
        assertEquals(failure.getUrl(), base + "/users/1");
        assertTrue(error.getMessage().contains("Schema validation failed"), error.getMessage());
    }

    @Test
    public void schemaValidationPassesAndMissingSchemaIsReported() throws Exception {
        String base = server(ex -> reply(ex, 200, "{\"id\":1,\"name\":\"Ada\"}", Map.of("Content-Type", "application/json")));
        ApiResponse res = ApiClient.get(base + "/users/1").send();

        assertSame(res.assertSchema("schemas/api-user.schema.json"), res);
        IllegalArgumentException missing = expectThrows(IllegalArgumentException.class,
                () -> res.assertSchema("schemas/does-not-exist.json"));
        assertTrue(missing.getMessage().contains("not found"), missing.getMessage());
    }

    @Test
    public void messageWithoutRequestContextOmitsNullPlaceholders() {
        ApiAssertionError error = new ApiAssertionError(null, null, 500, "boom", "failed");
        assertFalse(error.getMessage().contains("null null"), error.getMessage());
        assertTrue(error.getMessage().startsWith("failed"), error.getMessage());
    }
}
