package editorx.core.plugin

import editorx.core.plugin.gui.PluginGuiClient
import editorx.core.service.MutableServiceRegistry

class PluginContextImpl(
    private val plugin: Plugin,
    private val serviceRegistry: MutableServiceRegistry,
) : PluginContext, Comparable<PluginContextImpl> {
    private var guiClient: PluginGuiClient? = null
    private var hasActive = false
    private val ownedServices: MutableSet<Class<*>> = linkedSetOf()

    override fun pluginId(): String {
        return plugin.getInfo().id
    }

    override fun pluginInfo(): PluginInfo {
        return plugin.getInfo()
    }

    override fun gui(): PluginGuiClient? {
        return guiClient
    }

    override fun services(): MutableServiceRegistry = serviceRegistry

    override fun <T : Any> registerService(serviceClass: Class<T>, instance: T) {
        serviceRegistry.register(serviceClass, instance)
        ownedServices.add(serviceClass)
    }

    override fun <T : Any> unregisterService(serviceClass: Class<T>) {
        serviceRegistry.unregister(serviceClass)
        ownedServices.remove(serviceClass)
    }

    fun setGuiContext(guiClient: PluginGuiClient) {
        this.guiClient = guiClient
    }

    fun active() {
        if (hasActive) return
        plugin.activate(this)
        hasActive = true
    }

    fun deactivate() {
        if (hasActive) {
            plugin.deactivate()
            ownedServices.forEach { cls ->
                @Suppress("UNCHECKED_CAST")
                serviceRegistry.unregister(cls as Class<Any>)
            }
            ownedServices.clear()
            hasActive = false
        }
    }

    override fun compareTo(other: PluginContextImpl): Int {
        return this.pluginId().compareTo(other.pluginId())
    }
}
