package io.testfly.inspector;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

/** The left-side "TestFly Mobile" tool window: the device list and a button to open the inspector. */
public final class InspectorToolWindowFactory implements ToolWindowFactory, DumbAware {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        DeviceListPanel panel = new DeviceListPanel(project, toolWindow);
        Content content = ContentFactory.getInstance().createContent(panel, "", false);
        Disposer.register(content, panel);
        toolWindow.getContentManager().addContent(content);
    }
}
