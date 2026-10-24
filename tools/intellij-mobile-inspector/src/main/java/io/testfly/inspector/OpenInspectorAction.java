package io.testfly.inspector;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/** Tools > TestFly: Open Mobile Inspector; reuses the open tab. */
public final class OpenInspectorAction extends AnAction implements DumbAware {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        if (e.getProject() != null) {
            open(e.getProject());
        }
    }

    static void open(Project project) {
        FileEditorManager editors = FileEditorManager.getInstance(project);
        for (VirtualFile f : editors.getOpenFiles()) {
            if (f instanceof InspectorFile) {
                editors.openFile(f, true);
                return;
            }
        }
        editors.openFile(new InspectorFile(), true);
    }
}
