package editorx.core.gui

import editorx.core.settings.PropertiesSettingsStore
import editorx.core.settings.SettingsStore
import editorx.core.workspace.DefaultWorkspaceManager
import editorx.core.workspace.WorkspaceManager
import java.io.File

class GuiContext(private val appDir: File) {
    val settings: SettingsStore by lazy {
        val settingsFile = File(appDir, "settings.properties")
        PropertiesSettingsStore(settingsFile)
    }
    val workspace: WorkspaceManager = DefaultWorkspaceManager(settings)

    fun appDirectory(): File = appDir
}
