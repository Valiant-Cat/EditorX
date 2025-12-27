package editorx.core.plugin

interface PluginLoader {

    fun load(): List<DiscoveredPlugin>
}
