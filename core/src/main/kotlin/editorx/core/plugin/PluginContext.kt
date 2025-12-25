package editorx.core.plugin

import editorx.core.plugin.gui.PluginGuiClient
import editorx.core.service.ServiceRegistry

interface PluginContext {

    fun pluginId(): String

    fun pluginInfo(): PluginInfo

    fun gui(): PluginGuiClient?

    fun services(): ServiceRegistry

    fun <T : Any> registerService(serviceClass: Class<T>, instance: T)

    fun <T : Any> unregisterService(serviceClass: Class<T>)
}
