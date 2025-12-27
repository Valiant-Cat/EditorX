package editorx.plugins.json

import editorx.core.plugin.Plugin
import editorx.core.plugin.PluginContext
import editorx.core.plugin.PluginInfo

class JsonPlugin : Plugin {
    override fun getInfo() = PluginInfo(
        id = "json",
        name = "JSON",
        version = "0.0.1",
    )

    private var pluginContext: PluginContext? = null

    override fun activate(pluginContext: PluginContext) {
        this.pluginContext = pluginContext
        pluginContext.gui()?.registerFileType(JsonFIleType)
        pluginContext.gui()?.registerFormatter(JsonLanguage, JsonFormatter)
    }

    override fun deactivate() {
        pluginContext?.gui()?.unregisterAllFileTypes()
        pluginContext?.gui()?.unregisterAllFormatters()
        pluginContext = null
    }
}

