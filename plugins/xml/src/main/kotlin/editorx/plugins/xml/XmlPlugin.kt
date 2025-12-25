package editorx.plugins.xml

import XmlFileType
import XmlLanguage
import editorx.core.plugin.Plugin
import editorx.core.plugin.PluginContext
import editorx.core.plugin.PluginInfo

class XmlPlugin : Plugin {
    override fun getInfo(): PluginInfo = PluginInfo(
        id = "xml",
        name = "XML",
        version = "0.0.1",
    )

    override fun activate(pluginContext: PluginContext) {
        pluginContext.gui()?.registerFileType(XmlFileType)
        pluginContext.gui()?.registerFormatter(XmlLanguage, XmlFormatter)
    }
}

