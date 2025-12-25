package editorx.plugins.smali

import editorx.core.plugin.Plugin
import editorx.core.plugin.PluginContext
import editorx.core.plugin.PluginInfo

class SmaliPlugin : Plugin {
    override fun getInfo(): PluginInfo = PluginInfo(
        id = "smali",
        name = "Smali",
        version = "0.0.1",
    )

    override fun activate(pluginContext: PluginContext) {
        // 注册文件类型
        pluginContext.guiClient()?.registerFileType(SmaliFileType)

        // 注册 Smali 语法高亮
        pluginContext.guiClient()?.registerSyntaxHighlighter(SmaliLanguage, SmaliHighlighter)
    }
}
