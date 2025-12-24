package editorx.core.plugin

import editorx.core.plugin.gui.GuiContext
import editorx.core.services.ServiceRegistry

interface PluginContext {

    fun pluginId(): String

    fun pluginInfo(): PluginInfo

    fun gui(): GuiContext?

    fun services(): ServiceRegistry

    fun <T : Any> registerService(serviceClass: Class<T>, instance: T)

    fun <T : Any> unregisterService(serviceClass: Class<T>)
}
