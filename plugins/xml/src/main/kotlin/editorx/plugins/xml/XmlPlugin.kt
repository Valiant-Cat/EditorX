package editorx.plugins.xml

import XmlFileType
import XmlLanguage
import editorx.core.plugin.Plugin
import editorx.core.plugin.PluginContext
import editorx.core.plugin.PluginInfo

class XmlPlugin : Plugin {
    override fun getInfo() = PluginInfo(
        id = "xml",
        name = "XML",
        version = "0.0.1",
    )

    private var pluginContext: PluginContext? = null

    override fun activate(pluginContext: PluginContext) {
        this.pluginContext = pluginContext
        pluginContext.gui()?.registerFileType(XmlFileType)
        pluginContext.gui()?.registerFormatter(XmlLanguage, XmlFormatter)
    }

    override fun deactivate() {
        pluginContext?.gui()?.unregisterAllFileTypes()
        pluginContext?.gui()?.unregisterAllFormatters()
        pluginContext = null
    }
}

