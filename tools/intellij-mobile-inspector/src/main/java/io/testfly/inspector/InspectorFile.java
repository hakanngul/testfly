package io.testfly.inspector;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.ex.FakeFileType;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;

import javax.swing.Icon;

/** The editor tab that hosts the inspector (one per project). */
public final class InspectorFile extends LightVirtualFile {

    static final Icon ICON = IconLoader.getIcon("/icons/inspector.svg", InspectorFile.class);

    static final FileType TYPE = new FakeFileType() {
        @Override
        public String getName() {
            return "TestFlyMobileInspector";
        }

        @Override
        public String getDescription() {
            return "TestFly Mobile Inspector";
        }

        @Override
        public Icon getIcon() {
            return ICON;
        }

        @Override
        public boolean isMyFileType(VirtualFile file) {
            return file instanceof InspectorFile;
        }
    };

    public InspectorFile() {
        super("Mobile Inspector", TYPE, "");
        setWritable(false);
    }
}
