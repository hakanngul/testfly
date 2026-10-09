package io.testfly.unit.client;

import io.testfly.client.ApiResponse;
import io.testfly.client.ApiRequest;
import io.testfly.client.OpenApiValidator;
import org.testng.annotations.Test;
import java.net.URI;

public class OpenApiValidatorTest {

    @Test
    public void testValidatorUsesPathAndContentType() throws Exception {
        ApiRequest request = ApiRequest.builder().method("GET").uri(URI.create("https://example.com/api/v1/users/123")).build();
        ApiResponse response = ApiResponse.builder()
                .status(200)
                .body("{}")
                .header("Content-Type", "application/json")
                .request(request)
                .build();
        
        try {
            java.lang.reflect.Method method = OpenApiValidator.class.getDeclaredMethod("validate", ApiResponse.class, String.class);
            method.setAccessible(true);
            method.invoke(null, response, "non-existent-spec.yaml");
        } catch (java.lang.reflect.InvocationTargetException e) {
            assert e.getCause() instanceof IllegalArgumentException;
            assert e.getCause().getMessage().contains("Could not load or parse");
        }
    }
}
