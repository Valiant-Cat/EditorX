package editorx.gui.plugin

import editorx.gui.MainWindow
import editorx.plugin.LoadedPlugin
import editorx.plugin.PluginContext
import editorx.plugin.PluginContextFactory

class GuiPluginContextFactory(private val mv: MainWindow) : PluginContextFactory {

    override fun createPluginContext(loadedPlugin: LoadedPlugin): PluginContext {
        return GuiPluginContext(mv, loadedPlugin)
    }
}
