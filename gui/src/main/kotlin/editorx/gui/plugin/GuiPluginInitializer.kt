package editorx.gui.plugin

import editorx.gui.ui.MainWindow
import editorx.plugin.LoadedPlugin
import editorx.plugin.PluginContext
import editorx.plugin.PluginContextFactory
import editorx.plugin.PluginManager

object GuiPluginInitializer {

    fun initialize(mv: MainWindow) {
        try {
            val contextFactory = object : PluginContextFactory {
                override fun createPluginContext(loadedPlugin: LoadedPlugin): PluginContext {
                    return GuiPluginContext(mv, loadedPlugin)
                }
            }
            val pluginManager = PluginManager(contextFactory)
            pluginManager.loadPlugins()
            mv.statusBar.setMessage("插件系统已启动")
        } catch (e: Exception) {
            mv.statusBar.setMessage("插件加载失败: ${e.message}")
        }
    }
}
