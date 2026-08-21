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
    public void extractContent_emptyJson_returnsNull() {
        assertNull(OpenAiCompatibleProvider.extractContent("{}"));
    }
}
