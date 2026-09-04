package io.testfly.unit.ai;

import io.testfly.ai.AiProvider;
import io.testfly.ai.AiProviderRegistry;
import io.testfly.ai.GeminiProvider;
import org.testng.Assert;
import org.testng.annotations.Test;

public class GeminiProviderTest {

    @Test
    public void providerName_isGemini() {
        GeminiProvider provider = new GeminiProvider();
        Assert.assertEquals(provider.name(), "gemini");
    }

    @Test
    public void registry_returnsGeminiProvider() {
        AiProvider provider = AiProviderRegistry.get("gemini", null);
        Assert.assertNotNull(provider);
        Assert.assertTrue(provider instanceof GeminiProvider);
        Assert.assertEquals(provider.name(), "gemini");
    }

    @Test
    public void buildRequestBody_escapesJsonCorrectly() {
        String prompt = "Failure: \"Element not found\"\nDetails on line 2\twith tab";
        String body = GeminiProvider.buildRequestBody(prompt);

        Assert.assertTrue(body.contains("\"maxOutputTokens\":512"));
        Assert.assertTrue(body.contains("\\\"Element not found\\\""));
        Assert.assertTrue(body.contains("\\nDetails on line 2"));
        Assert.assertTrue(body.contains("\\twith tab"));
    }

    @Test
    public void extractContent_validGeminiResponse_returnsText() {
        String json = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "**Root Cause:** The element timed out.\\n**Suggested Fix:** Check the locator."
                      }
                    ],
                    "role": "model"
                  },
                  "finishReason": "STOP"
                }
              ]
            }
            """;

        String text = GeminiProvider.extractContent(json);
        Assert.assertNotNull(text);
        Assert.assertTrue(text.contains("**Root Cause:** The element timed out."));
        Assert.assertTrue(text.contains("**Suggested Fix:** Check the locator."));
    }

    @Test
    public void extractContent_withEscapedQuotesAndCharacters() {
        String json = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Line 1 with \\\"quotes\\\" and \\\\ slash\"}]}}]}";
        String text = GeminiProvider.extractContent(json);
        Assert.assertEquals(text, "Line 1 with \"quotes\" and \\ slash");
    }

    @Test
    public void extractContent_emptyOrInvalid_returnsNull() {
        Assert.assertNull(GeminiProvider.extractContent(null));
        Assert.assertNull(GeminiProvider.extractContent(""));
        Assert.assertNull(GeminiProvider.extractContent("{}"));
        Assert.assertNull(GeminiProvider.extractContent("{\"candidates\":[]}"));
        Assert.assertNull(GeminiProvider.extractContent("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"   \"}]}}]}"));
    }

    @Test
    public void call_invalidApiKey_failsGracefullyWithoutThrowing() {
        GeminiProvider provider = new GeminiProvider();
        // Should not throw, returns null on failure
        String result = provider.call("invalid-key", "gemini-2.0-flash", "test prompt", 1);
        Assert.assertNull(result);
    }
}
