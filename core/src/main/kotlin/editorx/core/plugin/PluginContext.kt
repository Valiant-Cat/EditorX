package editorx.core.plugin

import editorx.core.plugin.gui.PluginGuiProvider

interface PluginContext {

    fun pluginId(): String

    fun pluginInfo(): PluginInfo

    fun gui(): PluginGuiProvider?

    fun active()

    fun deactivate()

    fun isActive(): Boolean
}
