package editor.gui.plugin

import editor.gui.ui.MainWindow
import editor.plugin.PluginContext
import editor.plugin.PluginContextFactory
import editor.plugin.PluginManager

object GuiPluginInitializer {

    fun initialize(mv: MainWindow) {
        try {
            val contextFactory = object : PluginContextFactory {
                override fun createPluginContext(): PluginContext {
                    return GuiPluginContext(mv)
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