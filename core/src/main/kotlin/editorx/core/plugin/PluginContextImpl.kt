package editorx.core.plugin

import editorx.core.plugin.gui.GuiContext
import editorx.core.services.MutableServiceRegistry

class PluginContextImpl(
    private val plugin: Plugin,
    private val serviceRegistry: MutableServiceRegistry,
) : PluginContext, Comparable<PluginContextImpl> {
    private var guiContext: GuiContext? = null
    private var hasActive = false
    private val ownedServices: MutableSet<Class<*>> = linkedSetOf()

    override fun pluginId(): String {
        return plugin.getInfo().id
    }

    override fun pluginInfo(): PluginInfo {
        return plugin.getInfo()
    }

    override fun gui(): GuiContext? {
        return guiContext
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

    fun setGuiContext(guiContext: GuiContext) {
        this.guiContext = guiContext
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
