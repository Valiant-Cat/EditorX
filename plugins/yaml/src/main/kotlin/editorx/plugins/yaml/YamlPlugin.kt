package editorx.plugins.yaml

import YamlFileType
import editorx.core.plugin.Plugin
import editorx.core.plugin.PluginContext
import editorx.core.plugin.PluginInfo

class YamlPlugin : Plugin {

    override fun getInfo(): PluginInfo = PluginInfo(
        id = "yaml",
        name = "YAML",
        version = "0.0.1",
    )

    override fun activate(pluginContext: PluginContext) {
        pluginContext.guiClient()?.registerFileType(YamlFileType)
    }
}
