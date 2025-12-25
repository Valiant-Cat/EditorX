package editorx.plugins.androidarchive

import editorx.core.plugin.Plugin
import editorx.core.plugin.PluginContext
import editorx.core.plugin.PluginInfo

class AndroidArchivePlugin : Plugin {
    override fun getInfo(): PluginInfo = PluginInfo(
        id = "android-archive",
        name = "Android Archive",
        version = "0.0.1",
    )

    override fun activate(pluginContext: PluginContext) {
        pluginContext.guiClient()?.registerFileType(AndroidArchiveFileType)
    }
}




