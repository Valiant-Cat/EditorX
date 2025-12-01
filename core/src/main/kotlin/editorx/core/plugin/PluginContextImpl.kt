package editorx.core.plugin

import editorx.core.plugin.gui.GuiContext

class PluginContextImpl(private val plugin: Plugin) : PluginContext, Comparable<PluginContextImpl> {
    private var guiContext: GuiContext? = null
    private var hasActive = false

    override fun pluginId(): String {
        return plugin.getInfo().id
    }

    override fun pluginInfo(): PluginInfo {
        return plugin.getInfo()
    }

    override fun gui(): GuiContext? {
        return guiContext
    }

    fun setGuiContext(guiContext: GuiContext) {
        this.guiContext = guiContext
    }

    fun active() {
        plugin.activate(this)
        hasActive = true
    }

    fun deactivate() {
        if (hasActive) {
            plugin.deactivate()
        }
    }

    override fun compareTo(other: PluginContextImpl): Int {
        return this.pluginId().compareTo(other.pluginId())
    }
}