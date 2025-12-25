package editorx.core.gui

import editorx.core.store.Store
import editorx.core.store.FileStore
import editorx.core.workspace.DefaultWorkspaceManager
import editorx.core.workspace.WorkspaceManager
import java.io.File

class GuiContext(private val appDir: File) {
    val settings: Store by lazy {
        FileStore(File(appDir, "settings.properties"))
    }
    val workspace: WorkspaceManager = DefaultWorkspaceManager(settings)

    fun appDirectory(): File = appDir
}
