package editorx.core.gui

import editorx.core.store.Store
import editorx.core.store.FileStore
import editorx.core.workspace.DefaultWorkspace
import editorx.core.workspace.Workspace
import java.io.File

class GuiContext(val appDir: File) {

    val settings: Store by lazy {
        FileStore(File(appDir, "settings.properties"))
    }

    val workspace: Workspace = DefaultWorkspace(settings)
}
