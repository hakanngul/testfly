package io.testfly.inspector;

import com.intellij.openapi.options.Configurable;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;

import javax.swing.JComponent;
import javax.swing.JPanel;

public final class InspectorConfigurable implements Configurable {

    private final JBTextField serverUrl = new JBTextField();
    private final JBTextField mobilecliPath = new JBTextField();
    private final JBCheckBox autoStart = new JBCheckBox("Start a local mobilecli server when it is not running");
    private final JBCheckBox autoRefresh = new JBCheckBox("Read the screen again after a tap, swipe, text or button action");

    @Override
    public String getDisplayName() {
        return "TestFly Mobile Inspector";
    }

    @Override
    public JComponent createComponent() {
        JPanel panel = FormBuilder.createFormBuilder()
                .addLabeledComponent("mobilecli server URL:", serverUrl)
                .addLabeledComponent("mobilecli executable:", mobilecliPath)
                .addComponent(autoStart)
                .addComponent(autoRefresh)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
        reset();
        return panel;
    }

    @Override
    public boolean isModified() {
        InspectorSettings.Data d = InspectorSettings.get().getState();
        return !serverUrl.getText().equals(d.serverUrl) || !mobilecliPath.getText().equals(d.mobilecliPath)
                || autoStart.isSelected() != d.autoStartServer || autoRefresh.isSelected() != d.autoRefreshAfterAction;
    }

    @Override
    public void apply() {
        InspectorSettings.Data d = InspectorSettings.get().getState();
        d.serverUrl = serverUrl.getText().trim();
        d.mobilecliPath = mobilecliPath.getText().trim();
        d.autoStartServer = autoStart.isSelected();
        d.autoRefreshAfterAction = autoRefresh.isSelected();
    }

    @Override
    public void reset() {
        InspectorSettings.Data d = InspectorSettings.get().getState();
        serverUrl.setText(d.serverUrl);
        mobilecliPath.setText(d.mobilecliPath);
        autoStart.setSelected(d.autoStartServer);
        autoRefresh.setSelected(d.autoRefreshAfterAction);
    }
}
