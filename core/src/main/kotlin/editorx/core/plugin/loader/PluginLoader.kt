package editorx.core.plugin.loader

interface PluginLoader {

    fun load(): List<DiscoveredPlugin>
}
