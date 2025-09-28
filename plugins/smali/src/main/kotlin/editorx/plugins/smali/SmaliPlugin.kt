package editorx.plugins.smali

import editorx.plugin.Plugin
import editorx.plugin.PluginContext
import editorx.plugin.PluginInfo
import editorx.syntax.SyntaxProvider

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
        context.registerSyntaxAdapter(object : SyntaxProvider {
            override val syntaxStyleKey: String = "text/smali"
            override val fileExtensions: Set<String> = setOf("smali")
            override val isCodeFoldingEnabled: Boolean = true
            override val isBracketMatchingEnabled: Boolean = true

            override fun getTokenMakerClassName(): String {
                return SmaliTokenMaker::class.java.name
            }
        })
    }
}
