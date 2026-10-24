package io.testfly.inspector;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;

/** Settings > Tools > TestFly Mobile Inspector; same keys and defaults as the VS Code extension. */
@State(name = "TestFlyMobileInspector", storages = @Storage("testfly-mobile-inspector.xml"))
public final class InspectorSettings implements PersistentStateComponent<InspectorSettings.Data> {

    public static final class Data {
        public String serverUrl = "http://localhost:12000";
        public String mobilecliPath = "mobilecli";
        public boolean autoStartServer = true;
        public boolean autoRefreshAfterAction = true;
    }

    private Data data = new Data();

    public static InspectorSettings get() {
        return ApplicationManager.getApplication().getService(InspectorSettings.class);
    }

    @Override
    public Data getState() {
        return data;
    }

    @Override
    public void loadState(Data state) {
        data = state;
    }
}
