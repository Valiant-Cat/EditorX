package editorx.core.plugin

import editorx.core.plugin.gui.GuiContext

interface PluginContext {

    fun pluginId(): String

    fun pluginInfo(): PluginInfo

    fun gui(): GuiContext?
}
