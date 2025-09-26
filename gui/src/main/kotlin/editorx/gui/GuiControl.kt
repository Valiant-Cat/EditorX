package editorx.gui

import editorx.settings.PropertiesSettingsStore
import editorx.settings.SettingsStore
import editorx.workspace.DefaultWorkspaceManager
import editorx.workspace.WorkspaceManager
import java.io.File

class GuiControl(private val appDir: File) {
    val settings: SettingsStore by lazy {
        val settingsFile = File(appDir, "settings.properties")
        PropertiesSettingsStore(settingsFile)
    }
    val workspace: WorkspaceManager = DefaultWorkspaceManager(settings)
}
