package io.testfly.unit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.testfly.reporting.HtmlReportGenerator;
import org.testng.annotations.Test;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Regression tests for SEC-001 / SEC-002 / API-024: the JSON embedded in the HTML report must not be able
 * to close its {@code <script>} element, and substituted values must never be re-scanned for placeholders.
 */
public class HtmlReportGeneratorEscapingTest {

    private static final String DATA_OPEN = "<script id=\"testfly-data\" type=\"application/json\">";
    private static final String HOSTILE_MESSAGE = "x</script><img src=x onerror=alert(1)><!-- \u2028 \u2029";
    private static final String PLACEHOLDER_MESSAGE = "expected {{PASSED}} items";

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void embeddedDataBlockCannotBeClosedByErrorMessage() throws Exception {
        String html = renderReportWithErrorMessages(HOSTILE_MESSAGE, PLACEHOLDER_MESSAGE);

        String block = dataBlock(html);

        assertFalse(block.contains("<"), "embedded JSON must not contain a raw '<'");
        assertFalse(block.contains("\u2028") || block.contains("\u2029"), "line separators must be escaped");
        int blockEnd = html.indexOf(DATA_OPEN) + DATA_OPEN.length() + block.length();
        assertTrue(html.startsWith("</script>", blockEnd), "data block must end at its own closing tag");
        assertFalse(html.contains("<img src=x onerror=alert(1)>"), "injected markup must not reach the page");
    }

    @Test
    public void escapedDataBlockDecodesToOriginalMessages() throws Exception {
        String html = renderReportWithErrorMessages(HOSTILE_MESSAGE, PLACEHOLDER_MESSAGE);

        JsonNode tests = mapper.readTree(dataBlock(html)).get("currentRunTests");

        assertEquals(tests.get(0).get("errorMessage").asText(), HOSTILE_MESSAGE);
        assertEquals(tests.get(1).get("errorMessage").asText(), PLACEHOLDER_MESSAGE,
                "text that looks like a placeholder must be preserved verbatim");
    }

    @Test
    public void standaloneJsonExportKeepsRawText() throws Exception {
        Path dir = Files.createTempDirectory("testfly-html-escape-");
        try {
            generateInto(dir, HOSTILE_MESSAGE);

            JsonNode tests = mapper.readTree(dir.resolve("testfly-report-data.json").toFile()).get("currentRunTests");

            assertEquals(tests.get(0).get("errorMessage").asText(), HOSTILE_MESSAGE);
        } finally {
            deleteRecursively(dir);
        }
    }

    @Test
    public void escapeJsonForHtmlEscapesAngleBracketAndLineSeparators() throws Exception {
        Method escape = HtmlReportGenerator.class.getDeclaredMethod("escapeJsonForHtml", String.class);
        escape.setAccessible(true);

        String escaped = (String) escape.invoke(null, "[\"</script>\u2028\u2029\"]");

        assertEquals(escaped, "[\"\\u003c/script>\\u2028\\u2029\"]");
        assertEquals(escape.invoke(null, (Object) null), "");
    }

    @Test
    public void renderTemplateIsSinglePassAndEscapesRunHistoryJson() throws Exception {
        Method escape = HtmlReportGenerator.class.getDeclaredMethod("escapeJsonForHtml", String.class);
        Method render = HtmlReportGenerator.class.getDeclaredMethod("renderTemplate", String.class, Map.class);
        escape.setAccessible(true);
        render.setAccessible(true);
        Map<String, String> values = new LinkedHashMap<>();
        values.put("TESTFLY_DATA_JSON", "{\"m\":\"{{PASSED}} $1 \\\\\"}");
        values.put("RUN_HISTORY_JSON", (String) escape.invoke(null, "[{\"t\":\"</script>\"}]"));
        values.put("PASSED", "42");

        String out = (String) render.invoke(null, "{{TESTFLY_DATA_JSON}}|{{RUN_HISTORY_JSON}}|{{PASSED}}|{{UNKNOWN}}",
                values);

        assertEquals(out, "{\"m\":\"{{PASSED}} $1 \\\\\"}|[{\"t\":\"\\u003c/script>\"}]|42|{{UNKNOWN}}");
    }

    private String renderReportWithErrorMessages(String... messages) throws Exception {
        Path dir = Files.createTempDirectory("testfly-html-escape-");
        try {
            generateInto(dir, messages);
            return Files.readString(dir.resolve("testfly-report.html"), StandardCharsets.UTF_8);
        } finally {
            deleteRecursively(dir);
        }
    }

    private void generateInto(Path dir, String... messages) throws Exception {
        List<Map<String, Object>> tests = new ArrayList<>();
        for (int i = 0; i < messages.length; i++) {
            Map<String, Object> test = new LinkedHashMap<>();
            test.put("testId", "T" + i);
            test.put("status", "FAILED");
            test.put("totalMs", 10);
            test.put("errorMessage", messages[i]);
            tests.add(test);
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("totalTests", messages.length);
        root.put("passedTests", 0);
        root.put("failedTests", messages.length);
        root.put("skippedTests", 0);
        root.put("passRate", 0.0);
        root.put("totalTimeMs", 10L);
        root.put("tests", tests);
        root.put("loadTests", new ArrayList<>());
        File metrics = dir.resolve("testfly-metrics.json").toFile();
        mapper.writeValue(metrics, root);

        HtmlReportGenerator.generate(metrics);
    }

    private static String dataBlock(String html) {
        int start = html.indexOf(DATA_OPEN);
        assertTrue(start >= 0, "report must contain the testfly-data block");
        start += DATA_OPEN.length();
        return html.substring(start, html.indexOf("</script>", start));
    }

    private static void deleteRecursively(Path dir) throws Exception {
        try (var paths = Files.walk(dir)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }
}
