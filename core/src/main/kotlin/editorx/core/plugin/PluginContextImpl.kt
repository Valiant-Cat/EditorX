package editorx.core.plugin

import editorx.core.service.MutableServiceRegistry

class PluginContextImpl(
    private val plugin: Plugin,
    private val servicesRegistry: MutableServiceRegistry,
) : PluginContext, Comparable<PluginContextImpl> {
    private var hasActive = false

    var guiProvider: PluginGuiProvider? = null

    override fun pluginId(): String {
        return plugin.getInfo().id
    }

    override fun pluginInfo(): PluginInfo {
        return plugin.getInfo()
    }

    override fun gui(): PluginGuiProvider? {
        return guiProvider
    }


    override fun active() {
        if (hasActive) return
        plugin.activate(this)
        hasActive = true
    }

    override fun deactivate() {
        if (hasActive) {
            plugin.deactivate()
            hasActive = false
        }
    }

    override fun isActive(): Boolean {
        return hasActive
    }

    override fun <T : Any> registerService(serviceClass: Class<T>, instance: T) {
        servicesRegistry.registerMulti(serviceClass, instance)
    }

    override fun <T : Any> unregisterService(serviceClass: Class<T>, instance: T) {
        servicesRegistry.unregisterMulti(serviceClass, instance)
    }

    override fun compareTo(other: PluginContextImpl): Int {
        return this.pluginId().compareTo(other.pluginId())
    }
}
