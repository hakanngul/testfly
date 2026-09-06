package io.testfly.ai.remediation;

import io.testfly.ai.AiFailureAnalyzer;
import io.testfly.ai.AiProvider;
import io.testfly.ai.AiProviderRegistry;
import io.testfly.api.TestFlyApi;
import io.testfly.config.TestFlyConfig;
import io.testfly.internal.TestFlyContext;
import io.testfly.metrics.TestTiming;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.logging.Logger;

/**
 * Generates unified git diff patches (.patch) for failed tests using LLM intelligence.
 *
 * <p>Produces non-destructive patches saved to {@code target/remediations/{TestName}.patch}
 * which can be inspected by developers or applied via {@code git apply}.
 */
@TestFlyApi(since = "1.9.0")
public final class RemediationPatchGenerator {

    private static final Logger LOG = Logger.getLogger(RemediationPatchGenerator.class.getName());

    private RemediationPatchGenerator() {}

    /**
     * Generates a unified diff patch for a failing test and writes it to disk.
     *
     * @param testId    identifier of the failed test
     * @param snippet   source code context resolved by {@link SourceCodeLocator}
     * @param timing    metrics and error context of the failed test
     * @param pageUrl   current page URL at failure
     * @param pageTitle current page title at failure
     * @return the generated {@link File} patch, or {@code null} if generation failed
     */
    public static File generateAndSave(String testId, SourceCodeLocator.SourceSnippet snippet, TestTiming timing, String pageUrl, String pageTitle) {
        if (snippet == null || timing == null) {
            return null;
        }

        try {
            TestFlyConfig config = TestFlyContext.getConfig();
            if (config == null || config.getAi() == null) {
                return null;
            }

            TestFlyConfig.Ai aiCfg = config.getAi();
            if (!aiCfg.isGeneratePatch()) {
                return null;
            }

            String apiKey = AiFailureAnalyzer.resolveApiKey(aiCfg.getApiKey());
            if (apiKey == null || apiKey.isBlank()) {
                LOG.fine("[RemediationPatchGenerator] ai.apiKey is not configured. Skipping patch generation.");
                return null;
            }

            AiProvider provider = AiProviderRegistry.get(aiCfg.getProvider(), aiCfg.getBaseUrl());
            if (provider == null) {
                LOG.warning("[RemediationPatchGenerator] Unknown ai.provider: " + aiCfg.getProvider());
                return null;
            }

            String prompt = buildPatchPrompt(testId, snippet, timing, pageUrl, pageTitle);
            String response = provider.call(apiKey, aiCfg.getModel(), prompt, aiCfg.getTimeoutSeconds());

            if (response == null || response.isBlank()) {
                LOG.fine("[RemediationPatchGenerator] Empty patch response from AI provider.");
                return null;
            }

            String cleanPatch = sanitizePatch(response);
            if (cleanPatch.isBlank() || !cleanPatch.contains("@@")) {
                LOG.fine("[RemediationPatchGenerator] AI response did not contain a valid unified diff.");
                return null;
            }

            File outDir = new File("target", "remediations");
            outDir.mkdirs();

            String safeName = sanitizeFileName(testId) + ".patch";
            File patchFile = new File(outDir, safeName);
            Files.writeString(patchFile.toPath(), cleanPatch, StandardCharsets.UTF_8);

            LOG.info("[RemediationPatchGenerator] Patch generated: " + patchFile.getPath());
            return patchFile;

        } catch (Exception e) {
            LOG.warning("[RemediationPatchGenerator] Patch generation failed (non-critical): " + e.getMessage());
            return null;
        }
    }

    /**
     * Builds the patch generation prompt for the LLM.
     */
    public static String buildPatchPrompt(String testId, SourceCodeLocator.SourceSnippet snippet, TestTiming timing, String pageUrl, String pageTitle) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an expert QA automation engineer repairing automated tests.\n");
        sb.append("A Selenium/TestFly test has failed. Generate a Unified Diff (git diff) patch to fix the issue.\n\n");

        sb.append("## Test Failure Context\n");
        sb.append("- Test: ").append(testId).append("\n");
        if (pageUrl != null && !pageUrl.isBlank()) {
            sb.append("- URL:  ").append(pageUrl).append("\n");
        }
        if (pageTitle != null && !pageTitle.isBlank()) {
            sb.append("- Title: ").append(pageTitle).append("\n");
        }

        if (timing.getErrorMessage() != null) {
            sb.append("\n## Error Message\n```\n").append(timing.getErrorMessage()).append("\n```\n");
        }

        sb.append("\n## Failing Source File\n");
        sb.append("- File: ").append(snippet.relativePath()).append("\n");
        sb.append("- Line: ").append(snippet.lineNumber()).append("\n\n");

        sb.append("## Code Context (Lines ").append(snippet.startLine()).append("-").append(snippet.endLine()).append(")\n");
        sb.append("```java\n");
        sb.append(snippet.contextCode());
        sb.append("```\n\n");

        sb.append("## Task\n");
        sb.append("Write a standard unified git diff patch to fix the error in `").append(snippet.relativePath()).append("`.\n");
        sb.append("Rules:\n");
        sb.append("1. Output ONLY the unified git diff block.\n");
        sb.append("2. Include proper headers: `--- a/").append(snippet.relativePath()).append("` and `+++ b/").append(snippet.relativePath()).append("`.\n");
        sb.append("3. Do not include markdown prose, conversational text, or explanations. Only the diff.\n");

        return sb.toString();
    }

    /**
     * Strips any markdown fences or surrounding chatter from the diff output.
     */
    public static String sanitizePatch(String rawResponse) {
        if (rawResponse == null) {
            return "";
        }

        String cleaned = rawResponse.trim();
        if (cleaned.startsWith("```diff") || cleaned.startsWith("```patch") || cleaned.startsWith("```")) {
            int firstNewline = cleaned.indexOf('\n');
            int lastFence = cleaned.lastIndexOf("```");
            if (firstNewline != -1 && lastFence > firstNewline) {
                cleaned = cleaned.substring(firstNewline + 1, lastFence).trim();
            }
        }

        return cleaned;
    }

    private static String sanitizeFileName(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
