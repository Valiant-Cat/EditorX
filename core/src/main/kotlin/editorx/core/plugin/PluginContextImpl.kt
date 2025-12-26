package editorx.core.plugin

import editorx.core.plugin.gui.PluginGuiProvider

class PluginContextImpl(
    private val plugin: Plugin,
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

    override fun compareTo(other: PluginContextImpl): Int {
        return this.pluginId().compareTo(other.pluginId())
    }
}
