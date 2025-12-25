package editorx.plugins.json

import editorx.core.plugin.Plugin
import editorx.core.plugin.PluginContext
import editorx.core.plugin.PluginInfo

class JsonPlugin : Plugin {
    override fun getInfo(): PluginInfo = PluginInfo(
        id = "json",
        name = "JSON",
        version = "0.0.1",
    )

    override fun activate(pluginContext: PluginContext) {
        pluginContext.guiClient()?.registerFileType(JsonFIleType)
        pluginContext.guiClient()?.registerFormatter(JsonLanguage, JsonFormatter)
    }
}

