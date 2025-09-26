package editorx.plugin

interface PluginScanner {

    fun scanPlugins(): List<LoadedPlugin>
}
