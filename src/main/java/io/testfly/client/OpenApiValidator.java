package io.testfly.client;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.SimpleRequest;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.ValidationReport;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Validates API interactions against an OpenAPI 3.x specification.
 * Requires {@code com.atlassian.oai:swagger-request-validator-core} on the classpath.
 */
public class OpenApiValidator {

    private static final Map<String, OpenApiInteractionValidator> VALIDATOR_CACHE = new ConcurrentHashMap<>();

    static void validate(ApiResponse response, String specPath) {
        ensureValidatorAvailable();

        OpenApiInteractionValidator validator = VALIDATOR_CACHE.computeIfAbsent(specPath, path -> {
            try {
                return OpenApiInteractionValidator.createFor(path).build();
            } catch (Exception e) {
                throw new IllegalArgumentException("[OpenApiValidator] Could not load or parse OpenAPI spec: " + path, e);
            }
        });

        // Reconstruct a SimpleRequest from the response context
        SimpleRequest.Builder reqBuilder = new SimpleRequest.Builder(
                response.requestMethod(), response.requestUrl());
        
        // Atlassian's validator needs the response to validate
        SimpleResponse.Builder resBuilder = SimpleResponse.Builder.status(response.status());
        String contentType = response.header("Content-Type");
        if (contentType != null) {
            resBuilder.withContentType(contentType);
        }
        if (response.body() != null) {
            resBuilder.withBody(response.body());
        }
        
        // We only have the response headers available via ApiResponse right now.
        // If we wanted to validate the request fully, we'd need to pass the ApiClient's request state too.
        // But for response validation, this is sufficient.
        // To do full contract testing, we should pass headers. But for simplicity, we just pass body/status.
        
        ValidationReport report = validator.validateResponse(
                java.net.URI.create(response.requestUrl()).getPath(), 
                com.atlassian.oai.validator.model.Request.Method.valueOf(response.requestMethod().toUpperCase()), 
                resBuilder.build()
        );

        if (report.hasErrors()) {
            String details = report.getMessages().stream()
                    .map(msg -> msg.getMessage())
                    .collect(Collectors.joining("\n  - ", "\n  - ", ""));
            throw new AssertionError("[ApiResponse] OpenAPI validation failed against '" + specPath + "':" + details);
        }
    }

    private static void ensureValidatorAvailable() {
        try {
            Class.forName("com.atlassian.oai.validator.OpenApiInteractionValidator");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(
                    "[OpenApiValidator] assertOpenApi() requires 'swagger-request-validator-core' on the classpath. "
                    + "Add it to your pom.xml:\n"
                    + "  <dependency>\n"
                    + "    <groupId>com.atlassian.oai</groupId>\n"
                    + "    <artifactId>swagger-request-validator-core</artifactId>\n"
                    + "    <version>2.39.0</version>\n"
                    + "  </dependency>");
        }
    }
}
