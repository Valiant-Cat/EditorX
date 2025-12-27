package editorx.core.plugin.loader

import editorx.core.plugin.LoadedPlugin

interface PluginLoader {

    fun load(): List<LoadedPlugin>
}
