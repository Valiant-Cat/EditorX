package editorx.plugins.smali

import editorx.plugin.Plugin
import editorx.plugin.PluginContext
import editorx.plugin.PluginInfo
import editorx.syntax.SyntaxAdapter
import editorx.syntax.TokenMakerProvider
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea

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
        context.registerSyntaxAdapter(object : SyntaxAdapter {
            override val languageId: String = "text/smali"
            override val fileExtensions: Set<String> = setOf("smali")

            override fun getTokenMakerProvider(): TokenMakerProvider {
                return object : TokenMakerProvider {
                    override fun getTokenMakerClassName(): String {
                        return SmaliTokenMaker::class.java.name
                    }
                }
            }

            override fun configureTextArea(textArea: RSyntaxTextArea) {
                textArea.isCodeFoldingEnabled = true
                textArea.isBracketMatchingEnabled = true
                textArea.syntaxEditingStyle = languageId
            }
        })
    }
}
