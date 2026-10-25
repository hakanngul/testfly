package io.testfly.inspector;

import com.google.gson.JsonObject;
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

    private void post(JsonObject message) {
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
        worker.shutdownNow();
        if (controller != null) {
            controller.close();
        }
    }
}
