package editorx.plugins.smali

import editorx.plugin.Plugin
import editorx.plugin.PluginContext
import editorx.plugin.PluginInfo

class SmaliPlugin : Plugin {
    override fun getInfo(): PluginInfo = PluginInfo(
        id = "smali",
        name = "Smali",
        version = "0.0.1",
    )

    override fun activate(context: PluginContext) {
        // 注册文件类型
        context.registerFileType(SmaliFileType)

        // 注册 Smali 语法高亮
        context.registerSyntaxHighlighter(SmaliLanguage, SmaliHighlighter)
    }
}
