package editorx.plugins.smali

import editorx.plugin.Plugin
import editorx.plugin.PluginContext
import editorx.plugin.PluginInfo
import editorx.syntax.DocumentSelector
import editorx.syntax.SyntaxHighlighter
import editorx.syntax.SyntaxHighlighterProvider

class SmaliPlugin : Plugin {
    override fun getInfo(): PluginInfo {
        return PluginInfo(
            id = "smali",
            name = "Smali",
            version = "0.0.1",
        )
    }

    override fun activate(context: PluginContext) {
        // 注册 Smali 语法高亮提供者
        context.registerSyntaxHighlighter(object : SyntaxHighlighterProvider {
            override fun getSelector(): DocumentSelector {
                return DocumentSelector.forExtensions(".smali")
            }

            override fun createHighlighter(): SyntaxHighlighter {
                return SmaliSyntaxHighlighter()
            }

        })
    }
}
