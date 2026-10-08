package io.testfly.unit.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.testfly.client.ApiRequest;
import io.testfly.client.ApiResponse;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * Loads a real YAML OpenAPI document through the Jackson YAML + swagger-parser stack
 * so a Jackson module version skew (BLD-007) fails here instead of in consumer projects.
 */
public class OpenApiYamlLoadingTest {

    private static final String SPEC = """
            openapi: 3.0.3
            info:
              title: Users API
              version: 1.0.0
            paths:
              /api/users/{id}:
                get:
                  parameters:
                    - name: id
                      in: path
                      required: true
                      schema:
                        type: integer
                  responses:
                    '200':
                      description: A user
                      content:
                        application/json:
                          schema:
                            type: object
                            required: [id, name]
                            properties:
                              id:
                                type: integer
                              name:
                                type: string
            """;

    private Path specFile;

    @BeforeClass
    public void writeSpec() throws Exception {
        specFile = Files.createTempFile("testfly-openapi-", ".yaml");
        Files.writeString(specFile, SPEC, StandardCharsets.UTF_8);
    }

    @AfterClass(alwaysRun = true)
    public void deleteSpec() throws Exception {
        if (specFile != null) {
            Files.deleteIfExists(specFile);
        }
    }

    @Test
    public void yamlMapperParsesOpenApiDocument() throws Exception {
        JsonNode root = new ObjectMapper(new YAMLFactory()).readTree(SPEC);

        assertEquals(root.path("openapi").asText(), "3.0.3");
        assertTrue(root.path("paths").has("/api/users/{id}"));
    }

    @Test
    public void assertOpenApiAcceptsConformingResponse() {
        ApiResponse response = response(200, "{\"id\":7,\"name\":\"Ada\"}");

        response.assertOpenApi(specFile.toString());
    }

    @Test
    public void assertOpenApiRejectsNonConformingResponse() {
        ApiResponse response = response(200, "{\"id\":7}");

        assertThrows(AssertionError.class, () -> response.assertOpenApi(specFile.toString()));
    }

    private static ApiResponse response(int status, String body) {
        ApiRequest request = ApiRequest.builder()
                .method("GET")
                .uri(URI.create("https://example.com/api/users/7"))
                .build();
        return ApiResponse.builder()
                .status(status)
                .body(body)
                .header("Content-Type", "application/json")
                .request(request)
                .build();
    }
}
