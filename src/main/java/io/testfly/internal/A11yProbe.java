package io.testfly.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.testfly.locator.Role;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs the bundled accessibility probe in the browser and parses its JSON answer.
 * The same script feeds the scalar assertions (role, name, description) and the
 * ARIA snapshot, so both always agree.
 */
public final class A11yProbe {

    public static final int DEFAULT_MAX_DEPTH = 25;
    public static final int DEFAULT_MAX_NODES = 2000;

    private static final String RESOURCE = "/io/testfly/accessibility/a11y-probe.js";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static volatile String script;

    private A11yProbe() {
    }

    /** What the probe reports for one element. {@code role} is {@code null} for generic elements. */
    public record NodeInfo(String role, String name, String description, Map<String, Object> attrs) {
    }

    /** One node of the accessibility tree as reported by the probe. */
    public record TreeNode(String role, String name, Map<String, Object> attrs, List<TreeNode> children) {
    }

    /** The probe's tree answer: top-level nodes plus whether a size limit cut it short. */
    public record Tree(List<TreeNode> nodes, boolean truncated) {
    }

    public static NodeInfo probe(WebDriver driver, WebElement element) {
        JsonNode json = run(driver, element, "node", DEFAULT_MAX_DEPTH, DEFAULT_MAX_NODES);
        JsonNode role = json.get("role");
        return new NodeInfo(
                role == null || role.isNull() ? null : role.asText(),
                text(json.get("name")),
                text(json.get("description")),
                attrs(json.get("attrs")));
    }

    /** Captures the subtree under {@code root}; {@code null} means the document body. */
    public static Tree tree(WebDriver driver, WebElement root, int maxDepth, int maxNodes) {
        JsonNode json = run(driver, root, "tree", maxDepth, maxNodes);
        return new Tree(nodes(json.get("nodes")), json.path("truncated").asBoolean(false));
    }

    private static JsonNode run(WebDriver driver, WebElement element, String mode, int maxDepth, int maxNodes) {
        if (!(driver instanceof JavascriptExecutor js)) {
            throw new IllegalStateException("Accessibility assertions need a driver that can execute JavaScript.");
        }
        Map<String, Object> opts = new LinkedHashMap<>();
        opts.put("mode", mode);
        opts.put("maxDepth", maxDepth);
        opts.put("maxNodes", maxNodes);
        opts.put("roles", Arrays.stream(Role.values()).map(Role::ariaName).toList());
        Object raw = js.executeScript(loadScript(), element, opts);
        if (!(raw instanceof String json)) {
            throw new IllegalStateException("The accessibility probe returned no result.");
        }
        try {
            return MAPPER.readTree(json);
        } catch (IOException e) {
            throw new IllegalStateException("The accessibility probe returned invalid JSON.", e);
        }
    }

    private static List<TreeNode> nodes(JsonNode array) {
        java.util.ArrayList<TreeNode> out = new java.util.ArrayList<>();
        if (array != null && array.isArray()) {
            for (JsonNode n : array) {
                JsonNode name = n.get("name");
                out.add(new TreeNode(n.path("role").asText(),
                        name == null || name.isNull() ? null : name.asText(),
                        attrs(n.get("attrs")), nodes(n.get("children"))));
            }
        }
        return out;
    }

    private static Map<String, Object> attrs(JsonNode node) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (node != null && node.isObject()) {
            node.fields().forEachRemaining(e -> {
                JsonNode v = e.getValue();
                out.put(e.getKey(), v.isBoolean() ? (Object) v.asBoolean() : v.isNumber() ? (Object) v.asInt() : v.asText());
            });
        }
        return out;
    }

    private static String text(JsonNode node) {
        return node == null || node.isNull() ? "" : node.asText();
    }

    private static String loadScript() {
        String s = script;
        if (s != null) {
            return s;
        }
        synchronized (A11yProbe.class) {
            if (script == null) {
                try (InputStream in = A11yProbe.class.getResourceAsStream(RESOURCE)) {
                    if (in == null) {
                        throw new IllegalStateException("a11y-probe.js not found on the classpath at " + RESOURCE);
                    }
                    script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new IllegalStateException("Could not read " + RESOURCE, e);
                }
            }
            return script;
        }
    }
}
