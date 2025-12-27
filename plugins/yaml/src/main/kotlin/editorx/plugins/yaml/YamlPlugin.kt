package editorx.plugins.yaml

import YamlFileType
import editorx.core.plugin.Plugin
import editorx.core.plugin.PluginContext
import editorx.core.plugin.PluginInfo

class YamlPlugin : Plugin {

    override fun getInfo() = PluginInfo(
        id = "yaml",
        name = "YAML",
        version = "0.0.1",
    )

    private var pluginContext: PluginContext? = null

    override fun activate(pluginContext: PluginContext) {
        this.pluginContext = pluginContext
        pluginContext.gui()?.registerFileType(YamlFileType)
    }

    override fun deactivate() {
        pluginContext?.gui()?.unregisterAllFileTypes()
        pluginContext = null
    }
}
