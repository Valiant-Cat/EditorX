package editorx.plugins.smali

import editorx.core.plugin.Plugin
import editorx.core.plugin.PluginContext
import editorx.core.plugin.PluginInfo
import org.fife.ui.rsyntaxtextarea.folding.FoldParserManager

class SmaliPlugin : Plugin {
    override fun getInfo() = PluginInfo(
        id = "smali",
        name = "Smali",
        version = "0.0.1",
    )

    private var pluginContext: PluginContext? = null

    override fun activate(pluginContext: PluginContext) {
        this.pluginContext = pluginContext
        // 注册文件类型
        pluginContext.gui()?.registerFileType(SmaliFileType)

        // 注册 Smali 语法高亮
        pluginContext.gui()?.registerSyntaxHighlighter(SmaliLanguage, SmaliHighlighter)

        // 注册 Smali 折叠解析器
        FoldParserManager.get().addFoldParserMapping(SmaliHighlighter.syntaxStyleKey, SmaliFoldParser())
    }

    override fun deactivate() {
        pluginContext?.gui()?.unregisterAllFileTypes()
        pluginContext?.gui()?.unregisterAllSyntaxHighlighters()
        pluginContext = null
    }
}
