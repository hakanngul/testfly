package io.testfly.inspector;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.jcef.JBCefApp;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.ui.jcef.JBCefBrowserBase;
import com.intellij.ui.jcef.JBCefJSQuery;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import javax.swing.SwingConstants;
import java.awt.Color;
import java.awt.datatransfer.StringSelection;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The inspector tab: the VS Code extension's webview (media/*, copied in by Gradle) in a JCEF
 * browser. {@code acquireVsCodeApi()} is replaced by a JS query to {@link InspectorController},
 * and the IDE theme's colours are passed in as the {@code --vscode-*} CSS variables the page reads.
 */
public final class InspectorEditor extends UserDataHolderBase implements FileEditor {

    private final Project project;
    private final VirtualFile file;
    private final JComponent component;
    private JBCefBrowser browser;
    private InspectorController controller;
    // One message at a time, in order, like the VS Code extension host.
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> new Thread(r, "TestFly Mobile Inspector"));
    private LocatorCounts.Target lensTarget;
    private String lastScreen = ""; // device id + tree of the screen the counts were made for

    static {
        // new counts: let the editors ask the code vision provider again
        LocatorCounts.onChange(() -> ApplicationManager.getApplication().invokeLater(() -> {
            for (Project p : ProjectManager.getInstance().getOpenProjects()) {
                if (!p.isDisposed()) {
                    DaemonCodeAnalyzer.getInstance(p).restart();
                }
            }
        }));
    }

    InspectorEditor(Project project, VirtualFile file) {
        this.project = project;
        this.file = file;
        if (!JBCefApp.isSupported()) {
            component = new JBLabel("The Mobile Inspector needs JCEF (the embedded Chromium), which this IDE runtime does not provide.",
                    SwingConstants.CENTER);
            return;
        }
        browser = new JBCefBrowser();
        JBCefJSQuery query = JBCefJSQuery.create((JBCefBrowserBase) browser);
        InspectorSettings.Data s = InspectorSettings.get().getState();
        controller = new InspectorController(new MobileCliClient(s.serverUrl, s.mobilecliPath, s.autoStartServer), this::post,
                new InspectorController.Ide() {
                    @Override
                    public void copy(String text, String label) {
                        CopyPasteManager.getInstance().setContents(new StringSelection(text));
                        StatusBar.Info.set("Copied " + label, project);
                    }

                    @Override
                    public boolean autoRefreshAfterAction() {
                        return InspectorSettings.get().getState().autoRefreshAfterAction;
                    }
                });
        query.addHandler(json -> {
            if (json.contains("\"resolved\"") && onResolved(json)) {
                return null; // the page's answer to a count request from the code vision provider
            }
            worker.execute(() -> controller.handle(json));
            return null;
        });
        browser.loadHTML(page(query));
        Disposer.register(this, browser);
        Disposer.register(this, query);
        component = browser.getComponent();
    }

    private String page(JBCefJSQuery query) {
        String bridge = "window.acquireVsCodeApi = () => ({"
                + " getState() { return window.__testflyState || null; },"
                + " setState(s) { window.__testflyState = s; },"
                + " postMessage(m) { const request = JSON.stringify(m); " + query.inject("request") + " } });";
        return "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\"><title>Mobile Inspector</title>"
                + "<style>" + resource("inspector.css") + themeVariables() + "</style></head><body>"
                + resource("body.html")
                + "<script>" + bridge + "</script>"
                + "<script>" + resource("selectors.js") + "</script>"
                + "<script>" + resource("inspector.js") + "</script>"
                + "</body></html>";
    }

    /** IDE colours under the names the shared stylesheet reads, so the page follows the IDE theme. */
    private static String themeVariables() {
        EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
        boolean dark = !JBColor.isBright();
        Color fg = UIUtil.getLabelForeground();
        StringBuilder css = new StringBuilder(":root{");
        cssVar(css, "editor-background", scheme.getDefaultBackground());
        cssVar(css, "sideBar-background", UIUtil.getPanelBackground());
        cssVar(css, "foreground", fg);
        cssVar(css, "descriptionForeground", UIUtil.getContextHelpForeground());
        cssVar(css, "panel-border", JBColor.border());
        cssVar(css, "focusBorder", JBUI.CurrentTheme.Focus.focusColor());
        cssVar(css, "button-background", JBColor.namedColor("Button.default.startBackground", new JBColor(0x3574F0, 0x3574F0)));
        cssVar(css, "button-foreground", JBColor.namedColor("Button.default.foreground", JBColor.WHITE));
        cssVar(css, "button-hoverBackground", JBColor.namedColor("Button.default.focusedBorderColor", new JBColor(0x2E63CF, 0x2E63CF)));
        cssVar(css, "list-activeSelectionBackground", UIUtil.getListSelectionBackground(true));
        cssVar(css, "list-activeSelectionForeground", UIUtil.getListSelectionForeground(true));
        cssVar(css, "input-background", UIUtil.getTextFieldBackground());
        cssVar(css, "textLink-foreground", JBUI.CurrentTheme.Link.Foreground.ENABLED);
        cssVar(css, "errorForeground", JBColor.namedColor("Label.errorForeground", new JBColor(0xDB3B4B, 0xF0616D)));
        cssVar(css, "notifications-background", UIUtil.getPanelBackground());
        css.append("--vscode-list-hoverBackground:").append(dark ? "rgba(255,255,255,.06)" : "rgba(0,0,0,.05)").append(';');
        css.append("--vscode-button-secondaryBackground:").append(dark ? "rgba(255,255,255,.08)" : "rgba(0,0,0,.06)").append(';');
        css.append("--vscode-input-border:").append(dark ? "rgba(255,255,255,.14)" : "rgba(0,0,0,.18)").append(';');
        css.append("--vscode-font-family:'").append(UIUtil.getLabelFont().getFamily()).append("',system-ui,sans-serif;");
        css.append("--vscode-editor-font-family:'").append(scheme.getEditorFontName()).append("',monospace;");
        return css.append('}').toString();
    }

    private static void cssVar(StringBuilder css, String name, Color c) {
        if (c != null) {
            css.append("--vscode-").append(name).append(':')
                    .append(String.format(java.util.Locale.ROOT, "rgba(%d,%d,%d,%.3f)", c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha() / 255.0)).append(';');
        }
    }

    private static String resource(String name) {
        try (InputStream in = InspectorEditor.class.getResourceAsStream("/inspector/" + name)) {
            if (in == null) {
                throw new IllegalStateException("Missing inspector resource " + name + " (built from tools/vscode-mobile-inspector/media)");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Shows this device (from the tool window's device list). */
    void inspect(String deviceId) {
        if (controller != null) {
            controller.want(deviceId);
            worker.execute(() -> controller.handle("{\"type\":\"devices\"}"));
        }
    }

    private boolean onResolved(String json) {
        JsonObject m = JsonParser.parseString(json).getAsJsonObject();
        if (!"resolved".equals(MobileCliClient.str(m, "type"))) {
            return false;
        }
        List<LocatorCounts.Count> counts = new ArrayList<>();
        JsonArray results = m.has("results") && m.get("results").isJsonArray() ? m.getAsJsonArray("results") : new JsonArray();
        for (JsonElement r : results) {
            JsonObject o = r.getAsJsonObject();
            counts.add(new LocatorCounts.Count(o.has("count") ? o.get("count").getAsInt() : -1, MobileCliClient.str(o, "error")));
        }
        LocatorCounts.resolved(m.get("id").getAsLong(), counts);
        return true;
    }

    /** Tells the code vision provider about each new screen, so the counts in the editors follow it. */
    private void watchScreens(JsonObject message) {
        String type = MobileCliClient.str(message, "type");
        if ("screen".equals(type) && message.has("device")) {
            JsonObject device = message.getAsJsonObject("device");
            String key = MobileCliClient.str(device, "id") + "\n" + message.get("elements");
            if (key.equals(lastScreen)) {
                return; // a Live refresh of an unchanged screen
            }
            lastScreen = key;
            String name = MobileCliClient.str(device, "name");
            String platform = MobileCliClient.str(device, "platform");
            lensTarget = new LocatorCounts.Target() {
                @Override
                public String label() {
                    return name;
                }

                @Override
                public String platform() {
                    return platform;
                }

                @Override
                public void resolve(long requestId, List<String> selectors) {
                    JsonObject m = new JsonObject();
                    m.addProperty("type", "resolve");
                    m.addProperty("id", requestId);
                    JsonArray list = new JsonArray();
                    selectors.forEach(list::add);
                    m.add("selectors", list);
                    post(m);
                }

                @Override
                public void reveal(String selector) {
                    ApplicationManager.getApplication().invokeLater(() -> FileEditorManager.getInstance(project).openFile(file, true));
                    JsonObject m = new JsonObject();
                    m.addProperty("type", "reveal");
                    m.addProperty("selector", selector);
                    post(m);
                }
            };
            LocatorCounts.screenRead(lensTarget);
        } else if ("server".equals(type) && message.has("running") && !message.get("running").getAsBoolean()) {
            lastScreen = "";
            LocatorCounts.closed(lensTarget);
        }
    }

    private void post(JsonObject message) {
        watchScreens(message);
        if (browser != null && !browser.isDisposed()) {
            browser.getCefBrowser().executeJavaScript("window.postMessage(" + message + ", '*');", browser.getCefBrowser().getURL(), 0);
        }
    }

    @Override
    public @NotNull JComponent getComponent() {
        return component;
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        return component;
    }

    @Override
    public @NotNull String getName() {
        return "Mobile Inspector";
    }

    @Override
    public VirtualFile getFile() {
        return file;
    }

    @Override
    public void setState(@NotNull FileEditorState state) {
    }

    @Override
    public boolean isModified() {
        return false;
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
    }

    @Override
    public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
    }

    @Override
    public void dispose() {
        LocatorCounts.closed(lensTarget);
        worker.shutdownNow();
        if (controller != null) {
            controller.close();
        }
    }
}
