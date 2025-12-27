package editorx.core.plugin

import editorx.core.gui.GuiExtension
import editorx.core.service.MutableServiceRegistry

class PluginContext(
    private val plugin: Plugin,
    private val servicesRegistry: MutableServiceRegistry,
) : Comparable<PluginContext> {
    private var hasActive = false

    var guiExtension: GuiExtension? = null

    fun pluginId(): String {
        return plugin.getInfo().id
    }

    fun pluginInfo(): PluginInfo {
        return plugin.getInfo()
    }

    fun gui(): GuiExtension? {
        return guiExtension
    }

    fun active() {
        if (hasActive) return
        plugin.activate(this)
        hasActive = true
    }

    fun deactivate() {
        if (hasActive) {
            plugin.deactivate()
            hasActive = false
        }
    }

    fun isActive(): Boolean {
        return hasActive
    }

    fun <T : Any> registerService(serviceClass: Class<T>, instance: T) {
        servicesRegistry.registerService(serviceClass, instance)
    }

    fun <T : Any> unregisterService(serviceClass: Class<T>, instance: T) {
        servicesRegistry.unregisterService(serviceClass, instance)
    }

    override fun compareTo(other: PluginContext): Int {
        return this.pluginId().compareTo(other.pluginId())
    }
}
