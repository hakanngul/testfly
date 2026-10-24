package io.testfly.inspector;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public final class InspectorEditorProvider implements FileEditorProvider, DumbAware {

    @Override
    public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
        return file instanceof InspectorFile;
    }

    @Override
    public @NotNull FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
        try {
            return new InspectorEditor(project, file);
        } catch (NoClassDefFoundError e) {
            // JCEF (the embedded Chromium) is missing or disabled in this IDE: explain instead of failing.
            return new MessageEditor(file, "The Mobile Inspector needs JCEF (the embedded browser). Enable the bundled "
                    + "'JCEF' plugin or choose a JetBrains Runtime with JCEF (Help > Find Action > Choose Boot Java Runtime).");
        }
    }

    /** A read-only tab that shows one message. */
    private static final class MessageEditor extends com.intellij.openapi.util.UserDataHolderBase implements FileEditor {
        private final VirtualFile file;
        private final javax.swing.JComponent component;

        MessageEditor(VirtualFile file, String message) {
            this.file = file;
            this.component = new com.intellij.ui.components.JBLabel("<html><div style='width:420px'>" + message + "</div></html>",
                    javax.swing.SwingConstants.CENTER);
        }

        @Override public @NotNull javax.swing.JComponent getComponent() { return component; }
        @Override public javax.swing.JComponent getPreferredFocusedComponent() { return component; }
        @Override public @NotNull String getName() { return "Mobile Inspector"; }
        @Override public VirtualFile getFile() { return file; }
        @Override public void setState(@NotNull com.intellij.openapi.fileEditor.FileEditorState state) { }
        @Override public boolean isModified() { return false; }
        @Override public boolean isValid() { return true; }
        @Override public void addPropertyChangeListener(@NotNull java.beans.PropertyChangeListener l) { }
        @Override public void removePropertyChangeListener(@NotNull java.beans.PropertyChangeListener l) { }
        @Override public void dispose() { }
    }

    @Override
    public @NotNull String getEditorTypeId() {
        return "testfly-mobile-inspector";
    }

    @Override
    public @NotNull FileEditorPolicy getPolicy() {
        return FileEditorPolicy.HIDE_DEFAULT_EDITOR;
    }
}
