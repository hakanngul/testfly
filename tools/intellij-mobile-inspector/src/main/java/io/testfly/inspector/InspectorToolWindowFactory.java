package io.testfly.inspector;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import java.awt.Component;

/** The left-side "TestFly Mobile" tool window: what the inspector does and a button to open it. */
public final class InspectorToolWindowFactory implements ToolWindowFactory, DumbAware {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(JBUI.Borders.empty(16));

        JBLabel title = new JBLabel("Mobile Inspector", InspectorFile.ICON, JBLabel.LEFT);
        title.setFont(JBUI.Fonts.label().biggerOn(2).asBold());
        JBLabel text = new JBLabel("<html>Inspect an iOS or Android screen through mobilecli: screenshot, UI tree with "
                + "@eN refs and ready-to-paste TestFly selectors.</html>");
        text.setForeground(UIUtil.getContextHelpForeground());
        JButton open = new JButton("Open Mobile Inspector");
        open.putClientProperty("JButton.buttonType", "default");
        open.addActionListener(e -> OpenInspectorAction.open(project));
        JBLabel needs = new JBLabel("<html>Needs mobilecli and a booted simulator, emulator or device.</html>");
        needs.setForeground(UIUtil.getContextHelpForeground());
        ActionLink settings = new ActionLink("Settings", (java.awt.event.ActionListener) e ->
                ShowSettingsUtil.getInstance().showSettingsDialog(project, InspectorConfigurable.class));

        for (Component c : new Component[] {title, JBUI.Panels.simplePanel().withPreferredHeight(8), text,
                JBUI.Panels.simplePanel().withPreferredHeight(14), open, JBUI.Panels.simplePanel().withPreferredHeight(14), needs, settings}) {
            ((javax.swing.JComponent) c).setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(c);
        }
        toolWindow.getContentManager().addContent(ContentFactory.getInstance().createContent(panel, "", false));
    }
}
