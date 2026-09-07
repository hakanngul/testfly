package io.testfly.unit;

import io.testfly.ai.OpenAiCompatibleProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class OpenAiCompatibleProviderTest {

    @Test
    public void extractContent_parsesDeepSeekResponse() {
        String json = "{"
            + "\"id\":\"83dbda35\","
            + "\"model\":\"deepseek-v4-flash\","
            + "\"choices\":[{\"message\":{\"role\":\"assistant\","
            + "\"content\":\"**Root Cause:** Element not found.\\n\\n**Suggested Fix:**\\n- Add explicit wait\"}}]"
            + "}";
        String result = OpenAiCompatibleProvider.extractContent(json);
        assertNotNull(result);
        assertTrue(result.contains("Root Cause"));
        assertTrue(result.contains("Suggested Fix"));
    }

    @Test
    public void extractContent_parsesGeminiResponse() {
        String json = "{\"choices\":[{\"message\":{\"content\":\"Analysis complete.\"}}]}";
        String result = OpenAiCompatibleProvider.extractContent(json);
        assertEquals(result, "Analysis complete.");
    }

    @Test
    public void extractContent_nullContent_returnsNull() {
        String json = "{\"choices\":[{\"message\":{\"content\":null}}]}";
        assertNull(OpenAiCompatibleProvider.extractContent(json));
    }

    @Test
    public void buildEndpointUrl_handlesTokenPlanAndStandardUrls() {
        // Alibaba Cloud Token Plan baseUrl ending in /v1
        assertEquals(
                OpenAiCompatibleProvider.buildEndpointUrl("https://token-plan.ap-southeast-1.maas.aliyuncs.com/compatible-mode/v1"),
                "https://token-plan.ap-southeast-1.maas.aliyuncs.com/compatible-mode/v1/chat/completions"
        );
        // BaseUrl ending with trailing slash
        assertEquals(
                OpenAiCompatibleProvider.buildEndpointUrl("https://token-plan.ap-southeast-1.maas.aliyuncs.com/compatible-mode/v1/"),
                "https://token-plan.ap-southeast-1.maas.aliyuncs.com/compatible-mode/v1/chat/completions"
        );
        // Standard baseUrl without /v1
        assertEquals(
                OpenAiCompatibleProvider.buildEndpointUrl("https://api.deepseek.com"),
                "https://api.deepseek.com/v1/chat/completions"
        );
        // Fully specified chat/completions url
        assertEquals(
                OpenAiCompatibleProvider.buildEndpointUrl("https://custom.host/v1/chat/completions"),
                "https://custom.host/v1/chat/completions"
        );
    }

    @Test
    public void extractContent_emptyJson_returnsNull() {
        assertNull(OpenAiCompatibleProvider.extractContent("{}"));
    }
}
